#!/usr/bin/env bash
#
# Re-case French and German text. The procedures for converting MT
# output to "readable" text differs for the two languages.
#
# German: 1) detokenize, 2) re-case
#
# French: 1) re-case, 2) detokenize
#
# Author: Spence Green
#

if [ $# -ne 2 ]; then
	echo Usage: $(basename $0) "[French|German]" file
	exit -1
fi

lang=$1
infile=$2

model_path=/scr/nlp/data/WMT/recasers

if [ $lang == "French" ]; then
	model=kenlm:${model_path}/french.hmm.recaser.bin
else
	model=kenlm:${model_path}/german.hmm.recaser.bin
fi

JVM_OPTS="-server -Xmx2g -Xms2g -XX:+UseParallelGC"
JNI_OPTS="-Djava.library.path=/scr/nlp/data/gale3/KENLM-JNI/${HOST}:/scr/nlp/data/gale3/SRILM-JNI/${HOST}"

java $JVM_OPTS $JNI_OPTS edu.stanford.nlp.mt.tools.LanguageModelTrueCaser $model < $infile 2>/dev/null

