#!/usr/bin/env bash

# Local usage
#MEM=9g
#DECODER_OPTS="-Djava.library.path=/home/rayder441/sandbox/javanlp/projects/mt/src-cc"

# NLP cluster
MEM=80g
DECODER_OPTS="-Djava.library.path=/scr/nlp/data/gale3/KENLM-JNI/${HOST}"

JAVA_OPTS="-server -ea -Xmx${MEM} -Xms${MEM} -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:PermSize=256m -XX:MaxPermSize=256m"

java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.service.PhrasalService $*
