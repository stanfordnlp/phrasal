#!/usr/bin/env bash
#
# Moses training and tuning on the Stanford NLP cluster.sh
#
# Author: Spence Green

# IMPORTANT! TODO: Ensure that the following is set in your environment:
#
# LD_LIBRARY_PATH=/u/nlp/packages/boost_1_42_0/lib

# Paths to stuff
HOME=`pwd`
MOSES=/u/nlp/packages/mosesdecoder
GIZA=/u/nlp/packages/GIZA++

# TODO: Change these for your language
CORPUSDIR="$HOME"/training/corpus
LM=/scr/nlp/data/gale/NIST09/lm/releases/mt_giga3_afp_xin.1234.unk.lm.gz
TUNE_SET="$HOME"/mt06.unk
REF_PREFIX="$HOME"/refs/mt06/ref

# Phrase and feature extraction using Galley and Manning (2008)
# hierarchical re-ordering models.
mkdir -p $HOME/train/model
$MOSES/scripts/training/train-model.perl --max-phrase-length 7 \
--external-bin-dir $GIZA --first-step 4 --last-step 9 \
-root-dir $HOME/train -corpus $CORPUSDIR -f ar -e en \
-alignment-file $HOME/training/corpus -alignment grow-diag \
-lm 0:3:"$LM":0 \
-reordering hier-mslr-bidirectional-fe

# Model tuning with MIRA
# For MERT: remove the "--batch-mira" parameter
# For PRO: replace mira with "--pairwise-ranked"
mkdir -p $HOME/tune
$MOSES/scripts/training/mert-moses.pl \
--working-dir $HOME/tune \
--decoder-flags="-distortion-limit 5" --mertdir $MOSES/bin/ --batch-mira \
--threads=8 \
$TUNE_SET $REF_PREFIX \
$MOSES/bin/moses $HOME/train/model/moses.ini
