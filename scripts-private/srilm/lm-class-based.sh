#!/usr/bin/env bash
#
# Generates a class-based language model
# in ARPA format with WB-smoothing.
#
# Author: Spence Green
#
if [ $# -ne 2 ]; then
    echo "Usage: `basename $0` file lm_name"
    exit -1
fi

INFILE=$1
LM_NAME=$2

ngram-count -text $INFILE -lm $LM_NAME -order 7 -interpolate
-unk -wbdiscount
