#!/bin/sh

if [ "$PHRASAL" == "" ]; then
   echo "ERROR: Env variable PHRASAL is not set!"
   exit -1;
fi

if [ "$CORENLP" == "" ]; then
   echo "ERROR: Env variable CORENLP is not set!"
   exit -1;
fi

for jf in `ls $PHRASAL/*.jar $CORENLP/*.jar`; do
  export CLASSPATH=$jf:$CLASSPATH
done

