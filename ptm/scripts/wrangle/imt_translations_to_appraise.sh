#!/usr/bin/env bash

if [ $# -ne 3 ]; then
    echo Usage: $(basename $0) src_lang tgt_lang name
    exit -1
fi

SRC=$1
TGT=$2
NAME=$3

ROOT=appraise
mkdir -p $ROOT
mkdir -p $ROOT/sources
mkdir -p $ROOT/references
mkdir -p $ROOT/systems

# Copy translations
for file in $(ls *.trans); do
    ID="${file%.*}"
    paste $file $ID.props | perl -pe 's/.*IsValid=false.*/\t/g;' | cut -f1 > $ROOT/systems/$NAME.$SRC-$TGT.$ID
done

# Copy the reference
cp ref0 $ROOT/references/$NAME-ref.$TGT

# Copy the source
cp src0 $ROOT/sources/$NAME-src.$SRC

