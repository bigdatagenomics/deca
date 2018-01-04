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
the Spark driver classpath, e.g.

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
on a 16-core workstation with 128 GB RAM is below. Note that you will need to set the `DECA_JAR` environment variable, set `spark.local.dir` to a suitable temporary directory for your system and likely need to change the executor and driver memory to suitable values for your system. The `exclude_targets.txt` and `DATA.RD.txt` files from the XHMM tutorial data are also distributed as part of the DECA test resources in the `deca-core/src/test/resources/` directory.

```dtd
deca-submit \
--master local[16] \
--driver-class-path $DECA_JAR \
--conf spark.local.dir=/data/scratch/$USER \
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

To call CNVs from the original [BAM files](http://atgu.mgh.harvard.edu/xhmm/EXAMPLE_BAMS.zip):

```dtd
deca-submit \
--master local[16] \
--driver-class-path $DECA_JAR \
--conf spark.local.dir=/data/scratch/$USER \
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
	-exclude_targets <path-to-exclude-file> \
	-min_some_quality 29.5 \
	-I <path-to-read-depth-matrix> \
	-o <path-to-save-cnv-calls>
```

Note that many of the parameters above, e.g. driver and executor cores and memory, are specific to a particular cluster 
environment and would likely need to be modified for other environments.

## Running DECA using Toil on a workstation or AWS

We provide [Toil](http://www.ncbi.nlm.nih.gov/pubmed/28398314) workflows that allow DECA to be run either
on a local computer or on a cluster on the Amazon Web Services (AWS) cloud.
These workflows are written in Python and package DECA, Apache Spark, and Apache
Hadoop using Docker containers. This packaging automates the setup of Apache Spark, reducing the barrier-to-entry for
using DECA. To run either workflow, the user will need to [install
Toil](http://toil.readthedocs.io/en/3.10.1/gettingStarted/install.html#basic-installation).
To run the AWS workflow, the user will additionally need to follow the AWS setup
instructions.

*Note:* Support is currently limited to Python 2. Python 3 support is forthcoming.

### Installing the DECA Workflows

Once Toil has been installed, the user will need to download and install the
[bdgenomics.workflows](https://github.com/bigdatagenomics/workflows) package,
which contains the DECA workflows.

#### Installing from PyPI

For maximum convenience, `bdgenomics.workflows` is pip installable:

```
pip install bdgenomics.workflows==0.1.0
```

#### Installing from source

To install this package, run `make develop`:

```
git clone https://github.com/bigdatagenomics/workflows
cd workflows
make develop
```

This step should be run inside of a Python virtualenv. If run locally, this step
should be run inside of the same virtualenv that Toil was installed into. If run
on AWS, this step should be run inside of a virtualenv that was created on the
Toil AWS autoscaling cluster.

### Input Files

The DECA workflow takes two inputs:

1. A feature file that defines the regions over which to call copy number
   variants. This file can be formatted using any of the BED, GTF/GFF2, GFF3,
   Interval List, or NarrowPeak formats. In the AWS workflow, the ADAM Parquet
   Feature format is also supported.
2. A manifest file that contains paths to a set of sorted BAM files. Each file
   must have a scheme listed. In local mode, the file://, http://, and ftp://
   schemes are supported. On AWS, the s3a://, http://, and ftp:// schemes are
   supported. S3a is an overlay over the AWS Simple Storage System (S3) cloud
   data store which is provided by Apache Hadoop.

### Running Locally

To run locally, we invoke the following command:

```
bdg-deca \
  --targets <regions> \
  --samples <manifest> \
  --output-dir <path-to-save> \
  --memory <memory-in-GB> \
  --run-local \
  file:<toil-jobstore-path>
```

This command will run in Toil’s single machine mode, and will save the CNV
calls to `<path-to-save>/cnvs.gff`. `<toil-jobstore-path>` is the path to a
temporary directory where Toil will save intermediate files. The
`<memory-in-GB>` parameter should be specified without units; e.g., to allocate
20GB of memory, pass "--memory 20".

### Running on AWS

To run on AWS, we rely on Toil’s AWS provisioner, which starts a cluster on the
AWS cloud. Toil’s AWS provisioner runs on top of [Apache
Mesos](https://mesos.apache.org) and supports dynamically scaling the number of
nodes in the cluster to the amount of tasks being run. First, [create a Toil cluster on
AWS](http://toil.readthedocs.io/en/3.10.1/running/amazon.html).

Once the Toil cluster has launched, SSH onto the cluster, following the
instructions provided in the Toil/AWS documentation. To install
bdgenomics.workflows, run:

```
apt-get update
apt-get install git
git clone https://github.com/bigdatagenomics/workflows.git
cd workflows
virtualenv --system-site-packages venv
. venv/bin/activate
make develop
```

To run the DECA workflow, invoke the following command:

```
bdg-deca \
  --targets <regions> \
  --samples <manifest> \
  --output-dir <path-to-save> \
  --memory <memory-in-GB> \
  --provisioner aws \
  --batchSystem mesos \
  --mesosMaster $(hostname -i):5050 \
  --nodeType <type> \
  --num-nodes <spark-workers + 1> \
  --minNodes <spark-workers + 2> \
  aws:<region>:<toil-jobstore>
```
                                                                                                                               
Toil will launch a cluster with `spark-workers + 2` worker nodes to run this
workflow. For optimal performance, we recommend choosing a number of Apache
Spark worker nodes such that you have no less than 256MB of data per core. All file paths used in AWS mode must be files stored
in AWS’s S3 storage system, and must have an s3a:// URI scheme.
                                                                                                                              
# License

DECA is released under an [Apache 2.0 license](LICENSE.txt).
