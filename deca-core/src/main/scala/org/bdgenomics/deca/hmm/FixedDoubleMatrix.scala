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

/**
 * Created by mlinderman on 4/7/17.
 */
class FixedDoubleMatrix(
    var v0_0: Double, var v1_0: Double, var v2_0: Double,
    var v0_1: Double, var v1_1: Double, var v2_1: Double,
    var v0_2: Double, var v1_2: Double, var v2_2: Double) {

  def this(data: Array[Double]) = {
    this(data(0), data(1), data(2), data(3), data(4), data(5), data(6), data(7), data(8))
  }

  def rows(): Int = 3
  def cols(): Int = 3

  def apply(row: Int, col: Int) = {
    (row, col) match {
      case (0, 0) => v0_0
      case (0, 1) => v0_1
      case (0, 2) => v0_2
      case (1, 0) => v1_0
      case (1, 1) => v1_1
      case (1, 2) => v1_2
      case (2, 0) => v2_0
      case (2, 1) => v2_1
      case (2, 2) => v2_2
      case (_, _) =>
        throw new IndexOutOfBoundsException(row + " or " + col + " not in 0-2")
    }
  }

  def *(that: FixedDoubleVector): FixedDoubleVector = {
    new FixedDoubleVector(
      v0_0 * that.v0 + v0_1 * that.v1 + v0_2 * that.v2,
      v1_0 * that.v0 + v1_1 * that.v1 + v1_2 * that.v2,
      v2_0 * that.v0 + v2_1 * that.v1 + v2_2 * that.v2)
  }

  def colArgMax(): (Int, Int, Int) = {
    val col0 = {
      if (v0_0 >= v1_0) {
        if (v0_0 >= v2_0) 0 else 2
      } else {
        if (v1_0 >= v2_0) 1 else 2
      }
    }
    val col1 = {
      if (v0_1 >= v1_1) {
        if (v0_1 >= v2_1) 0 else 2
      } else {
        if (v1_1 >= v2_1) 1 else 2
      }
    }
    val col2 = {
      if (v0_2 >= v1_2) {
        if (v0_2 >= v2_2) 0 else 2
      } else {
        if (v1_2 >= v2_2) 1 else 2
      }
    }
    (col0, col1, col2)
  }

  def toArray(): Array[Double] = {
    Array(v0_0, v1_0, v2_0, v0_1, v1_1, v2_1, v0_2, v1_2, v2_2)
  }
}

