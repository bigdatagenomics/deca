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
package org.bdgenomics.deca.util

/**
 * Created by mlinderman on 4/11/17.
 */
object Phred {
  def phred(prob: BigDecimal, max: Double = 99.0): Double = {
    val actual = (-10.0 * math.log10(1.0 - prob.doubleValue()))
    math.min(actual, max)
  }

  def phred(prob: Double, max: Double): Double = {
    if (prob >= 1.0) { // Occurs due to numerical issues
      max
    } else {
      val actual = (-10.0 * math.log10(1.0 - prob.doubleValue()))
      math.min(actual, max)
    }
  }

  def phred(prob: Double): Double = {
    phred(prob, 99.0)
  }
}
