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
class FixedVector(val v0: BigDecimal, val v1: BigDecimal, val v2: BigDecimal) {
  def this(data: Array[BigDecimal]) = this(data(0), data(1), data(2))
  def this(data: Array[Double]) = this(data(0), data(1), data(2))

  def apply(i: Int): BigDecimal = {
    if (i == 0) return v0
    else if (i == 1) return v1
    else if (i == 2) return v2
    else
      throw new IndexOutOfBoundsException(i + " not in 0-2")
  }

  def :*(that: FixedVector): FixedVector = {
    new FixedVector(v0 * that.v0, v1 * that.v1, v2 * that.v2)
  }

  def :*(that: FixedMatrix): FixedMatrix = {
    new FixedMatrix(
      v0 * that.v0_0, v1 * that.v1_0, v2 * that.v2_0,
      v0 * that.v0_1, v1 * that.v1_1, v2 * that.v2_1,
      v0 * that.v0_2, v1 * that.v1_2, v2 * that.v2_2)
  }

  def *(that: FixedVector): BigDecimal = {
    v0 * that.v0 + v1 * that.v1 + v2 * that.v2
  }

  def *(that: FixedMatrix): FixedVector = {
    new FixedVector(
      v0 * that.v0_0 + v1 * that.v1_0 + v2 * that.v2_0,
      v0 * that.v0_1 + v1 * that.v1_1 + v2 * that.v2_1,
      v0 * that.v0_2 + v1 * that.v1_2 + v2 * that.v2_2)
  }

  def sum(): BigDecimal = v0 + v1 + v2

  def argmax(): Int = {
    if (v0 >= v1) {
      if (v0 >= v2) 0 else 2
    } else {
      if (v1 >= v2) 1 else 2
    }
  }

  override def toString() = {
    s"FixedVector(${v0},${v1},${v2})"
  }
}

object FixedVector {
  def apply(v0: BigDecimal, v1: BigDecimal, v2: BigDecimal): FixedVector = {
    new FixedVector(v0, v1, v2)
  }
  def apply(v0: Double, v1: Double, v2: Double): FixedVector = {
    new FixedVector(v0, v1, v2)
  }

  val ZEROS = FixedVector(0.0, 0.0, 0.0)
  val ONES = FixedVector(1.0, 1.0, 1.0)

}
