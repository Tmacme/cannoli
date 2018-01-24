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
package org.bdgenomics.cannoli.cli

import htsjdk.samtools.ValidationStringency
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.ADAMSaveAnyArgs
import org.bdgenomics.adam.rdd.variant.{
  VariantContextRDD,
  VCFInFormatter,
  VCFOutFormatter
}
import org.bdgenomics.utils.cli._
import org.bdgenomics.utils.misc.Logging
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }

object Bcftools extends BDGCommandCompanion {
  val commandName = "bcftools"
  val commandDescription = "ADAM Pipe API wrapper for BCFtools norm."

  def apply(cmdLine: Array[String]) = {
    new Bcftools(Args4j[BcftoolsArgs](cmdLine))
  }
}

class BcftoolsArgs extends Args4jBase with ADAMSaveAnyArgs with ParquetArgs {
  @Argument(required = true, metaVar = "INPUT", usage = "Location to pipe from, in VCF format.", index = 0)
  var inputPath: String = null

  @Argument(required = true, metaVar = "OUTPUT", usage = "Location to pipe to, in VCF format.", index = 1)
  var outputPath: String = null

  @Args4jOption(required = false, name = "-bcftools_path", usage = "Path to the BCFtools executable. Defaults to bcftools.")
  var bcftoolsPath: String = "bcftools"

  @Args4jOption(required = true, name = "-bcftools_reference", usage = "Reference sequence for analysis. An index file (.fai) will be created if none exists.")
  var referencePath: String = null

  @Args4jOption(required = false, name = "-docker_image", usage = "Docker image to use. Defaults to quay.io/biocontainers/bcftools:1.6--0.")
  var dockerImage: String = "quay.io/biocontainers/bcftools:1.6--0"

  @Args4jOption(required = false, name = "-use_docker", usage = "If true, uses Docker to launch BCFtools. If false, uses the BCFtools executable path.")
  var useDocker: Boolean = false

  @Args4jOption(required = false, name = "-single", usage = "Saves OUTPUT as single file.")
  var asSingleFile: Boolean = false

  @Args4jOption(required = false, name = "-defer_merging", usage = "Defers merging single file output.")
  var deferMerging: Boolean = false

  @Args4jOption(required = false, name = "-disable_fast_concat", usage = "Disables the parallel file concatenation engine.")
  var disableFastConcat: Boolean = false

  @Args4jOption(required = false, name = "-stringency", usage = "Stringency level for various checks; can be SILENT, LENIENT, or STRICT. Defaults to STRICT.")
  var stringency: String = "STRICT"

  // must be defined due to ADAMSaveAnyArgs, but unused here
  var sortFastqOutput: Boolean = false
}

/**
 * Bcftools.
 */
class Bcftools(protected val args: BcftoolsArgs) extends BDGSparkCommand[BcftoolsArgs] with Logging {
  val companion = Bcftools
  val stringency: ValidationStringency = ValidationStringency.valueOf(args.stringency)

  def run(sc: SparkContext) {
    val input: VariantContextRDD = sc.loadVcf(args.inputPath, stringency = stringency)

    implicit val tFormatter = VCFInFormatter
    implicit val uFormatter = new VCFOutFormatter(sc.hadoopConfiguration)

    val bcftoolsCommand = if (args.useDocker) {
      Seq("docker",
        "run",
        "--interactive",
        "--rm",
        args.dockerImage,
        "bcftools",
        "norm",
        "--fasta-ref",
        args.referencePath).mkString(" ")
    } else {
      Seq(args.bcftoolsPath,
        "norm",
        "--fasta-ref",
        args.referencePath).mkString(" ")
    }

    val output: VariantContextRDD = input.pipe[VariantContext, VariantContextRDD, VCFInFormatter](bcftoolsCommand)
      .transform(_.cache())

    output.saveAsVcf(args, stringency)
  }
}
