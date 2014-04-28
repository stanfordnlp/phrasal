#!/usr/bin/env bash
#
# Tune a baseline Moses system.
#
# Author: Spence Green

# IMPORTANT: Ensure that LD_LIBRARY_PATH points to your boost installation
#
#   LD_LIBRARY_PATH=/u/nlp/packages/boost_1_42_0/lib
#
# and that MOSES points to the Moses installation directory:
#
#   MOSES=/path/to/mosesdecoder
#

if [ $# -ne 4 ]; then
	echo Usage: `basename $0` corpus_dir src_lang tgt_lang lm_file
	exit 0
fi

CORPUSDIR=$1
SRC_LANG=$2
TGT_LANG=$3
LM_FILE=$4

# Defaults
HOME=`pwd`
CORES=4
GIZA=/home/rayder441/packages/giza-pp

# Phrase and feature extraction using Galley and Manning (2008)
# hierarchical re-ordering models.
mkdir -p $HOME/train/model
$MOSES/scripts/training/train-model.perl --max-phrase-length 7 \
-external-bin-dir $GIZA \
--first-step 3 --last-step 9 \
-root-dir $HOME/train -corpus $CORPUSDIR/corpus -f $SRC_LANG -e $TGT_LANG \
-giza-f2e $CORPUSDIR/giza.ar-en \
-giza-e2f $CORPUSDIR/giza.en-ar \
-alignment grow-diag \
-lm 0:3:$LM_FILE:0 \
-reordering hier-mslr-bidirectional-fe \
-cores $CORES



