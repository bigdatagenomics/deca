# Tutorial

As described in the [README](README.md) a small dataset (30 samples by 300 targets) is distributed as part of the [XHMM tutorial](http://atgu.mgh.harvard.edu/xhmm/tutorial.shtml). Execute the following commands to call CNVs in that dataset with DECA. A video of this workflow running on OSX is [available](https://i.imgur.com/gp0D4B1.gifv). More detail about the individual steps, parameters, etc. is included in the [README](README.md).

1. Install DECA as described in the README:

    ```
    git clone git@github.com:bigdatagenomics/deca.git
    ```

    Assuming that Maven and Spark are already installed (with `$SPARK_HOME` defined), build DECA with native support.
    ```
    cd deca
    export MAVEN_OPTS="-Xmx512m"
    mvn package -P native-lgpl
    ```

    Add the `deca-submit` script to `PATH` and create an environment variable pointing to the DECA jar.
    ```
    export PATH=$PATH:$PWD/bin
    export DECA_JAR=$PWD/deca-cli/target/deca-cli_2.11-0.2.1-SNAPSHOT.jar 
    ```
  
1. Download and prepare XHMM tutorial data:

    ```
    cd ..
    wget http://atgu.mgh.harvard.edu/xhmm/RUN.zip
    unzip RUN.zip
    cat low_complexity_targets.txt extreme_gc_targets.txt | sort -u > exclude_targets.txt
    ```

1. Run DECA on a workstation:

    Note that you will need to set `spark.local.dir` to a suitable temporary directory for your system and likely need to change the number of cores, executor memory and driver memory to suitable values for your system. The resulting CNVs will be written to `DECA.gff3`.

    ```
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
