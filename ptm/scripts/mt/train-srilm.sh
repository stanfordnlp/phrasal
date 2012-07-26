#!/usr/bin/env bash
#
# Builds a large (5-gram) LM from a *pre-processed*, gzip'd
# text file.
# 
# Author: Spence Green
#
if [ $# -ne 2 ]; then
  echo Usage: `basename $0` textfile_gz lm_name
	exit -1
fi

textfile=$1
lm_name=$2

# Directories for logs and intermediate files
mkdir -p logs
mkdir -p counts

# Package directories
srilmdir=/u/nlp/packages/SRILM

# Put SRILM on the PATH
PATH="$srilmdir"/bin:"$srilmdir"/bin/i686-m64:"$PATH"

# This command-line fu is redacted from Michel's Makefile
# at $JAVANLP/projects/mt/makefiles/lm/train
#
# Also using configuration parameters (that work well for English)
# from Michel's NIST09 Ar-En systems

# These options specify thresholds below which we discard n-grams.
# Reducing the higher order terms will obviously make the LMs dramatically
# larger. These parameters are for a 5-gram model.
ORDER=5
LMFILTER="-gt2min 1 -gt3min 2 -gt4min 3 -gt5min 3"

# Step 1: extract (lower-cased) counts
COUNT=ngram-count
LOWER=-tolower
LMOPTS="-order $ORDER -kndiscount -interpolate -debug 2 $LOWER"

(time $COUNT $LMOPTS -text "$textfile" -write "$lm_name".counts.gz -sort) 2> logs/"$lm_name".counts.log

# TODO: We may need to use ngram-merge for super-massive lms built with
# the Gigaword corpora. See Michel's original Makefile

# Step 2: build LM
# See: http://www.speech.sri.com/projects/srilm/manpages/training-scripts.1.html
#
#  -read := list of counts files
#  -name := prefix for intermediate files
#  -lm   := name of new LM file
#
MAKE_LM=make-big-lm

(time $MAKE_LM -read "$lm_name".counts.gz -lm "$lm_name".gz $LMOPTS $LMFILTER -debug 2 -name counts/"$lm_name") 2> logs/"$lm_name".log


# Step 3: binarize
BINARIZE=ngram

(time $BINARIZE -order $ORDER -lm "$lm_name".gz -write-bin-lm "$lm_name".bin) 2> logs/"$lm_name".bin.log

gzip "$lm_name".bin

