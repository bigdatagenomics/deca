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

import breeze.linalg.{ DenseVector, sum }
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.linalg.distributed.{ IndexedRow, IndexedRowMatrix }
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.feature.FeatureRDD
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.adam.rich.RichAlignmentRecord
import org.bdgenomics.deca.coverage.ReadDepthMatrix
import org.bdgenomics.deca.util.MLibUtils
import org.bdgenomics.deca.Timers._
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.utils.misc.Logging

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.{ HashMap, MultiMap, Set }
import scala.math.{ max, min }
import scala.util.control.Breaks._

/**
 * Created by mlinderman on 3/8/17.
 */

private[deca] class PotentialMateOverlap(names: HashMap[String, AlignmentRecord], targets: MultiMap[String, Int]) {
  def this() = this(new mutable.HashMap[String, AlignmentRecord], new mutable.HashMap[String, Set[Int]] with MultiMap[String, Int])

  def addBinding(read: AlignmentRecord, targetIndex: Int) = {
    names.putIfAbsent(read.getReadName, read)
    targets.addBinding(read.getReadName, targetIndex)
  }

  def remove(read: AlignmentRecord): Option[(AlignmentRecord, mutable.Set[Int])] = {
    val myName = read.getReadName
    targets.remove(myName).map(targetSet => {
      val leftRead = names.remove(myName).get // If targets is defined, so should names
      (leftRead, targetSet)
    })
  }

  def clear() = {
    names.clear()
    targets.clear()
  }
}

object Coverage extends Serializable with Logging {

  def totalCoverageOfRegion(region: ReferenceRegion, read: RichAlignmentRecord): Long = {
    // "Clip" bases when the insert size is shorter than the read length
    val insert = read.getInferredInsertSize
    val regionStart = if (insert != null && insert < 0) max(region.start, read.getEnd + insert - 1) else region.start
    val regionEnd = if (insert != null && insert > 0) min(region.end, read.getStart + insert + 1) else region.end

    var pileup: Long = 0
    var cigarSegmentStart = read.getStart
    read.samtoolsCigar.foreach(cigar => {
      val op = cigar.getOperator
      if (op.consumesReadBases && op.consumesReferenceBases) {
        val overlap = min(regionEnd, cigarSegmentStart + cigar.getLength) - max(regionStart, cigarSegmentStart)
        if (overlap > 0) {
          pileup += overlap
        }
        cigarSegmentStart += cigar.getLength
      } else if (op.consumesReferenceBases) {
        cigarSegmentStart += cigar.getLength;
      }
    })

    pileup
  }

  private def perBaseCoverageOfRegion(region: ReferenceRegion, pileup: DenseVector[Int], read: RichAlignmentRecord): DenseVector[Int] = {
    // "Clip" bases when the insert size is shorter than the read length
    val insert = read.getInferredInsertSize
    val regionStart = if (insert != null && insert < 0) max(region.start, read.getEnd + insert - 1) else region.start
    val regionEnd = if (insert != null && insert > 0) min(region.end, read.getStart + insert + 1) else region.end

    var cigarSegmentStart = read.getStart
    read.samtoolsCigar.foreach(cigar => {
      val op = cigar.getOperator
      if (op.consumesReadBases && op.consumesReferenceBases) {
        val idxStart: Int = (max(regionStart, cigarSegmentStart) - region.start).toInt
        val idxEnd: Int = (min(regionEnd, cigarSegmentStart + cigar.getLength) - region.start).toInt
        for (pos <- idxStart until idxEnd) {
          pileup(pos) = pileup(pos) + 1
        }
        cigarSegmentStart += cigar.getLength
      } else if (op.consumesReferenceBases) {
        cigarSegmentStart += cigar.getLength;
      }
    })

    pileup
  }

  def fragmentOverlap(region: ReferenceRegion, read1: RichAlignmentRecord, read2: RichAlignmentRecord): Long = {
    var pileup = DenseVector.zeros[Int](region.length.toInt)

    pileup = perBaseCoverageOfRegion(region, pileup, read1)
    pileup = perBaseCoverageOfRegion(region, pileup, read2)

    var trueOverlap: Long = 0
    for (i <- 0 until pileup.length) {
      if (pileup(i) == 2)
        trueOverlap += 1
    }

    trueOverlap
  }

