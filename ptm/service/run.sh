#!/usr/bin/env bash

MEM=9g
JAVA_OPTS="-server -ea -Xmx${MEM} -Xms${MEM} -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:PermSize=256m -XX:MaxPermSize=256m"
DECODER_OPTS="-Djava.library.path=/home/rayder441/sandbox/javanlp/projects/mt/src-cc"

java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.tools.service.PhrasalService $*
