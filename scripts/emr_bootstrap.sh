#!/usr/bin/env bash

usage()
{
        cat << EOF
usage: `basename $0` [JAR]

Bootstraps Amazon Elastic MapReduce cluster. If the s3 URL for JAR file is provided as an argument, the script
will copy that file onto the nodes instead of building DECA from source.
EOF
}

DECA_JAR=
if [[ $# -eq 1 ]]; then
    DECA_JAR=$1
    echo "Using pre-built JAR: $DECA_JAR"
fi

# Only perform remaining steps on the master node
if grep --quiet '"isMaster": false' /mnt/var/lib/info/instance.json; then
    echo "This is not the master node, exiting."
    exit 0
fi

# Install git
sudo yum install git -y

# Install Maven
cd $HOME
mkdir maven
cd maven
wget http://apache.mirrors.lucidnetworks.net/maven/maven-3/3.5.3/binaries/apache-maven-3.5.3-bin.tar.gz
tar -xzvf apache-maven-3.5.3-bin.tar.gz
export PATH=$PATH:$PWD/apache-maven-3.5.3/bin

# Download (and install) DECA (adding deca-submit to your PATH)
cd $HOME
git clone https://github.com/bigdatagenomics/deca.git
cd deca
mvn clean

if [[ -n $DECA_JAR ]]; then
    mkdir -p deca-cli/target
    aws s3 cp $DECA_JAR $HOME/deca/deca-cli/target/
else
    export MAVEN_OPTS="-Xmx512m"
    mvn -Dmaven.test.skip=true clean package -P native-lgpl
fi

# Add path information
echo -e "\n\n# Added by bootstrap actions" >> $HOME/.bash_profile
echo "export PATH=\$PATH:$HOME/deca/bin" >> $HOME/.bash_profile
