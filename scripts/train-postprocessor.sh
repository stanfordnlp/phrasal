#!/usr/bin/env bash
#
# Train the CRF-based post-processor.
#
# Author: Spence Green
#

if [ $# -ne 4 ]; then
    echo Usage: $(basename $0) mem threads training_file test_file
    exit -1
fi

# TODO(spenceg) Change this to the desired language.
# See edu.stanford.nlp.mt.process
POSTPROC=edu.stanford.nlp.mt.process.en.EnglishPostprocessor

function prep_data {
    FILE=$1
    # Detect file encoding
    ext="${FILE##*.}"
    CAT=cat
    if [ $ext == "gz" ]; then 
	CAT=zcat
    elif [ $ext == "bz2" ]; then
	CAT=bzcat
    fi
    # Cleanup the input
    $CAT $FILE | sed -e 's/[[:cntrl:]]/ /g' | python $PHRASAL_HOME/scripts-private/cleanup_txt.py > ${FILE}.out
}    

# Process command-line
MEM=$1
THREADS=$2

TRAIN=$3
prep_data $TRAIN
TRAIN=${TRAIN}.out

TEST=$4
prep_data $TEST
TEST=${TEST}.out

java -server -ea -Xmx${MEM} -Xms${MEM} -XX:+UseParallelGC -XX:+UseParallelOldGC $POSTPROC -trainFile ${TRAIN} -serializeTo ${TRAIN}.ld.ser.gz -labelDictionaryCutoff 200 -useOWLQN true -priorLambda 0.1 -multiThreadGrad ${THREADS} -multiThreadClassifier ${THREADS} -testFile ${TEST}
