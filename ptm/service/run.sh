#!/usr/bin/env bash

MEM=9g
JAVA_OPTS="-server -ea -Xmx${MEM} -Xms${MEM} -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:PermSize=256m -XX:MaxPermSize=256m"
DECODER_OPTS="-Djava.library.path=/home/rayder441/sandbox/javanlp/projects/mt/src-cc"

#CP=/home/rayder441/sandbox/javanlp/projects/core/classes:/home/rayder441/sandbox/javanlp/projects/mt/classes:/home/rayder441/sandbox/javanlp/projects/mt/lib/*:/home/rayder441/sandbox/javanlp/projects/more/classes:/home/rayder441/sandbox/javanlp/projects/mt/lib-research/*:

#CP="$CLASSPATH":/home/rayder441/sandbox/javanlp/projects/core/lib/tomcat/*

#echo "$CP"

# Add the static content for debugging
#CP="$CP":/home/rayder441/sandbox/javanlp/projects/mt/src-research/

java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.tools.service.PhrasalService $*
