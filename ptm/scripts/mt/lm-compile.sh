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

source lm.local

(time $MAKE_LM -read $* -lm "$lm_name".gz $LMOPTS $LMFILTER -debug 2 -name counts/"$lm_name") 2> logs/"$lm_name".log

(time $BINARIZE -order $ORDER -lm "$lm_name".gz -write-bin-lm "$lm_name".bin) 2> logs/"$lm_name".bin.log

gzip "$lm_name".bin

