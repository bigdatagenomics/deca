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

import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.SequenceDictionary
import org.bdgenomics.adam.rdd.feature.FeatureRDD
import org.bdgenomics.deca.Timers._
import org.bdgenomics.deca.coverage.ReadDepthMatrix
import org.bdgenomics.deca.hmm.{ SampleModel, TransitionProbabilities }
import org.bdgenomics.deca.util.MLibUtils
import org.bdgenomics.formats.avro.{ Feature, Strand }
import org.bdgenomics.utils.misc.Logging

/**
 * Created by mlinderman on 4/4/17.
 */
object HMM extends Serializable with Logging {

  def discoverCNVs(readMatrix: ReadDepthMatrix,
                   sequences: SequenceDictionary = SequenceDictionary.empty,
                   M: Double = 3, T: Double = 6, p: Double = 1e-8, D: Double = 70000,
                   minSomeQuality: Double = 30.0): FeatureRDD = DiscoverCNVs.time {

    val sc = SparkContext.getOrCreate()

    // Generate transition probabilities from targets
    val transProb = sc.broadcast(TransitionProbabilities(readMatrix.targets, D = D, p = p, q = 1.0 / T))

    // Broadcast samples and targets so that features can be fixed up
    val targets = sc.broadcast(readMatrix.targets)
    val samples = sc.broadcast(readMatrix.samples)

    val cnvs = readMatrix.depth.rows.flatMap(obs => {
      // Create per-sample HMM model
      val model = SampleModel(obs.vector, transProb.value, M, p)

      // Discover CNVs
      val per_sample_cnvs = model.discoverCNVs(minSomeQuality)

      // Refine feature descriptions with coordinates, sample, etc.
      per_sample_cnvs.flatMap(raw_feature => {
        val attr = raw_feature.getAttributes

        val start_index = attr.get("START_TARGET").toInt
        val end_index = attr.get("END_TARGET").toInt

        val start_target = targets.value(start_index)
        val end_target = targets.value(end_index)

        def process(builder: Feature.Builder): Feature = {
          val innerAttr = raw_feature.getAttributes

          builder.setSource(samples.value(obs.index.toInt))
          builder.setStrand(Strand.INDEPENDENT)

          // Transform START_TARGET and END_TARGET to be 1-indexed
          innerAttr.put("START_TARGET", (start_index + 1).toString)
          innerAttr.put("END_TARGET", (end_index + 1).toString)

          builder.setAttributes(innerAttr)

          builder.build()
        }

        if (start_target.referenceName == end_target.referenceName) {
          val builder = Feature.newBuilder(raw_feature)
          builder.setContigName(start_target.referenceName)
          builder.setStart(start_target.start)
          builder.setEnd(end_target.end)

          Iterable(process(builder))
        } else {
          val builder1 = Feature.newBuilder(raw_feature)
          builder1.setContigName(start_target.referenceName)
          builder1.setStart(start_target.start)
          val optCl: Option[Long] = sequences(start_target.referenceName).map(_.length)
          val cl: Long = optCl.getOrElse(Long.MaxValue)
          val jcl: java.lang.Long = cl

          builder1.setEnd(jcl)

          val builder2 = Feature.newBuilder(raw_feature)
          builder2.setContigName(end_target.referenceName)
          builder2.setStart(0L)
          builder2.setEnd(end_target.end)

          Iterable(process(builder1), process(builder2))
        }
      })
    })

    FeatureRDD(cnvs, sequences)
  }
}
