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

import org.apache.spark.SparkContext
import org.bdgenomics.deca.cli.util.{ IntOptionHandler => IntOptionArg, StringOptionHandler => StringOptionArg }
import org.bdgenomics.deca.coverage.ReadDepthMatrix
import org.bdgenomics.deca.{ Deca, HMM, Normalization }
import org.bdgenomics.utils.cli._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }

/**
 * Created by mlinderman on 6/2/17.
 */

object NormalizingDiscoverer extends BDGCommandCompanion {
  val commandName = "normalize_and_discover"
  val commandDescription = "Normalize XHMM read-depth matrix and discover CNVs"

  def apply(cmdLine: Array[String]) = {
    new NormalizingDiscoverer(Args4j[NormalizingDiscovererArgs](cmdLine))
  }
}

class NormalizingDiscovererArgs extends Args4jBase with NormalizeArgs with DiscoveryArgs {
  @Args4jOption(required = true,
    name = "-I",
    usage = "The XHMM read depth matrix")
  var inputPath: String = null

  @Args4jOption(required = true,
    name = "-o",
    usage = "Path to write discovered CNVs as GFF3 file")
  var outputPath: String = null

  @Args4jOption(required = false,
    name = "-save_zscores",
    usage = "Path to write XHMM normalized, filtered, Z score matrix",
    handler = classOf[StringOptionArg])
  var zScorePath: Option[String] = None

  @Args4jOption(required = false,
    name = "-min_partitions",
    usage = "Desired minimum number of partitions to be created when reading in XHMM matrix",
    handler = classOf[IntOptionArg])
  var minPartitions: Option[Int] = None

  @Args4jOption(required = false,
    name = "-multi_file",
    usage = "Do not merge output files.")
  var multiFile: Boolean = false
}

class NormalizingDiscoverer(protected val args: NormalizingDiscovererArgs) extends BDGSparkCommand[NormalizingDiscovererArgs] {
  val companion = NormalizingDiscoverer

  def run(sc: SparkContext): Unit = {
    val matrix = Deca.readXHMMMatrix(args.inputPath,
      targetsToExclude = args.excludeTargetsPath,
      minTargetLength = args.minTargetLength,
      maxTargetLength = args.maxTargetLength,
      minPartitions = args.minPartitions)

    val (zRowMatrix, zTargets) = Normalization.normalizeReadDepth(
      matrix,
      minTargetMeanRD = args.minTargetMeanRD,
      maxTargetMeanRD = args.maxTargetMeanRD,
      minSampleMeanRD = args.minSampleMeanRD,
      maxSampleMeanRD = args.maxSampleMeanRD,
      maxSampleSDRD = args.maxSampleSDRD,
      maxTargetSDRDStar = args.maxTargetSDRDStar,
      fixedToRemove = args.fixedPCToRemove,
      initialKFraction = args.initialKFraction)

    matrix.unpersist()

    val zMatrix = ReadDepthMatrix(zRowMatrix, matrix.samples, zTargets)
    args.zScorePath.foreach(path => {
      zMatrix.cache()
      Deca.writeXHMMMatrix(zMatrix, path, label = "Matrix", asSingleFile = !args.multiFile)
    })

    var features = HMM.discoverCNVs(zMatrix,
      M = args.M, T = args.T, p = args.p, D = args.D,
      minSomeQuality = args.minSomeQuality)
    features.saveAsGff3(args.outputPath, asSingleFile = !args.multiFile)
  }
}
