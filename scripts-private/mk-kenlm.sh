#!/usr/bin/env bash
#
# Estimate a language model using KenLM. Great for research purposes
# since there are no funny hyperparameters to tweak.
#
# You should run this on a local disk with sufficient space since
# the lm compiler uses a disk-based merge sort of the counts.
#
# Author: Spence Green
#

if [ $# -le 3 ]; then
    echo Usage: `basename $0` order name type file_gz "[file_gz]"
    echo
    echo "type: [probing|trie]"
    echo 
    echo Output file: name.arpa
    echo
    echo You need to rename the output file to name.gz
    exit
fi

KENLM_BIN=$JAVANLP_HOME/projects/mt/src-cc/kenlm/bin
MAKELM=${KENLM_BIN}/lmplz
MAKEBIN=${KENLM_BIN}/build_binary
TEMPDIR=kenlm_tmp
ORDER=$1
NAME=$2
TYPE=$3

shift 3

mkdir -p $TEMPDIR

echo "Building ARPA LM..."
zcat $* | perl -ne 's/<s>//g; print' | $MAKELM --interpolate_unigrams -o $ORDER -S 80% -T $TEMPDIR > "$NAME".arpa

echo "Binarizing ARPA LM with standard settings"
$MAKEBIN $TYPE "$NAME".arpa "$NAME".bin
