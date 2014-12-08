#!/usr/bin/env bash
#
# Data selection with the feature decay algorithm
#
# Author: Spence Green

if [[ $# -lt 6 ]]; then
    echo Usage: `basename $0` mem arg [arg]
    echo
    echo args are passed directly to:
    java edu.stanford.nlp.mt.tools.FDACorpusSelection
    exit -1
fi

MEM=$1
shift

JAVA_OPTS="-server -ea -Xmx${MEM} -Xms${MEM} -XX:+UseParallelGC -XX:+UseParallelOldGC"

java $JAVA_OPTS edu.stanford.nlp.mt.tools.FDACorpusSelection $*
