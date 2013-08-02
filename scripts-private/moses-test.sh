#!/usr/bin/env bash
#
# Decode test sets with Moses.
#
# Assumes that the phrase table and re-ordering models for each test set
# are located in "newtestset.tables".
#
# Author: Spence Green
#

# IMPORTANT! TODO: Ensure that the following is set in your environment:
#
# LD_LIBRARY_PATH=/u/nlp/packages/boost_1_42_0/lib

if [ $# -lt 6 ]; then
    echo Usage: `basename $0` name ini-file tune-set-name ref-dir threads newtestset:filepath "[newtestset:filepath]"
    echo e.g. `basename $0` kbmira kbmira.tune/moses.ini mt06 refs 16 mt04:zhdata/mt04.seg.zh
    exit -1
fi

name=$1
ini_file=$2
tune_set_name=$3
ref_dir=$4
n_threads=$5
shift 5

MOSES=/u/nlp/packages/mosesdecoder
DECODE=$MOSES/bin/moses

# Iterate over test sets
for tuple in $@; do
    echo Decoding and evaluating: $tuple
    testset=${tuple%:*}
    test_file=${tuple#*:}
   	cat $ini_file | perl -ne \
			"s/$tune_set_name\.tune\/model/$testset\.tables/g; print" \
			> $testset.$tune_set_name.$name.ini
    cat $test_file | $DECODE -f $testset.$tune_set_name.$name.ini \
	-threads $n_threads > $testset.$tune_set_name.$name.trans
    cat $testset.$tune_set_name.$name.trans | \
	bleu $ref_dir/$testset/ref* > $testset.$tune_set_name.$name.bleu 
done
