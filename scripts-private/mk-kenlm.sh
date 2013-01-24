#!/usr/bin/env bash
#
# Estimate a language model using KenLM. Great for research purposes
# since there are no funny hyperparameters to tweak.
#
# Author: Spence Green
#

if [ $# -le 2 ]; then
    echo Usage: `basename $0` order name file_gz "[file_gz] > name.gz.kenlm"
    exit
fi

MAKELM=/u/nlp/packages/mosesdecoder/bin/lmplz
ORDER=$1
NAME=$2

shift 2

zcat $* | $MAKELM -o $ORDER -S 90% -T kenlm_tmp | gzip -c > "$NAME".gz.kenlm