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
package org.bdgenomics.deca.cli

import htsjdk.samtools.ValidationStringency
import org.apache.spark.SparkContext

import org.bdgenomics.adam.projections.{ Projection, AlignmentRecordField => ARF, FeatureField => FF }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.deca.coverage.ReadDepthMatrix
import org.bdgenomics.deca.cli.util.{ IntOptionHandler => IntOptionArg, StringOptionHandler => StringOptionArg }
import org.bdgenomics.deca.{ Coverage, Deca, HMM, Normalization }
import org.bdgenomics.utils.cli._
import org.kohsuke.args4j.spi.StringArrayOptionHandler
import org.kohsuke.args4j.{ Option => Args4jOption }

/**
 * Created by mlinderman on 4/12/17.
 */

object CNVer extends BDGCommandCompanion {
  val commandName = "cnv"
  val commandDescription = "Discover CNVs from raw read data"

  def apply(cmdLine: Array[String]) = {
    new CNVer(Args4j[CNVerArgs](cmdLine))
  }
}

class CNVerArgs extends Args4jBase with CoverageArgs with NormalizeArgs with DiscoveryArgs {
  @Args4jOption(required = true,
    name = "-I",
    usage = "One or more BAM, Parquet or other alignment files",
    handler = classOf[StringArrayOptionHandler])
  var readsPaths: Array[String] = null

  @Args4jOption(required = true,
    name = "-L",
    usage = "Targets for XHMM analysis as interval_list, BED or other feature file")
  var targetsPath: String = null

  @Args4jOption(required = true,
    name = "-o",
    usage = "Path to write discovered CNVs as GFF3 file")
  var outputPath: String = null

  @Args4jOption(required = false,
    name = "-save_rd",
    usage = "Path to write XHMM read depth matrix",
    handler = classOf[StringOptionArg])
  var rdPath: Option[String] = None

  @Args4jOption(required = false,
    name = "-save_zscores",
    usage = "Path to write XHMM normalized, filtered, Z score matrix",
    handler = classOf[StringOptionArg])
  var zScorePath: Option[String] = None

  @Args4jOption(required = false,
    name = "-l",
    usage = "Input file is a list of paths")
  var readPathIsList: Boolean = false

  @Args4jOption(required = false,
    name = "-num_partitions",
    usage = "Desired number of partitions for read matrix. Defaults to number of samples.",
    handler = classOf[IntOptionArg])
  var numPartitions: Option[Int] = None
}

class CNVer(protected val args: CNVerArgs) extends BDGSparkCommand[CNVerArgs] {
  val companion = CNVer

  def run(sc: SparkContext): Unit = {
    // 1. Read alignment files
    if (args.readPathIsList) {
      args.readsPaths = sc.textFile(args.readsPaths.head).collect
    }

    val readProj = {
      var readFields = Seq(
        ARF.readMapped,
        ARF.duplicateRead,
        ARF.failedVendorQualityChecks,
        ARF.primaryAlignment,
        ARF.mapq,
        ARF.contigName,
        ARF.start,
        ARF.end,
        ARF.cigar,
        ARF.mateMapped,
        ARF.mateContigName,
        ARF.mateAlignmentStart,
        ARF.inferredInsertSize)
      Projection(readFields: _*)
    }

    val readsRdds = args.readsPaths.map(path => {
      // TODO: Add push down filters
      log.info("Loading {} alignment file", path)
      sc.loadAlignments(path, optProjection = Some(readProj), stringency = ValidationStringency.SILENT)
    })

    val targetsAsFeatures = {
      val targetProj = Projection(FF.contigName, FF.start, FF.end)
      sc.loadFeatures(args.targetsPath, optProjection = Some(targetProj))
    }

    // 2. Compute coverage
    val rdMatrix = Coverage.coverageMatrix(readsRdds, targetsAsFeatures, minMapQ = args.minMappingQuality, numPartitions = args.numPartitions)
    readsRdds.foreach(_.rdd.unpersist()) // Alignments no longer needed

    args.zScorePath.foreach(path => {
      rdMatrix.cache()
      Deca.writeXHMMMatrix(rdMatrix, path, label = "DECA._mean_cvg")
    })

    // 3. Normalize read depth
    val (zRowMatrix, zTargets) = Normalization.normalizeReadDepth(
      rdMatrix,
      minTargetMeanRD = args.minTargetMeanRD,
      maxTargetMeanRD = args.maxTargetMeanRD,
      minSampleMeanRD = args.minSampleMeanRD,
      maxSampleMeanRD = args.maxSampleMeanRD,
      maxSampleSDRD = args.maxSampleSDRD,
      maxTargetSDRDStar = args.maxTargetSDRDStar,
      initialKFraction = args.initialKFraction)

    rdMatrix.unpersist() // Read matrix no longer needed

    val zMatrix = ReadDepthMatrix(zRowMatrix, rdMatrix.samples, zTargets)
    args.zScorePath.foreach(path => {
      zMatrix.cache()
      Deca.writeXHMMMatrix(zMatrix, path, label = "Matrix")
    })

    // 4. Discover CNVs
    var features = HMM.discoverCNVs(zMatrix, M = args.M, T = args.T, p = args.p, D = args.D, minSomeQuality = args.minSomeQuality)
      .addSequences(readsRdds.head.sequences)
    features.saveAsGff3(args.outputPath, asSingleFile = true)
  }
}