  private def queryGreaterThanOther(query: ReferenceRegion, other: ReferenceRegion): Boolean = {
    if (query.referenceName != other.referenceName)
      query.referenceName > other.referenceName
    else
      query.start >= other.end
  }

  private def readOverlapsTarget(read: AlignmentRecord, target: ReferenceRegion): Boolean = {
    read.getContigName == target.referenceName && read.getEnd() > target.start && read.getStart < target.end
  }

  private def couldOverlapMate(read: AlignmentRecord): Boolean = {
    read.getMateMapped &&
      read.getMateContigName == read.getContigName &&
      read.getMateAlignmentStart >= read.getStart && read.getMateAlignmentStart < read.getEnd
  }

  /**
   * Find smallest target index for which all targets overlap or greater than the query
   *
   * @param targets Array of target regions and associated target indices into read matrix
   * @param query Query region
   * @return Optional target index
   */
  def findFirstTarget(targets: Array[(ReferenceRegion, Int)], query: ReferenceRegion): Option[Int] = {
    var first: Int = 0
    var last: Int = targets.length
    while (first < last) {
      val mid: Int = (first + last) / 2
      if (queryGreaterThanOther(query, targets(mid)._1))
        first = mid + 1
      else
        last = mid
    }

    if (first < targets.length) Some(first) else None
  }

  def sortedCoverageCalculation(targets: Broadcast[Array[(ReferenceRegion, Int)]], reads: AlignmentRecordRDD): RDD[(Int, Double)] = {
    val targetsArray = targets.value
    reads.rdd.mapPartitions(readIter => {
      if (!readIter.hasNext) {
        Iterator[(Int, Double)]()
      } else {
        val coverageByTarget = new mutable.HashMap[Int, Long].withDefaultValue(0)
        val potentialMateOverlap = new PotentialMateOverlap()

        // Scan forward to identify any overlapping targets to compute depth. Return new first target for future searches
        // as we advance through the reads
        @tailrec def scanForward(read: RichAlignmentRecord, firstIndex: Int, targetIndex: Int, mateOverlap: Boolean): Int = {
          if (targetIndex >= targetsArray.length)
            return firstIndex
          else {
            val targetRegion = targetsArray(targetIndex)._1
            if (readOverlapsTarget(read, targetRegion)) {

              coverageByTarget(targetIndex) += totalCoverageOfRegion(targetRegion, read)
              if (mateOverlap) {
                // Slow path: Track potentially overlapping paired reads to more precisely calculate fragment coverage
                potentialMateOverlap.addBinding(read, targetIndex)
              }

              return scanForward(read, firstIndex, targetIndex + 1, mateOverlap)
            } else if (read.getContigName == targetRegion.referenceName && read.getStart >= targetRegion.end)
              return scanForward(read, targetIndex + 1, targetIndex + 1, mateOverlap)
            else
              return firstIndex
          }
        }

        // Find starting target of sorted reads once per partition
        var firstRead = readIter.next()
        var firstTarget = findFirstTarget(targetsArray, ReferenceRegion.unstranded(firstRead))
        while (firstTarget.isDefined) {
          var movingFirstTargetIndex = scanForward(
            new RichAlignmentRecord(firstRead), firstTarget.get, firstTarget.get, couldOverlapMate(firstRead))

          breakable {
            readIter.foreach(read => {
              // Crossed a contig boundary? Reset firstRead and firstTarget for coverage analysis of next contig
              if (read.getContigName != firstRead.getContigName) {
                firstRead = read
                firstTarget = findFirstTarget(targetsArray, ReferenceRegion.unstranded(read))
                break // Should happen infrequently
              }

              potentialMateOverlap.remove(read).foreach {
                case (mate, targets) => targets.foreach(targetIndex => {
                  coverageByTarget(targetIndex) -= fragmentOverlap(targetsArray(targetIndex)._1, mate, read)
                })
              }

              movingFirstTargetIndex = scanForward(
                new RichAlignmentRecord(read), movingFirstTargetIndex, movingFirstTargetIndex, couldOverlapMate(read))
            })

            firstTarget = None // Terminate while loop since we have exhausted all reads
          }

          potentialMateOverlap.clear()
        }

        coverageByTarget.map {
          case (targetIndex, totalCoverage) => {
            val target = targetsArray(targetIndex)
            (target._2, totalCoverage.toDouble / target._1.length)
          }
        }.toIterator
      }
    })
  }

