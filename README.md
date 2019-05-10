DECA: Distributed Exome CNV Analyzer
====

# Introduction

DECA is a distributed re-implementation of the [XHMM](https://atgu.mgh.harvard.edu/xhmm/) exome CNV caller using ADAM and Apache Spark.

# Getting Started

## Installation

**Note**: These instructions are shared with other tools that build on [ADAM](https://github.com/bigdatagenomics/adam).

### Building from Source

You will need to have [Maven](http://maven.apache.org/) installed in order to build DECA.

> **Note:** The default configuration is for Hadoop 2.7.3. If building against a different
> version of Hadoop, please edit the build configuration in the `<properties>` section of
> the `pom.xml` file.

```dtd
$ git clone https://github.com/.../deca.git
$ cd deca
$ export MAVEN_OPTS="-Xmx512m"
$ mvn clean package
```

### Installing Spark

You'll need to have a Spark release on your system and the `$SPARK_HOME` environment variable pointing at it; prebuilt binaries can be downloaded from the
[Spark website](http://spark.apache.org/downloads.html). DECA has been developed and tested with
[Spark 2.1.0 built against Hadoop 2.7 with Scala 2.11](http://d3kbcqa49mib13.cloudfront.net/spark-2.1.0-bin-hadoop2.7.tgz), but any more recent Spark distribution should likely work.

## Helpful Scripts

The `bin/deca-submit` script wraps the `spark-submit` commands to set up and launch DECA.

## Commands

```dtd
$ deca-submit

Usage: deca-submit [<spark-args> --] <deca-args> [-version]

Choose one of the following commands:

             normalize : Normalize XHMM read-depth matrix
              coverage : Generate XHMM read depth matrix from read data
              discover : Call CNVs from normalized read matrix
normalize_and_discover : Normalize XHMM read-depth matrix and discover CNVs
                   cnv : Discover CNVs from raw read data

```
You can learn more about a command, by calling it without arguments or with `--help`, e.g.

```dtd
$ deca-submit normalize_and_discover --help
 -I VAL                   : The XHMM read depth matrix
 -cnv_rate N              : CNV rate (p). Defaults to 1e-8.
 -exclude_targets STRING  : Path to file of targets (chr:start-end) to be excluded from analysis
 -fixed_pc_toremove INT   : Fixed number of principal components to remove if defined. Defaults to undefined.
 -h (-help, --help, -?)   : Print help
 -initial_k_fraction N    : Set initial k to fraction of max components. Defaults to 0.10.
 -max_sample_mean_RD N    : Maximum sample mean read depth prior to normalization. Defaults to 200.
 -max_sample_sd_RD N      : Maximum sample standard deviation of the read depth prior to normalization. Defaults to 150.
 -max_target_length N     : Maximum target length. Defaults to 10000.
 -max_target_mean_RD N    : Maximum target mean read depth prior to normalization. Defaults to 500.
 -max_target_sd_RD_star N : Maximum target standard deviation of the read depth after normalization. Defaults to 30.
 -mean_target_distance N  : Mean within-CNV target distance (D). Defaults to 70000.
 -mean_targets_cnv N      : Mean targets per CNV (T). Defaults to 6.
 -min_partitions INT      : Desired minimum number of partitions to be created when reading in XHMM matrix
 -min_sample_mean_RD N    : Minimum sample mean read depth prior to normalization. Defaults to 25.
 -min_some_quality N      : Min Q_SOME to discover a CNV. Defaults to 30.0.
 -min_target_length N     : Minimum target length. Defaults to 10.
 -min_target_mean_RD N    : Minimum target mean read depth prior to normalization. Defaults to 10.
 -o VAL                   : Path to write discovered CNVs as GFF3 file
 -print_metrics           : Print metrics to the log on completion
 -save_zscores STRING     : Path to write XHMM normalized, filtered, Z score matrix
 -zscore_threshold N      : Depth Z score threshold (M). Defaults to 3.
```

## Using native library algebra libraries

Apache Spark includes the [Netlib-Java](https://github.com/fommil/netlib-java) library for high-performance linear algebra. 
Netlib-Java can invoke optimized BLAS and Lapack system libraries if available; however, many Spark distributions are built 
without Netlib-Java system library support. You may be able to use system libraries by including the DECA jar on 
the Spark driver classpath when running locally, e.g.

```dtd
deca-submit --driver-class-path $DECA_JAR ...
```
or you may need to rebuild Spark as described in the [Spark MLlib guide](http://spark.apache.org/docs/2.1.0/ml-guide.html). 

If you see the following warning messages in the log file, you have not successfully invoked the system libraries:

```dtd
WARN  BLAS:61 - Failed to load implementation from: com.github.fommil.netlib.NativeSystemBLAS
WARN  BLAS:61 - Failed to load implementation from: com.github.fommil.neltlib.NativeRefARPACK
```

To build DECA with the optimized netlib native code in, you will need to invoke the `native-lgpl` profile when running Maven:

```
mvn package -P native-lgpl
```

We cannot package this code by default, as netlib is licensed under the LGPL and cannot be bundled in Apache 2 licensed code.

# Example Usage 

## Running DECA in "stand-alone" mode on a workstation

A small dataset (30 samples by 300 targets) is distributed as part of the [XHMM tutorial](http://atgu.mgh.harvard.edu/xhmm/tutorial.shtml). 
An example DECA command to call CNVs from the [pre-computed read-depth matrix and related files](http://atgu.mgh.harvard.edu/xhmm/RUN.zip)
on a 16-core workstation with 128 GB RAM is below. Note that you will need to set the `DECA_JAR` environment variable to point to the jar file created by `mvn package`, set `spark.local.dir` to a suitable temporary directory for your system and likely need to change the executor and driver memory to suitable values for your system. The `exclude_targets.txt` and `DATA.RD.txt` files from the XHMM tutorial data are also distributed as part of the DECA test resources in the `deca-core/src/test/resources/` directory.

From within the unzip'd RUN directory, prepare `exclude_targets.txt`:

```
cat low_complexity_targets.txt extreme_gc_targets.txt | sort -u > exclude_targets.txt
```

then run DECA:

```dtd
deca-submit \
--master local[16] \
--driver-class-path $DECA_JAR \
--conf spark.local.dir=/path/to/temp/directory \
--conf spark.driver.maxResultSize=0 \
--conf spark.kryo.registrationRequired=true \
--executor-memory 96G --driver-memory 16G \
-- normalize_and_discover \
-min_some_quality 29.5 \
-exclude_targets exclude_targets.txt \
-I DATA.RD.txt \
-o DECA.gff3
```

The resulting [GFF3](https://github.com/The-Sequence-Ontology/Specifications/blob/master/gff3.md) file should contain

```dtd
22      HG00121 DEL     18898402        18913235        9.167771318038923       .       .       END_TARGET=117;START_TARGET=104;Q_SOME=90;Q_START=8;Q_STOP=4;Q_EXACT=9;Q_NON_DIPLOID=90
22      HG00113 DUP     17071768        17073440        25.32122306047942       .       .       END_TARGET=11;START_TARGET=4;Q_SOME=99;Q_START=53;Q_STOP=25;Q_EXACT=25;Q_NON_DIPLOID=99
```

The `exlude_targets.txt` file is the unique combination of the `extreme_gc_targets.txt` and `low_complexity_targets.txt` 
files provided in the tutorial data. The `min_some_quality` parameter is set to 29.5 to mimic XHMM behavior which uses a 
default minimum SOME quality of 30 *after* rounding (while DECA applies the filter prior to rounding). Depending on your particular
computing environment, you may need to modify the [spark-submit](https://spark.apache.org/docs/latest/submitting-applications.html) 
[configuration parameters](https://spark.apache.org/docs/latest/configuration.html). `spark.driver.maxResultSize` is set to 0 (unlimited) 
to address errors collecting larger amounts of data to the driver.

The corresponding xcnv output from XHMM is:

```dtd
SAMPLE  CNV     INTERVAL        KB      CHR     MID_BP  TARGETS NUM_TARG        Q_EXACT Q_SOME  Q_NON_DIPLOID   Q_START Q_STOP  MEAN_RD MEAN_ORIG_RD
HG00121 DEL     22:18898402-18913235    14.83   22      18905818        104..117        14      9       90      90      8       4       -2.51   37.99
HG00113 DUP     22:17071768-17073440    1.67    22      17072604        4..11   8       25      99      99      53      25      4.00    197.73
```

View a [recording](https://i.imgur.com/gp0D4B1.gifv) of the above installation and CNV calling workflow executed on OSX.

To call CNVs from the original [BAM files](http://atgu.mgh.harvard.edu/xhmm/EXAMPLE_BAMS.zip) compute the coverage:

```dtd
deca-submit \
--master local[16] \
--driver-class-path $DECA_JAR \
--conf spark.local.dir=/path/to/temp/directory \
--conf spark.driver.maxResultSize=0 \
--conf spark.kryo.registrationRequired=true \
--executor-memory 96G --driver-memory 16G \
-- coverage \
-L EXOME.interval_list \
-I *.bam 
-o DECA.RD.txt
```

followed by the `normalize_and_discovery` command above (with `DECA.RD.txt` as the input). DECA's coverage calculation is 
designed to match the output of the GATK DepthOfCoverage command specified in the XHMM protocol, i.e. count fragment depth with 
zero minimum base quality.

## Running DECA on a YARN cluster

The equivalent example command to call CNVs on a YARN cluster with Spark dynamic allocation would be:

```
deca-submit \
	--master yarn \
	--deploy-mode cluster \
	--num-executors 1 \
	--executor-memory 72G \
	--executor-cores 5 \
	--driver-memory 72G \
	--driver-cores 5 \
	--conf spark.driver.maxResultSize=0 \
	--conf spark.yarn.executor.memoryOverhead=4096 \
	--conf spark.yarn.driver.memoryOverhead=4096 \
	--conf spark.kryo.registrationRequired=true \
	--conf spark.hadoop.mapreduce.input.fileinputformat.split.minsize=$(( 8 * 1024 * 1024 )) \
	--conf spark.default.parallelism=10 \
	--conf spark.dynamicAllocation.enabled=true \
	-- normalize_and_discover \
	-min_partitions 10 \
	-exclude_targets "hdfs://path/to/exclude_targets.txt" \
	-min_some_quality 29.5 \
	-I "hdfs://path/to/DATA.RD.txt" \
	-o "hdfs://path/to/DECA.gff3"
```

Note that many of the parameters above, e.g. driver and executor cores and memory, are specific to a particular cluster 
environment and would likely need to be modified for other environments.

## Running DECA on AWS with Elastic MapReduce

DECA can readily be run on Amazon AWS using the Elastic MapReduce (EMR) [Spark configuration](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/emr-spark.html). Data can be read from and written to S3 using the s3a:// scheme. For example, the 1000 Genomes data are available as a [public dataset](https://aws.amazon.com/1000genomes/) on S3 in the `1000genomes` bucket (i.e. `s3a://1000genomes/...`). S3a is an overlay over the AWS Simple Storage System (S3) cloud data store which is provided by Apache Hadoop.

Note that unlike HDFS, S3 is an eventually-consistent filesystem and so you may encounter problems when trying to read recently written files, such as occurs at the end of the DECA operations when combining sharded files. When writing to S3 use the `-multi_file` option to leave the files sharded for subsequent combination or analysis.

DECA has been tested with emr-5.13. Clusters can be created with the command-line tools or the AWS management console. A JSON file [`emr_config.json`](scripts/emr_config.json) is provided in the scripts directory to configure clusters for maximum resource utilization.

A bootstrap script [`emr_bootstrap.sh`](scripts/emr_bootstrap.sh) is provided in the scripts directory for use as a [bootstrap action](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-plan-bootstrap.html). The bootstrap script can copy a pre-built JAR onto the cluster (faster) or build DECA directly from GitHub (slower). To use the bootstrap script, copy it to S3 and provide the S3 path as the bootstrap action when creating the cluster. To copy a pre-built JAR onto the cluster provide a s3 path to the DECA CLI jar, e.g. `s3://path/to/deca-cli_2.11-0.2.1-SNAPSHOT.jar`, as the optional argument to bootstrap action. After connecting to the EMR master node via SSH, you can launch DECA as you would on any YARN cluster. For example the following command calls CNVs in the entire 1000 Genomes phase 3 cohort on a cluster of i3.2xlarge nodes.

```
deca-submit \
    --master yarn \
    --deploy-mode cluster \
    --num-executors 7 \
    --executor-memory 22G \
    --executor-cores 4 \
    --driver-memory 22G \
    --driver-cores 5 \
    --conf spark.driver.maxResultSize=0 \
    --conf spark.kryo.registrationRequired=true \
    --conf spark.dynamicAllocation.enabled=false \
    --conf spark.hadoop.mapreduce.input.fileinputformat.split.minsize=$(( 104 * 1024 * 1024 )) \
    --conf spark.default.parallelism=28 \
    --conf spark.executor.extraJavaOptions="-XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=35" \
    -- normalize_and_discover \
    -min_partitions 28 \
    -exclude_targets "s3a://path/to/20130108.exome.targets.exclude.txt" \
    -min_some_quality 29.5 \
    -print_metrics \
    -I "s3a://path/to/DATA.2535.RD.txt" \
    -o "s3a://path/to/DATA.2535.RD.gff3" \
    -multi_file
```

Alternately jobs can be launched as steps on cluster. In this approach, no bootstrap actions are needed; the JAR file can be downloaded directly from S3. The `spark-submit` arguments are:

```
--class org.bdgenomics.deca.cli.DecaMain
--conf spark.serializer=org.apache.spark.serializer.KryoSerializer
--conf spark.kryo.registrator=org.bdgenomics.deca.serialization.DECAKryoRegistrator
```

(in addition to the arguments shown above) and the application arguments would be

```
normalize_and_discover
-min_partitions 28
-exclude_targets s3a://path/to/20130108.exome.targets.exclude.txt
-min_some_quality 29.5
-print_metrics
-I s3a://path/to/DATA.2535.RD.txt
-o s3a://path/to/DATA.2535.RD.gff3
-multi_file
```

## Running DECA on Databricks

DECA can readily be run on [Databricks](https://databricks.com) on the Amazon cloud. DECA has been tested on Databricks Light 2.4 as a spark-submit job using the DECA jar fetched from a S3 bucket. As with EMR, data can be read from and written to S3 using the s3a:// scheme. The Databricks cluster was configured to access S3 via [AWS IAM roles](https://docs.databricks.com/administration-guide/cloud-configurations/aws/iam-roles.html#secure-access-to-s3-buckets-using-iam-roles). Note that access to any public buckets, e.g. the 1000genomes bucket, must also be included in the cross account IAM role created according to the above instructions. The same issues with eventual consistency described above also apply when writing data to S3 from the Databricks cluster.

An example configuration for calling CNVs directly from the original BAM files:

```json
[
  "--class", "org.bdgenomics.deca.cli.DecaMain",
  "--conf", "spark.serializer=org.apache.spark.serializer.KryoSerializer",
  "--conf", "spark.kryo.registrator=org.bdgenomics.deca.serialization.DECAKryoRegistrator",
  "--conf", "spark.kryo.registrationRequired=true",
  "--conf", "spark.hadoop.fs.s3.impl=com.databricks.s3a.S3AFileSystem",
  "--conf", "spark.hadoop.fs.s3a.impl=com.databricks.s3a.S3AFileSystem",
  "--conf", "spark.hadoop.fs.s3n.impl=com.databricks.s3a.S3AFileSystem",
  "--conf", "spark.hadoop.fs.s3a.canned.acl=BucketOwnerFullControl",
  "--conf", "spark.hadoop.fs.s3a.acl.default=BucketOwnerFullControl",
  "--conf", "spark.hadoop.mapreduce.input.fileinputformat.split.minsize=536870912",
  "s3://path/to/deca-cli_2.11-0.2.1-SNAPSHOT.jar",
  "cnv",
  "-L", "s3a://path/to/20130108.exome.targets.filtered.interval_list",
  "-I", "s3a://path/to/1kg.bams.50.list",
  "-l",
  "-o", "s3a://path/to/DECA.50.gff3",
  "-multi_file"
]
```

# License

DECA is released under an [Apache 2.0 license](LICENSE.txt).
