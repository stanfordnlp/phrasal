#!/usr/bin/env bash
#
# Decode test sets with Moses.
#
# Author: Spence Green
#

# IMPORTANT! TODO: Ensure that the following is set in your environment:
#
# LD_LIBRARY_PATH=/u/nlp/packages/boost_1_42_0/lib

if [ $# -lt 3 ]; then
    echo Usage: `basename $0` name ini-file tune-set-name ref-dir newtestset:filepath "[newtestset:filepath]"
    echo e.g. `basename $0` kbmira kbmira.tune/moses.ini mt06 refs mt04:zhdata/mt04.seg.zh
    exit -1
fi

name=$1
ini_file=$2
tune_set_name=$3
ref_dir=$4
shift 4

MOSES=/scr/spenceg/moses1_0
DECODE=$MOSES/bin/moses

# Iterate over test sets
for tuple in $@; do
    echo Decoding and evaluating: $tuple
    testset=${tuple%:*}
    test_file=${tuple#*:}
    cat $ini_file | perl -ne 's/$tune_set_name/$testset/g; print' > $testset.$tune_set_name.ini
    cat $test_file | $DECODE -f $testset.$tune_set_name.ini > $testset.$tune_set_name.$name.trans
    cat $testset.$tune_set_name.$name.trans | bleu $ref_dir/$testset/ref* > $testset.$tune_set_name.$name.bleu 
done
