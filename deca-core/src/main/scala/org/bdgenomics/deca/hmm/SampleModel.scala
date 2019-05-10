/**
 * Licensed to Big Data Genomics (BDG) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The BDG licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bdgenomics.deca.hmm
import java.util

import breeze.linalg.{ argmax, sum, DenseMatrix => BDM, DenseVector => BDV }
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.spark.mllib.linalg.{ DenseVector => SDV, Vector => SV }
import org.bdgenomics.deca.util.{ MLibUtils, Phred }
import org.bdgenomics.formats.avro.{ Feature, OntologyTerm }
import org.bdgenomics.deca.Timers._

/**
 * Created by mlinderman on 4/5/17.
 */

class SampleModel(obs: BDV[Double], transProb: TransitionProbabilities, M: Double, p: Double) {

  private lazy val delDist = new NormalDistribution(-M, 1)
  private lazy val dipDist = new NormalDistribution(0, 1)
  private lazy val dupDist = new NormalDistribution(M, 1)

  private def emitDist(t: Int): FixedVector = {
    val my_obs = obs(t)
    FixedVector(delDist.density(my_obs), dipDist.density(my_obs), dupDist.density(my_obs))
  }

  private def doubleEmitDist(t: Int): FixedDoubleVector = {
    val my_obs = obs(t)
    FixedDoubleVector(delDist.density(my_obs), dipDist.density(my_obs), dupDist.density(my_obs))
  }

  private def logEmitDist(t: Int): FixedDoubleVector = {
    val my_obs = obs(t)
    FixedDoubleVector(delDist.logDensity(my_obs), dipDist.logDensity(my_obs), dupDist.logDensity(my_obs))
  }

  private def emitDist(t: Int, kind: Int): Double = {
    val my_obs = obs(t)
    kind match {
      case 0 => delDist.density(my_obs)
      case 1 => dipDist.density(my_obs)
      case 2 => dupDist.density(my_obs)
      case _ => throw new IndexOutOfBoundsException(kind + " not in 0-2")
    }
  }

  private def logEmitDist(t: Int, kind: Int): Double = {
    val my_obs = obs(t)
    kind match {
      case 0 => delDist.logDensity(my_obs)
      case 1 => dipDist.logDensity(my_obs)
      case 2 => dupDist.logDensity(my_obs)
      case _ => throw new IndexOutOfBoundsException(kind + " not in 0-2")
    }
  }

  private def emitDistExclude(t: Int, kind: Int): FixedVector = {
    val my_obs = obs(t)
    kind match {
      case 0 => FixedVector(0.0, dipDist.density(my_obs), dupDist.density(my_obs))
      case 1 => FixedVector(delDist.density(my_obs), 0.0, dupDist.density(my_obs))
      case 2 => FixedVector(delDist.density(my_obs), dipDist.density(my_obs), 0.0)
      case _ => throw new IndexOutOfBoundsException(kind + " not in 0-2")
    }
  }

  private def doubleEmitDistExclude(t: Int, kind: Int): FixedDoubleVector = {
    val my_obs = obs(t)
    kind match {
      case 0 => FixedDoubleVector(0.0, dipDist.density(my_obs), dupDist.density(my_obs))
      case 1 => FixedDoubleVector(delDist.density(my_obs), 0.0, dupDist.density(my_obs))
      case 2 => FixedDoubleVector(delDist.density(my_obs), dipDist.density(my_obs), 0.0)
      case _ => throw new IndexOutOfBoundsException(kind + " not in 0-2")
    }
  }

  private def kind2Type(kind: Int) = {
    kind match {
      case 0 => "DEL"
      case 1 => "DIP"
      case 2 => "DUP"
      case _ => throw new IndexOutOfBoundsException(kind + " not in 0-2")
    }
  }

  private def excludeType(kind: Int) = {
    kind match {
      case 0 => 2
      case 2 => 0
      case _ => throw new IndexOutOfBoundsException(kind + " not 0 or 2")
    }
  }

  lazy val fwdCache = {
    val fwd = Array.ofDim[FixedVector](obs.length)

    fwd(0) = FixedVector(p, 1 - 2 * p, p) :* emitDist(0)
    for (t <- 1 until obs.length) {
      // Compute forward algorithm
      fwd(t) = (fwd(t - 1) * transProb.matrix(t)) :* emitDist(t)
    }

    fwd
  }

