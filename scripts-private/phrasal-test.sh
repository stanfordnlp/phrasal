#!/usr/bin/env bash
#
# Convenience script (around phrasal.sh) for decoding multiple
# test sets given a weight vector.
#
# Author: Sida Wang, Spence Green
#
if [ $# -lt 5 ]; then
    echo "Usage: `basename $0` vars_file ini_file name iteration \
newtestset:filepath [newtestset:filepath]"
    echo "e.g. `basename $0` phrasal.vars phrasal.ini l1-001-cluster 5 \
mt04:zhdata/mt04.seg.zh"
    exit -1
fi

#
# Take the results of a tuning run and decode a test set
#
function decodetest {
    echo Decoding on $DECODE_SET_NAME tuned on $TUNE_SET_NAME runname $SYS_NAME
    if [ -L $TUNE_SET_NAME.$SYS_NAME.online.final.binwts ]; then
	rm $TUNE_SET_NAME.$SYS_NAME.online.final.binwts
    fi
    if [ -e $TUNE_SET_NAME.$SYS_NAME.online.final.binwts ]; then
	mv $TUNE_SET_NAME.$SYS_NAME.online.final.binwts \
        $TUNE_SET_NAME.$SYS_NAME.online.final.binwts.bak
    fi

    # Symlink the appropriate iteration
    ln -s  $TUNE_SET_NAME.$SYS_NAME.online.$ITERATION.binwts \
        $TUNE_SET_NAME.$SYS_NAME.online.final.binwts
    
    DECODE_VAR_FILE=$DECODE_SET_NAME.$TUNE_SET_NAME.$SYS_NAME.vars
    if [ -e $DECODE_SET_NAME.$TUNE_SET_NAME.$SYS_NAME.vars ]; then
        echo "var file already exists, using the existing one"
    else
        echo "var file does not exist, making new one"
    cat $VAR_FILE | grep -v "DECODE_SET" > $DECODE_VAR_FILE
    echo "DECODE_SET=$DECODE_SET" >> $DECODE_VAR_FILE
    echo "DECODE_SET_NAME=$DECODE_SET_NAME" >> $DECODE_VAR_FILE
    fi

    phrasal.sh $DECODE_VAR_FILE 4-5 $INI_FILE $SYS_NAME
    mv $DECODE_SET_NAME.$TUNE_SET_NAME.$SYS_NAME.bleu \
        $DECODE_SET_NAME.$TUNE_SET_NAME.$SYS_NAME.$ITERATION.bleu
}

VAR_FILE=$1
INI_FILE=$2
SYS_NAME=$3
ITERATION=$4
shift 4

source $VAR_FILE

# Iterate over test sets
for tuple in $@; do
    echo $tuple
    DECODE_SET_NAME=${tuple%:*}
    DECODE_SET=${tuple#*:}
    decodetest
done
