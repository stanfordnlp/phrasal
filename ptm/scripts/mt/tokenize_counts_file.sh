#!/usr/bin/env bash
#
# Run a tokenizer over an srilm counts file. Common use case:
# tokenizing Google n-grams counts files.
#
# After tokenization, filter down to the desired model order
# using filter_counts_file.py
#
# NOTE: This script uses nlpsub. You must merge the outputs
# of the splits into the final counts file.
#

# Maximum number of lines per split of the input file
SPLIT_SIZE=1000000

if [ $# -ne 2 ]; then
    echo Usage: `basename $0` lang counts_file
    exit -1
fi

lang=$1
counts_file=$2

SCRIPT_DIR=${JAVANLP_HOME}/projects/mt/ptm/scripts/mt
TOK=${SCRIPT_DIR}/tok_stanford.sh

# Detect file encoding
ext="${counts_file##*.}"
CAT=cat
if [ $ext == "gz" ]; then 
    CAT=zcat
elif [ $ext == "bz2" ]; then
    CAT=bzcat
fi

# Split the input file into smaller chunks
file_sz=`$CAT $counts_file | wc -l`
if [ $file_sz -gt $SPLIT_SIZE ]; then
    $CAT $counts_file | cut -f1 - | split -d -l $SPLIT_SIZE - split.cnt.
else
    $CAT $counts_file | cut -f1 > split.cnt.1
fi

for txtfile in `ls split.cnt.*`
do
    echo nlpsub -m1g $TOK $lang $txtfile
done