  lazy val (scaledFwdCache, fwdScalingFactorCache) = {
    val alpha_prime = Array.ofDim[FixedDoubleVector](obs.length)
    val c = Array.ofDim[Double](obs.length)

    { // t=0
      val alpha_t = FixedDoubleVector(p, 1 - 2 * p, p) :* doubleEmitDist(0)
      c(0) = alpha_t.sum()
      alpha_prime(0) = alpha_t / c(0)
    }

    for (t <- 1 until obs.length) {
      val alpha_t = (alpha_prime(t - 1) * transProb.doubleMatrix(t)) :* doubleEmitDist(t)
      c(t) = alpha_t.sum()
      alpha_prime(t) = alpha_t / c(t)
    }

    (alpha_prime, c)
  }

  lazy val hiddenCache = {
    val backPointers = Array.ofDim[(Int, Int, Int)](obs.length)
    var prevVit = FixedDoubleVector(math.log(p), math.log(1 - 2 * p), math.log(p)) :+ logEmitDist(0)
    for (t <- 1 until obs.length) {
      val emit = logEmitDist(t)
      val (vt_0, incoming_0) = FixedDoubleVector.argmaxSum(prevVit, transProb.logTo(t, 0), emit.v0)
      val (vt_1, incoming_1) = FixedDoubleVector.argmaxSum(prevVit, transProb.logTo(t, 1), emit.v1)
      val (vt_2, incoming_2) = FixedDoubleVector.argmaxSum(prevVit, transProb.logTo(t, 2), emit.v2)

      backPointers(t) = (incoming_0, incoming_1, incoming_2)
      prevVit = FixedDoubleVector(vt_0, vt_1, vt_2)
    }

    val hidden = Array.ofDim[Int](obs.length)
    hidden(obs.length - 1) = prevVit.argmax()
    for (t <- (0 until obs.length - 1).reverse) {
      hidden(t) = backPointers(t + 1).productElement(hidden(t + 1)).asInstanceOf[Int]
    }
    hidden
  }

  lazy val totalLikelihood = fwdCache(obs.length - 1).sum()

  def exact_probability(t1: Int, t2: Int, kind: Int, bwd: FixedVector): BigDecimal = {
    var prob = fwdCache(t1)(kind) * bwd(kind)
    for (t <- t1 + 1 to t2) {
      prob *= transProb.edge(t, kind, kind) * emitDist(t, kind)
    }
    prob / totalLikelihood
  }

  def exact_probability(t1: Int, t2: Int, kind: Int, scaledBwd: FixedDoubleVector): Double = {
    var prob = math.log(scaledFwdCache(t1)(kind)) + math.log(scaledBwd(kind))
    for (t <- t1 + 1 to t2) {
      prob += transProb.logEdge(t, kind, kind) + logEmitDist(t, kind)
    }
    // When t1 == t2, the scaling factor will get double counted
    var missing_c = if (t1 == t2) -math.log(fwdScalingFactorCache(t1)) else 0
    for (t <- t1 + 1 until t2) {
      missing_c += math.log(fwdScalingFactorCache(t))
    }
    math.exp(prob - missing_c)
  }

  def exclude_probability(t1: Int, t2: Int, exclude: Int, bwd: FixedVector): BigDecimal = {
    var fwd: FixedVector = if (t1 > 0) fwdCache(t1 - 1) else FixedVector.ZEROS
    for (t <- t1 to t2) {
      val trans = transProb.matrix(t)
      fwd = (fwd * trans) :* emitDistExclude(t, exclude)
    }
    (fwd * bwd) / totalLikelihood
  }

  def exclude_probability(t1: Int, t2: Int, exclude: Int, scaledBwd: FixedDoubleVector): Double = {
    var fwd: FixedDoubleVector = if (t1 > 0) scaledFwdCache(t1 - 1) else FixedDoubleVector.ZEROS
    var c_T: Double = 0.0
    for (t <- t1 to t2) {
      val trans = transProb.doubleMatrix(t)
      fwd = (fwd * trans) :* doubleEmitDistExclude(t, exclude)
      val c = fwd.sum()
      fwd = fwd / c
      c_T += math.log(c)
    }
    var missing_c: Double = 0.0
    for (t <- t1 until t2) {
      missing_c += math.log(fwdScalingFactorCache(t))
    }
    math.exp(math.log(fwd * scaledBwd) + c_T - missing_c)
  }

  def stop_probability(t2: Int, kind: Int, bwd: FixedVector): BigDecimal = {
    try {
      fwdCache(t2)(kind) * transProb.edge(t2 + 1, kind, 1) * emitDist(t2 + 1, 1) * bwd(1) / totalLikelihood
    } catch {
      case div0: ArithmeticException                     => 0.0
      case end: java.lang.ArrayIndexOutOfBoundsException => 0.0
    }
  }

