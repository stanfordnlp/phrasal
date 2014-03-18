#!/usr/bin/env bash

if [ $# -le 1 ]; then
    echo Usage: `basename $0` "[local|cluster]" options
    exit -1
fi

mode=$1
shift

if [ $mode == "local" ]; then
    MEM=9g
elif [ $mode == "cluster" ]; then
    MEM=150g
else
    echo Usage: `basename $0` "[local|cluster]" options
    exit -1
fi

DECODER_OPTS="-Djava.library.path=${JAVANLP_HOME}/projects/mt/src-cc"
JAVA_OPTS="-server -ea -Xmx${MEM} -Xms${MEM} -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:PermSize=256m -XX:MaxPermSize=256m"

java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.service.PhrasalService $*
