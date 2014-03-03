#!/usr/bin/env bash
#
# Run the LM-based recaser.
#
# Author: Spence Green
#

if [ $# -ne 2 ]; then
	echo Usage: $(basename $0) lm_file text_file
	exit -1
fi

lm_file=$1
infile=$2

HOST=`hostname -s`
JVM_OPTS="-server -ea -Xmx2g -Xms2g -XX:+UseParallelGC -XX:+UseParallelOldGC"
JNI_OPTS="-Djava.library.path=/scr/nlp/data/gale3/KENLM-JNI/${HOST}"

java $JVM_OPTS $JNI_OPTS edu.stanford.nlp.mt.tools.LanguageModelTrueCaser $model < $infile 
