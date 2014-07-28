#!/usr/bin/env bash
#
# Run the Phrasal web service. Use the "-help"
# option to see the options for the service itself.
#
# Author: Spence Green
#
if [ $# -le 1 ]; then
    echo Usage: `basename $0` mem options
    exit -1
fi

MEM=$1
shift

DECODER_OPTS="-Djava.library.path=${JAVANLP_HOME}/projects/more/src-cc"
JAVA_OPTS="-server -ea -Xmx${MEM} -Xms${MEM} -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:PermSize=256m -XX:MaxPermSize=256m"

java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.service.PhrasalService $*
