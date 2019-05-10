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

import org.bdgenomics.utils.instrumentation.Metrics

/**
 * Created by mlinderman on 2/24/17.
 */
private[deca] object Timers extends Metrics {

  // org.bdgenomics.deca.Deca
  val ReadXHMMMatrix = timer("Read XHMM-compatible matrix")
  val WriteXHMMMatrix = timer("Write XHMM-compatible matrix")

  // org.bdgenomics.deca.Coverage
  val ComputeReadDepths = timer("Generate read depth matrix from reads")
  val PerSampleTargetCoverage = timer("Determine coverage per-sample as coordinates")
  val TargetCoverage = timer("Determine coverage for all samples as coordinates")
  val CoverageCoordinatesToMatrix = timer("Convert coverage coordinates to matrix")

  // org.bdgenomics.deca.Normalization
  val NormalizeReadDepths = timer("Normalize read depth matrix")
  val ReadDepthFilterI = timer("Filter read depths by target and sample prior to PCA normalization")
  val PCANormalization = timer("PCA normalization")
  val ComputeSVD = timer("Compute SVD")
  val ReadDepthFilterII = timer("Filter normalized read depths by target")
  val ComputeZScores = timer("Perform z-scaling of normalized read depths")

  // org.bdgenomics.deca.HMM
  val DiscoverCNVs = timer("Discover CNVs from normalized read depth")
  val DiscoverCNVsSample = timer("Discover CNVS from normalized read depth in a single sample")
  val ViterbiSample = timer("Viterbi algorithm in a single sample")
  val ForwardSample = timer("Forward algorithm in a single sample")
}
