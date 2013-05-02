#!/usr/bin/env bash
#
# Apply re-casing followed by detokenization.
#
# Author: Spence Green
#

if [ $# -ne 1 ]; then
    echo Usage: $(basename $0) file
    exit -1
fi

infile=$1

DETOK="java edu.stanford.nlp.mt.process.fr.Detokenizer"

recase.sh French $infile > $INFILE.cased
$DETOK < $INFILE.cased > $infile.postproc

