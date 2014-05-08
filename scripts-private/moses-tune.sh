#!/usr/bin/env bash
#
# Tune a Moses model
#

if [ $# -ne 4 ]; then
    echo Usage: `basename $0` tune_set tune_dir ini_file ref_prefix
    exit 0
fi

TUNE_SET=$1
TUNEDIR=$2
INI_FILE=$3
REF_PREFIX=$4

HOME=`pwd`
CORES=4
NBEST_SIZE=200

# Batch mira Model tuning with MIRA
# For MERT: remove the "--batch-mira" parameter
# For PRO: replace mira with "--pairwise-ranked"
# To continue a tuning run: add "--continue"
rm -rf $HOME/$TUNEDIR
mkdir -p $HOME/$TUNEDIR
$MOSES/scripts/training/mert-moses.pl \
--working-dir $HOME/$TUNEDIR \
--decoder-flags="-threads $CORES" \
--mertdir $MOSES/bin/ \
--no-filter-phrase-table \
--nbest=$NBEST_SIZE \
--batch-mira \
--return-best-dev \
$TUNE_SET $REF_PREFIX \
$MOSES/bin/moses $INI_FILE