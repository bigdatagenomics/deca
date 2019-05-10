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
class FixedDoubleVector(val v0: Double, val v1: Double, val v2: Double) {
  def this(data: Array[Double]) = this(data(0), data(1), data(2))

  def apply(i: Int): Double = {
    i match {
      case 0 => v0
      case 1 => v1
      case 2 => v2
      case _ => throw new IndexOutOfBoundsException(i + " not in 0-2")
    }
  }

  def :+(that: FixedDoubleVector): FixedDoubleVector = {
    new FixedDoubleVector(v0 + that.v0, v1 + that.v1, v2 + that.v2)
  }

  def :*(that: FixedDoubleVector): FixedDoubleVector = {
    new FixedDoubleVector(v0 * that.v0, v1 * that.v1, v2 * that.v2)
  }

  def *(that: FixedDoubleVector): Double = {
    v0 * that.v0 + v1 * that.v1 + v2 * that.v2
  }

  def *(that: FixedDoubleMatrix): FixedDoubleVector = {
    new FixedDoubleVector(
      v0 * that.v0_0 + v1 * that.v1_0 + v2 * that.v2_0,
      v0 * that.v0_1 + v1 * that.v1_1 + v2 * that.v2_1,
      v0 * that.v0_2 + v1 * that.v1_2 + v2 * that.v2_2)
  }

  def /(that: Double): FixedDoubleVector = {
    new FixedDoubleVector(v0 / that, v1 / that, v2 / that)
  }

  def sum(): Double = v0 + v1 + v2

  def argmax(): Int = {
    if (v0 >= v1) {
      if (v0 >= v2) 0 else 2
    } else {
      if (v1 >= v2) 1 else 2
    }
  }

  override def toString = {
    s"FixedDoubleVector(${v0},${v1},${v2})"
  }
}

object FixedDoubleVector {
  def apply(v: Double): FixedDoubleVector = {
    new FixedDoubleVector(v, v, v)
  }

  def apply(v0: Double, v1: Double, v2: Double): FixedDoubleVector = {
    new FixedDoubleVector(v0, v1, v2)
  }

  def argmaxSum(vector0: FixedDoubleVector, vector1: FixedDoubleVector, scalar2: Double): (Double, Int) = {
    val v0 = vector0.v0 + vector1.v0
    val v1 = vector0.v1 + vector1.v1
    val v2 = vector0.v2 + vector1.v2
    if (v0 >= v1) {
      if (v0 >= v2) (v0 + scalar2, 0) else (v2 + scalar2, 2)
    } else {
      if (v1 >= v2) (v1 + scalar2, 1) else (v2 + scalar2, 2)
    }
  }

  val ZEROS = FixedDoubleVector(0.0, 0.0, 0.0)
  val ONES = FixedDoubleVector(1.0, 1.0, 1.0)
}
