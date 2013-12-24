#!/usr/bin/env bash
#
# Generate a word->class mapping from monolingual
# input text.
#
# Author: Spence Green
#
if [ $# -lt 3 ]; then
    echo Script usage: `basename $0` mem threads arg [arg]
    echo
    echo args are passed to MakeWordClasses:
    echo
    java edu.stanford.nlp.mt.wordcls.MakeWordClasses -help
    exit -1
fi

MEM=$1
THREADS=$2
shift 2

JAVA_OPTS="-server -ea -Xmx${MEM} -Xms${MEM} -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:PermSize=256m -XX:MaxPermSize=256m"

java $JAVA_OPTS edu.stanford.nlp.mt.wordcls.MakeWordClasses -nthreads $THREADS $*
