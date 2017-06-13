package org.bdgenomics.deca.coverage

import org.apache.spark.mllib.linalg.distributed.IndexedRowMatrix
import org.bdgenomics.adam.models.ReferenceRegion

/**
 * Created by mlinderman on 4/4/17.
 */

case class ReadDepthMatrix(depth: IndexedRowMatrix, samples: Array[String], targets: Array[ReferenceRegion]) {
  assert(
    depth.numRows() == samples.length && depth.numCols() == targets.length,
    "Size of depth matrix not consistent with samples and targets")

  def cache() = depth.rows.cache()

  def unpersist() = depth.rows.unpersist()

  // We can't use the IndexedRowMatrix numRows() method as it returns the maximum index
  def numSamples() = depth.rows.count()

  def numTargets() = targets.length
}
