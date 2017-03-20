package org.bdgenomics.deca

import org.bdgenomics.utils.instrumentation.Metrics

/**
 * Created by mlinderman on 2/24/17.
 */
private[deca] object Timers extends Metrics {

  // org.bdgenomics.deca.Normalization
  val NormalizeReadDepths = timer("Normalize read depth matrix")
  val ReadDepthFilterI = timer("Filter read depths by target and sample prior to PCA normalization")
  val PCANormalization = timer("PCA normalization")
  val ComputeSVD = timer("Compute SVD")
  val ReadDepthFilterII = timer("Filter normalized read depths by target")
  val ComputeZScores = timer("Perform z-scaling of normalized read depths")
}