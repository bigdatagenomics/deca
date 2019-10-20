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
package org.bdgenomics.deca

import breeze.linalg.DenseMatrix
import breeze.numerics.abs
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.deca.coverage.ReadDepthMatrix
import org.bdgenomics.deca.hmm.{ FixedMatrix, TransitionProbabilities }
import org.bdgenomics.formats.avro.Feature

/**
 * Created by mlinderman on 4/5/17.
 */
class HMMSuite extends DecaFunSuite {

  /**
   * Margin to use for comparing numerical values.
   */
  val thresh = 1e-8

  def aboutEq(a: FixedMatrix, b: Array[Double]): Boolean = {
    require(a.rows * a.cols == b.length, "Matrices must be the same size.")
    a.toArray.zip(b).forall(pair => (pair._1 - pair._2).abs < thresh)

  }

  def aboutEq(a: Double, b: Double): Boolean = {
    (a - b).abs < thresh
  }

  sparkTest("Generates transition matrix for array of distances") {
    val matrix = Deca.readXHMMMatrix(resourceUrl("DATA.PCA_normalized.filtered.sample_zscores.RD.txt").toString)

    val transProb = TransitionProbabilities(matrix.targets, D = 70000, p = 1.0e-8, q = 1.0 / 6.0)

    {
      val expTrans = Array[Double](
        0.8333214286, 1.0e-8, 1.4285612245e-13, 0.1666785713, 1 - 2 * 1.0e-8, 0.1666785713, 1.4285612245e-13, 1.0e-8, 0.8333214286)
      assert(aboutEq(transProb.matrix(1), expTrans))
    }

  }

  sparkTest("Handles contig boundaries in computing transition matrix") {
    val transProb = TransitionProbabilities(Array(
      new ReferenceRegion("1", 249230845, 249231325),
      new ReferenceRegion("2", 41527, 41677)), D = 70000, p = 1.0e-8, q = 1.0 / 6.0)

    {
      val expTrans = Array[Double](
        1.0e-8, 1.0e-8, 1.0e-8, 1 - 2 * 1.0e-8, 1 - 2 * 1.0e-8, 1 - 2 * 1.0e-8, 1.0e-8, 1.0e-8, 1.0e-8)
      assert(aboutEq(transProb.matrix(1), expTrans))
    }
  }

  sparkTest("Discovers CNVs") {
    val matrix = Deca.readXHMMMatrix(resourceUrl("DATA.PCA_normalized.filtered.sample_zscores.RD.txt").toString)

    //    SAMPLE    CNV  INTERVAL               KB      CHR   MID_BP     TARGETS     NUM_TARG   Q_EXACT   Q_SOME   Q_NON_DIPLOID   Q_START   Q_STOP    MEAN_RD   MEAN_ORIG_RD
    //    HG00121   DEL  22:18898402-18913235   14.83   22    18905818   104..117    14         9         90       90              8         4         -2.51     37.99
    //    HG00113   DUP  22:17071768-17073440   1.67    22    17072604   4..11       8          25        99       99              53        25        4.00      197.73

    val cnvs = HMM.discoverCNVs(matrix, SequenceDictionary.empty)
    assert(cnvs.rdd.count === 2)

    val del = cnvs.rdd.filter(f => Option(f.getSource).exists(_.equals("HG00121"))).first()
    assert(del.getFeatureType() == "DEL")
    assert(del.getReferenceName() == "22")
    assert(del.getStart() == 18898401L)
    assert(del.getEnd() == 18913235L)
    assert(aboutEq(del.getScore(), 9.167934190998345))

    val del_attr = del.getAttributes()
    assert(del_attr.get("Q_EXACT") == "9")
    assert(del_attr.get("Q_SOME") == "90")
    assert(del_attr.get("Q_NON_DIPLOID") == "90")
    assert(del_attr.get("Q_START") == "8")
    assert(del_attr.get("Q_STOP") == "4")

    val dup = cnvs.rdd.filter(f => Option(f.getSource).exists(_.equals("HG00113"))).first()
    assert(dup.getFeatureType() == "DUP")
    assert(dup.getReferenceName() == "22")
    assert(dup.getStart() == 17071767L)
    assert(dup.getEnd() == 17073440L)
    assert(aboutEq(dup.getScore(), 25.321428730083596))

    val dup_attr = dup.getAttributes()
    assert(dup_attr.get("Q_EXACT") == "25")
    assert(dup_attr.get("Q_SOME") == "99")
    assert(dup_attr.get("Q_NON_DIPLOID") == "99")
    assert(dup_attr.get("Q_START") == "53")
    assert(dup_attr.get("Q_STOP") == "25")
  }
}
