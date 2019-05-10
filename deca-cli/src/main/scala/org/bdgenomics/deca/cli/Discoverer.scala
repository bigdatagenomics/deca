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
import org.bdgenomics.deca.cli.util.{ IntOptionHandler => IntOptionArg }
import org.bdgenomics.deca.{ Deca, HMM }
import org.bdgenomics.utils.cli._
import org.kohsuke.args4j.{ Option => Args4jOption }

/**
 * Created by mlinderman on 4/12/17.
 */
object Discoverer extends BDGCommandCompanion {
  val commandName = "discover"
  val commandDescription = "Call CNVs from normalized read matrix"

  def apply(cmdLine: Array[String]) = {
    new Discoverer(Args4j[DiscovererArgs](cmdLine))
  }
}

trait DiscoveryArgs {
  @Args4jOption(required = false,
    name = "-zscore_threshold",
    usage = "Depth Z score threshold (M). Defaults to 3.")
  var M: Double = 3

  @Args4jOption(required = false,
    name = "-mean_targets_cnv",
    usage = "Mean targets per CNV (T). Defaults to 6.")
  var T: Double = 6

  @Args4jOption(required = false,
    name = "-cnv_rate",
    usage = "CNV rate (p). Defaults to 1e-8.")
  var p: Double = 1e-8

  @Args4jOption(required = false,
    name = "-mean_target_distance",
    usage = "Mean within-CNV target distance (D). Defaults to 70000.")
  var D: Double = 70000

  @Args4jOption(required = false,
    name = "-min_some_quality",
    usage = "Min Q_SOME to discover a CNV. Defaults to 30.0.")
  var minSomeQuality: Double = 30.0
}

class DiscovererArgs extends Args4jBase with DiscoveryArgs {
  @Args4jOption(required = true,
    name = "-I",
    usage = "The XHMM normalized read depth matrix")
  var inputPath: String = null

  @Args4jOption(required = true,
    name = "-o",
    usage = "Path to write discovered CNVs as GFF3 file")
  var outputPath: String = null

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

class Discoverer(protected val args: DiscovererArgs) extends BDGSparkCommand[DiscovererArgs] {
  val companion = Discoverer

  def run(sc: SparkContext): Unit = {
    var matrix = Deca.readXHMMMatrix(args.inputPath, minPartitions = args.minPartitions)
    var features = HMM.discoverCNVs(matrix, M = args.M, T = args.T, p = args.p, D = args.D, minSomeQuality = args.minSomeQuality)
    features.saveAsGff3(args.outputPath, asSingleFile = !args.multiFile)
  }
}