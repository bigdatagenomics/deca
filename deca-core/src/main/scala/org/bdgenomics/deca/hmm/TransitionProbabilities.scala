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

import com.esotericsoftware.kryo.io.{ Input, Output }
import com.esotericsoftware.kryo.{ Kryo, Serializer }
import org.bdgenomics.adam.models.ReferenceRegion

/**
 * Created by mlinderman on 4/4/17.
 */
class TransitionProbabilities(val f: Array[Double], val p: Double, val q: Double) {
  // Methods for row and columns and individual entry
  def matrix(t: Int): FixedMatrix = {
    if (t == 0) {
      new FixedMatrix(0, p, 0, 0, 1 - 2 * p, 0, 0, p, 0)
    } else {
      val ft = f(t)
      // Matrices are initialized in column order
      new FixedMatrix(
        ft * (1 - q) + (1 - ft) * p, p, (1 - ft) * p,
        ft * q + (1 - ft) * (1 - 2 * p), 1 - 2 * p, ft * q + (1 - ft) * (1 - 2 * p),
        (1 - ft) * p, p, ft * (1 - q) + (1 - ft) * p)
    }
  }

  def doubleMatrix(t: Int): FixedDoubleMatrix = {
    if (t == 0) {
      new FixedDoubleMatrix(0, p, 0, 0, 1 - 2 * p, 0, 0, p, 0)
    } else {
      val ft = f(t)
      // Matrices are initialized in column order
      new FixedDoubleMatrix(
        v0_0 = ft * (1 - q) + (1 - ft) * p, v1_0 = p, v2_0 = (1 - ft) * p,
        v0_1 = ft * q + (1 - ft) * (1 - 2 * p), v1_1 = 1 - 2 * p, v2_1 = ft * q + (1 - ft) * (1 - 2 * p),
        v0_2 = (1 - ft) * p, v1_2 = p, v2_2 = ft * (1 - q) + (1 - ft) * p)
    }
  }

  def edge(t: Int, xt_1: Int, xt: Int): Double = {
    val ft = f(t)
    (xt_1, xt) match {
      case (0, 0) => ft * (1 - q) + (1 - ft) * p
      case (0, 1) => ft * q + (1 - ft) * (1 - 2 * p)
      case (0, 2) => (1 - ft) * p
      case (1, 0) => p
      case (1, 1) => 1 - 2 * p
      case (1, 2) => p
      case (2, 0) => (1 - ft) * p
      case (2, 1) => ft * q + (1 - ft) * (1 - 2 * p)
      case (2, 2) => ft * (1 - q) + (1 - ft) * p
      case (_, _) =>
        throw new IndexOutOfBoundsException(xt_1 + " or " + xt + " not in 0-2")
    }
  }

  def logEdge(t: Int, xt_1: Int, xt: Int): Double = {
    math.log(edge(t, xt_1, xt))
  }

  def to(t: Int, xt: Int): FixedVector = {
    val ft = f(t)
    if (xt == 0) return new FixedVector(ft * (1 - q) + (1 - ft) * p, p, (1 - ft) * p)
    else if (xt == 1) return new FixedVector(ft * q + (1 - ft) * (1 - 2 * p), 1 - 2 * p, ft * q + (1 - ft) * (1 - 2 * p))
    else if (xt == 2) return new FixedVector((1 - ft) * p, p, ft * (1 - q) + (1 - ft) * p)
    else
      throw new IndexOutOfBoundsException(xt + " not in 0-2")
  }

  def logTo(t: Int, xt: Int): FixedDoubleVector = {
    val ft = f(t)
    xt match {
      case 0 => new FixedDoubleVector(math.log(ft * (1 - q) + (1 - ft) * p), math.log(p), math.log((1 - ft) * p))
      case 1 => new FixedDoubleVector(math.log(ft * q + (1 - ft) * (1 - 2 * p)), math.log(1 - 2 * p), math.log(ft * q + (1 - ft) * (1 - 2 * p)))
      case 2 => new FixedDoubleVector(math.log((1 - ft) * p), math.log(p), math.log(ft * (1 - q) + (1 - ft) * p))
      case _ => throw new IndexOutOfBoundsException(xt + " not in 0-2")
    }
  }
}

object TransitionProbabilities {
  def apply(targets: Array[ReferenceRegion], D: Double, p: Double, q: Double): TransitionProbabilities = {
    val f = new Array[Double](targets.length)
    (1 until f.length).foreach(i => {
      f(i) = targets(i - 1).distance(targets(i)) match {
        case Some(dist) => math.exp(-(dist - 1).toDouble / D) // XHMM is inc. start - inc. end - 1
        case None       => 0.0 // "Infinite" distance
      }
    })
    new TransitionProbabilities(f, p, q)
  }
}

private[deca] class TransitionProbabilitiesSerializer extends Serializer[TransitionProbabilities] {
  def write(kryo: Kryo, output: Output, obj: TransitionProbabilities) {
    output.writeInt(obj.f.length)
    obj.f.foreach(output.writeDouble(_))
    output.writeDouble(obj.p)
    output.writeDouble(obj.q)
  }

  def read(kryo: Kryo, input: Input, klazz: Class[TransitionProbabilities]): TransitionProbabilities = {
    val fSize = input.readInt()
    val f = new Array[Double](fSize)
    (0 until fSize).foreach(i => {
      f(i) = input.readDouble()
    })
    val p = input.readDouble()
    val q = input.readDouble()
    new TransitionProbabilities(f, p, q)
  }
}