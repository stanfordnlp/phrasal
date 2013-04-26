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

#TODO: de-compounding
cat $infile > $infile.decompound

# Truecasing
recase.sh German $infile.decompound > $infile.cased

# Detokenization
cat $infile.cased | en_detokenizer > $infile.postproc

rm -f $infile.{cased,decompound}
