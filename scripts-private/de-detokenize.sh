#!/usr/bin/env bash
#
# Apply de-tokenization followed by re-casing.
#
# Author: Spence Green
#

if [ $# -ne 1 ]; then
    echo Usage: $(basename $0) file
    exit -1
fi

infile=$1

cat $infile | de-rules.py | en_detokenizer > $infile.detok
recase.sh German $infile.detok > $infile.cased