  def coverageMatrixFromCoordinates(coverageCoordinates: RDD[(Int, (Int, Double))], numSamples: Long, numTargets: Long, numPartitions: Option[Int] = None): IndexedRowMatrix = CoverageCoordinatesToMatrix.time {
    val indexedRows = coverageCoordinates.groupByKey(numPartitions.getOrElse(numSamples.toInt)).map {
      case (sampleIdx, targetCovg) =>
        var perTargetCoverage = DenseVector.zeros[Double](numTargets.toInt)
        targetCovg.foreach { case (targetIdx, covg) => perTargetCoverage(targetIdx) = covg; }
        IndexedRow(sampleIdx.toLong, MLibUtils.breezeVectorToMLlib(perTargetCoverage))
    }
    new IndexedRowMatrix(indexedRows, numSamples, numTargets.toInt)
  }

  def coverageMatrix(readRdds: Seq[AlignmentRecordRDD], targets: FeatureRDD, minMapQ: Int = 0, numPartitions: Option[Int] = None): ReadDepthMatrix = ComputeReadDepths.time {
    // Sequence dictionary parsing is broken in current ADAM release:
    //    https://github.com/bigdatagenomics/adam/issues/1409
    // which breaks the desired sorting of the targets. Upgrading to a newer version of ADAM did not fix the issues as
    // the necessary indices are not being set when the header of the interval_list is being parsed. Instead we assume
    // the intervals are supplied in the desired order.

    val targetsDriver = targets.rdd
      .map(ReferenceRegion.unstranded(_))
      .collect()

    val broadcastTargetArray = {
      // After assigning target indices we sort with natural ordering of the ReferenceRegions (lexicographic contigs)
      // to eliminate need for sequence dictionary in subsequent analyses
      val sc = SparkContext.getOrCreate()
      sc.broadcast(targetsDriver.zipWithIndex.sortBy(_._1))
    }

    // TODO: Verify inputs, e.g. BAM files, are in sorted order

    val samplesDriver = readRdds.map(readsRdd => {
      val samples = readsRdd.recordGroups.toSamples
      if (samples.length > 1)
        throw new IllegalArgumentException("reads RDD must be a single sample")
      samples.head.getSampleId
    }).toArray

    val numSamples = readRdds.length
    val numTargets = targetsDriver.length

    val coverageCoordinates = TargetCoverage.time {
      val coverageCoordinatesPerSample = readRdds.zipWithIndex.map {
        case (readsRdd, sampleIdx) => PerSampleTargetCoverage.time {
          // Read filtering
          val filteredReadsRdd = readsRdd.transform(rdd => rdd.filter(read => {
            !read.getDuplicateRead &&
              !read.getFailedVendorQualityChecks &&
              read.getPrimaryAlignment &&
              read.getReadMapped &&
              (minMapQ == 0 || read.getMapq >= minMapQ)
          }))

          sortedCoverageCalculation(broadcastTargetArray, filteredReadsRdd)
            .reduceByKey(_ + _)
            .map((sampleIdx, _))
        }
      }

      val sc = SparkContext.getOrCreate()
      sc.union(coverageCoordinatesPerSample)
    }

    val rdMatrix = coverageMatrixFromCoordinates(coverageCoordinates, numSamples, numTargets, numPartitions = numPartitions)
    ReadDepthMatrix(rdMatrix, samplesDriver, targetsDriver)
  }

}