  def stop_probability(t2: Int, kind: Int, scaledBwd: FixedDoubleVector): Double = {
    try {
      scaledFwdCache(t2)(kind) * transProb.edge(t2 + 1, kind, 1) * emitDist(t2 + 1, 1) * scaledBwd(1)
    } catch {
      case div0: ArithmeticException                     => 0.0
      case end: java.lang.ArrayIndexOutOfBoundsException => 0.0
    }
  }

  def start_probability(t1: Int, kind: Int, bwd: FixedVector): BigDecimal = {
    if (t1 > 0)
      fwdCache(t1 - 1)(1) * transProb.edge(t1, 1, kind) * emitDist(t1, kind) * bwd(kind) / totalLikelihood
    else
      0.0
  }

  def start_probability(t1: Int, kind: Int, scaledBwd: FixedDoubleVector): Double = {
    if (t1 > 0)
      scaledFwdCache(t1 - 1)(1) * transProb.edge(t1, 1, kind) * emitDist(t1, kind) * scaledBwd(kind)
    else
      0.0
  }

  def discoverCNVs(minSomeQuality: Double, qualFormat: String = "%.0f"): Seq[Feature] = {
    val features = scala.collection.mutable.ArrayBuffer.empty[Feature]

    // Compute backward probabilities without backward caching while looking for CNVs
    var scaledPrevBwd = FixedDoubleVector(1 / fwdScalingFactorCache(obs.length - 1))
    var scaledCurrentCNV: (Int, Int, FixedDoubleVector, Double) = null

    for (t <- (0 until obs.length).reverse) {
      //val bwd = if (t == obs.length - 1) FixedVector.ONES else transProb.matrix(t + 1) * (emitDist(t + 1) :* prevBwd)
      val scaledBwd = if (t == obs.length - 1) {
        scaledPrevBwd
      } else {
        (transProb.doubleMatrix(t + 1) * (doubleEmitDist(t + 1) :* scaledPrevBwd)) / fwdScalingFactorCache(t)
      }
      val kind = hiddenCache(t)

      if (kind != 1 && scaledCurrentCNV == null) {
        // Start of a new CNV
        scaledCurrentCNV = (t, kind, scaledBwd, Phred.phred(stop_probability(t, kind, scaledPrevBwd)))
      } else if (scaledCurrentCNV != null && (kind != scaledCurrentCNV._2 || t == 0)) {
        // End of a CNV and possibly the start of another
        val cnvStart = if (t > 0 || kind == 1) t + 1 else 0 // If we reach target 0 in a CNV
        val (cnvEnd, cnvKind, scaledCNVBwd, stopPhredPr) = scaledCurrentCNV

        // Compute the CNV quality scores
        val dipPr = exact_probability(cnvStart, cnvEnd, 1, scaledCNVBwd) // 1 => DIP
        val somePhredPr = Phred.phred(exclude_probability(cnvStart, cnvEnd, excludeType(cnvKind), scaledCNVBwd) - dipPr)

        if (somePhredPr >= minSomeQuality) {
          val exactPhredPr = Phred.phred(exact_probability(cnvStart, cnvEnd, cnvKind, scaledCNVBwd))
          val startPhredPr = Phred.phred(start_probability(cnvStart, cnvKind, scaledPrevBwd))

          val cnvAttr = new util.HashMap[String, String]()
          cnvAttr.put("START_TARGET", cnvStart.toString)
          cnvAttr.put("END_TARGET", cnvEnd.toString)
          cnvAttr.put("Q_EXACT", qualFormat.format(exactPhredPr))
          cnvAttr.put("Q_SOME", qualFormat.format(somePhredPr))
          cnvAttr.put("Q_NON_DIPLOID", qualFormat.format(Phred.phred(1.0 - dipPr)))
          cnvAttr.put("Q_START", qualFormat.format(startPhredPr))
          cnvAttr.put("Q_STOP", qualFormat.format(stopPhredPr))

          val builder = Feature.newBuilder()
          builder.setFeatureType(kind2Type(cnvKind))
          builder.setScore(exactPhredPr)
          builder.setAttributes(cnvAttr)

          features += builder.build()
        }

        // Are we back to diploid, or did we start another different CNV
        scaledCurrentCNV = if (kind != 1) (t, kind, scaledBwd, Phred.phred(stop_probability(t, kind, scaledPrevBwd))) else null
      }

      scaledPrevBwd = scaledBwd
    }

    features
  }
}

object SampleModel {
  def apply(obs: SV, transProb: TransitionProbabilities, M: Double, p: Double, maxObs: Double = 10.0): SampleModel = {
    // "Clamp" observations
    new SampleModel(MLibUtils.mllibVectorToDenseBreeze(obs).map(v => {
      if (math.abs(v) > maxObs) math.signum(v) * maxObs else v
    }), transProb, M, p)
  }
}
