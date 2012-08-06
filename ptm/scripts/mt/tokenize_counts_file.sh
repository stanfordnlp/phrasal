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
SPLIT_SIZE=10000000

if [ $# -ne 2 ]; then
    echo Usage: `basename $0` lang counts_file
    exit -1
fi

# Uncomment the line below to run on the cluster
MEM=1g
EXEC=
#EXEC="nlpsub -m${MEM}"

lang=$1
counts_file=$2
outfile=`basename $counts_file`
outfile_pref="${outfile%.*}"

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
    $CAT $counts_file | cut -f1 - | split -d -l $SPLIT_SIZE - "$outfile_pref".
else
    $CAT $counts_file | cut -f1 > "$outfile_pref".00
fi

for txtfile in `ls ${outfile_pref}.*`
do
    $EXEC $TOK $lang $txtfile noclean
done
