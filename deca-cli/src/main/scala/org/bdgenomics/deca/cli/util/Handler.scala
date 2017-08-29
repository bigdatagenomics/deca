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
package org.bdgenomics.deca.cli.util

import org.kohsuke.args4j.{ CmdLineParser, OptionDef }
import org.kohsuke.args4j.spi.{ OptionHandler ⇒ Args4JOptionHandler, Parameters, Setter }

/**
 * Created by mlinderman on 5/15/17.
 */

// Adapted from: https://github.com/hammerlab/args4s/blob/master/src/main/scala/org/hammerlab/args4s/Handler.scala
class Handler[T](parser: CmdLineParser,
                 option: OptionDef,
                 setter: Setter[T],
                 defaultMetaVariable: String,
                 fn: String => T)
    extends Args4JOptionHandler[T](parser, option, setter) {
  override def getDefaultMetaVariable: String = defaultMetaVariable

  override def parseArguments(params: Parameters): Int = {
    setter.addValue(fn(params.getParameter(0)))
    1
  }
}
