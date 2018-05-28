# Benchmarking

The sections below list example commands for running the different XHMM and
DECA benchmarks reported in the DECA publication. For clarity specific paths
have been changed to generic "/path/to/...". Different analyses would vary the
number of cores (or YARN executors) and the number of samples.

## Running XHMM

Two scripts for running the multiple XHMM commands are distributed in the
`scripts/` directory. The specific commands for running XHMM on the workstation (with 16 threads):

```
$DECA_SRC/scripts/xhmm_normalize.sh -x /path/to/20130108.exome.targets.gc.txt -x /path/to/20130108.exome.targets.lc.txt -p 16 -t xhmm.time.txt xhmm/DATA.2535.RD.txt xhmm/DATA.2535
$DECA_SRC/xhmm_discover.sh -t xhmm.time.txt xhmm/DATA.2535.PCA_normalized.filtered.sample_zscores.RD.txt xhmm/DATA.2535
```

The same scripts are used on the Hadoop cluster, but launched with YARN
distributed shell (requesting 204800M and 16 cores, so as to obtain an entire
cluster node).

## Running DECA CNV discovery

The specific command for running CNV discovery on the workstation:

```
/usr/bin/time -f "%C\t%E\t%U\t%M\t%K" --append -o deca.time.txt \
    deca-submit \
        --master local[16] \
        --driver-class-path $DECA_JAR \
        --conf spark.local.dir=/path/to/temp/directory \
        --conf spark.driver.maxResultSize=0 \
        --conf spark.kryo.registrationRequired=true \
        --executor-memory 96G \
        --driver-memory 16G 
        -- \
        normalize_and_discover \
        -exclude_targets 20130108.exome.targets.exclude.txt \
        -min_some_quality 29.5 \
        -print_metrics \
        -I DATA.2535.RD.txt \
        -o DATA.2535.gff3
```

and on the Hadoop cluster (with a varying number of executors, as set by the `$EXEC` variable):

```
/usr/bin/time -f "%C\t%E\t%U\t%M\t%K" --append -o deca.time.txt \
    deca-submit \
        --master yarn \
        --deploy-mode cluster \
        --num-executors $EXEC \
        --executor-memory 72G \
        --executor-cores 5 \
        --driver-memory 72G \
        --driver-cores 5 \
        --conf spark.driver.maxResultSize=0 \
        --conf spark.yarn.executor.memoryOverhead=4096 \
        --conf spark.yarn.driver.memoryOverhead=4096 \
        --conf spark.kryo.registrationRequired=true \
        --conf spark.hadoop.mapreduce.input.fileinputformat.split.minsize=8388608 \
        --conf spark.default.parallelism=$(( 2 * $EXEC * 5 )) \
        --conf spark.dynamicAllocation.enabled=false \
        -- normalize_and_discover \
        -min_partitions $(( 2 * $EXEC * 5 )) \
        -exclude_targets hdfs://path/to/20130108.exome.targets.exclude.txt \
        -min_some_quality 29.5 \
        -print_metrics \
        -I "hdfs://path/to/DATA.2535.RD.txt" \
        -o "hdfs://path/to/DATA.2535.gff3"
```

The `20130108.exome.targets.exclude.txt` is the concatenation
`20130108.exome.targets.gc.txt` and `20130108.exome.targets.lc.txt` files,
which are in turn generated from `20130108.exome.targets.interval_list` as
described in the [XHMM
tutorial](http://atgu.mgh.harvard.edu/xhmm/tutorial.shtml).
`20130108.exome.targets.interval_list` is generated from the [BED
file](ftp://ftp.1000genomes.ebi.ac.uk/vol1/ftp/technical/reference/exome_pull_down_targets//20130108.exome.targets.bed)
distributed by the 1000 Genomes project.

## Running DECA coverage

The specific command for running on the coverage analyses on the workstation:

```
/usr/bin/time -f "%C\t%E\t%U\t%M\t%K" --append -o deca.time.txt \
    deca-submit \
        --master local[16] \
        --driver-class-path $DECA_JAR \
        --conf spark.local.dir=/path/to/temp/directory \
        --conf spark.driver.maxResultSize=0 \
        --conf spark.kryo.registrationRequired=true \
        --executor-memory 96G \
        --driver-memory 16G 
        -- \
        coverage \
        -L 20130108.exome.targets.filtered.interval_list \
        -print_metrics \
        -I /path/to/bam1.bam ... /path/to/bam50.bam \
        -o DECA.50.RD.txt
```

and on the Hadoop cluster with a dynamic number of executors:

```
/usr/bin/time -f "%C\t%E\t%U\t%M\t%K" --append -o deca.time.txt \
    deca-submit \
        --master yarn \
        --deploy-mode cluster \
        --executor-memory 72G \
        --executor-cores 5 \
        --driver-memory 72G \
        --driver-cores 5 \
        --conf spark.driver.maxResultSize=0 \
        --conf spark.yarn.executor.memoryOverhead=4096 \
        --conf spark.yarn.driver.memoryOverhead=4096 \
        --conf spark.kryo.registrationRequired=true \
        --conf spark.hadoop.mapreduce.input.fileinputformat.split.minsize=268435456 \
        --conf spark.dynamicAllocation.enabled=true \
        -- coverage \
        -L hdfs://path/to/20130108.exome.targets.filtered.interval_list  \
        -print_metrics \
        -I "hdfs://path/to/bam.list" -l \
        -o "hdfs://path/to/DECA.2535.RD.txt
```

Due to limits on command line length, coverage often needs to be invoked with a
file containing a list of bam files (indicated by `-l` option in the example
above) instead of the bam files themselves.

The `20130108.exome.targets.filtered.interval_list` file was generated by
removing all targets in `20130108.exome.targets.exclude.txt` from
`20130108.exome.targets.interval_list`.
