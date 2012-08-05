#!/usr/bin/env bash
#
# Step 2 of LM training.
#
# Merges an input list of count files, builds the LM, and binarizes it.
#
# Builds an LM from a *pre-processed*, gzip'd
# text file.
#
# NOTE: This script does not use nlpsub. It executes two sequential,
# single-threaded jobs. Run this script on a high-memory machine.
#
# Author: Spence Green
#
if [ $# -lt 2 ]; then
  echo "Usage: `basename $0` lm_name counts_file [counts_files]"
  exit -1
fi

lm_name=$1
shift

# Directories for logs and intermediate files
mkdir -p logs

# Configure the LM locally
source lm.local

# Step 2: build LM and binarize
# See: http://www.speech.sri.com/projects/srilm/manpages/training-scripts.1.html
#
#  -read := list of counts files
#  -name := prefix for intermediate files
#  -lm   := name of new LM file
#
MAKE_LM=make-big-lm
BINARIZE=ngram

counts_cmd=
for txtfile in $*
do
    counts_cmd="-read ${txtfile} ${counts_cmd}"
done

echo Counts: ${counts_cmd}

# Closed vocabulary
(time $MAKE_LM ${counts_cmd} $LMOPTS -name "$lm_name".gz) 2> logs/"$lm_name".log

(time $BINARIZE -order $ORDER -lm "$lm_name".gz -write-bin-lm "$lm_name".bin) 2> logs/"$lm_name".bin.log

gzip "$lm_name".bin

# Open vocabulary
(time $MAKE_LM ${counts_cmd} $LMOPTS -unk -name "$lm_name".unk.gz) 2> logs/"$lm_name".unk.log

(time $BINARIZE -order $ORDER -lm "$lm_name".unk.gz -write-bin-lm "$lm_name".unk.bin) 2> logs/"$lm_name".unk.bin.log

gzip "$lm_name".unk.bin
