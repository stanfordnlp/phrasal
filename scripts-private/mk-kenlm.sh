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

if [ $# -le 2 ]; then
    echo Usage: `basename $0` order name file_gz "[file_gz]"
		echo
		echo Output file: name.gz.kenlm
		echo
		echo You need to rename the output file to name.gz
    exit
fi

MAKELM=/u/nlp/packages/mosesdecoder/bin/lmplz
TEMPDIR=kenlm_tmp
ORDER=$1
NAME=$2

shift 2

mkdir -p $TEMPDIR
zcat $* | grep -v '<s>' | tr '[:upper:]' '[:lower:]' | $MAKELM -o $ORDER -S 90% -T $TEMPDIR | gzip -c > "$NAME".gz.kenlm
