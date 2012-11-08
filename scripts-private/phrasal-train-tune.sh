#!/usr/bin/env bash
#
# Train and tune Phrasal. Steps are configurable with the variables
# at the top of this script.
# 
# Steps:
#   1  Phrase extraction (tuning filtering)
#   2  Pre-process tuning set for OOVs
#   3  Run tuning
#   4  Phrase extraction (decoding filtering)
#   5  Pre-process decoding set for OOVs
#   6  Decode
#   7  Output results file
#
# 
# Author: Spence Green
#
if [ $# -ne 4 ]; then
    echo Usage: `basename $0` var_file start-step ini_file sys_name
    exit 0
fi
VAR_FILE=$1
START_STEP=$2
INI_FILE=$3
SYS_NAME=$4

echo ===========================
echo Phrasal training and tuning
echo ===========================
echo System name: $SYS_NAME
echo Start step: $START_STEP
echo Loading local parameters from $VAR_FILE
source $VAR_FILE

######################################################################
######################################################################

#
# Extract a filtered phrase table.
#
function extract {
    FILTSET=$1
    PTABLEDIR=$2
    mkdir -p $PTABLEDIR
    java $JAVA_OPTS $EXTRACTOR_OPTS edu.stanford.nlp.mt.train.PhraseExtract -threads $THREADS_EXTRACT -extractors $EXTRACTORS $SPLIT -fCorpus $CORPUS_SRC -eCorpus $CORPUS_TGT -align $CORPUS_ALIGN -fFilterCorpus $FILTSET -maxELen $MAX_PHRASE_LEN -endAtLine $LINES $LO_ARGS -phiFilter $MIN_PHRASE_SCORE 2> "$PTABLEDIR"/merged.gz.log | gzip -c > "$PTABLEDIR"/merged.gz

    # Split the phrase table into rule scores and lex re-ordering
    # scores
    zcat "$PTABLEDIR"/merged.gz | "$SCRIPTDIR"/filter_po_tables "$PTABLEDIR"/phrase-table.gz "$PTABLEDIR"/lo-hier.msd2-bidirectional-fe.gz -99999999 8 >& "$PTABLEDIR"/phrase-table.gz.log
}

#
# Prepare a file for decoding by removing OOVs. Also extract a vocabulary.
#
function prep_source {
    INFILE=$1
    OUTFILE=$2
    PTABLEDIR=$3
    NAME=$4
    rm -f $OUTFILE
    perl "$SCRIPTDIR"/remove_unk_before_decode $MAX_PHRASE_LEN "$PTABLEDIR"/phrase-table.gz "$INFILE" "$OUTFILE".tmp >& "$OUTFILE".err 
    cat "$OUTFILE".tmp | sed 's/^ *$/null/' > "$OUTFILE"
    rm -f "$OUTFILE".tmp
    cat "$OUTFILE" | ngram-count -text - -write-vocab "$NAME".f.vocab -order 1 -unk
}

#
# Tune
#
function tune {
    "$SCRIPTDIR"/update_ini SETID $TUNE_SET_NAME < $INI_FILE > $TUNE_INI_FILE
    phrasal-mert.pl \
	--opt-flags="$OPT_FLAGS" \
	--working-dir="$TUNEDIR" \
	--phrasal-flags="" \
	--java-flags="$JAVA_OPTS $DECODER_OPTS" \
	--mert-java-flags="$JAVA_OPTS $MERT_OPTS" \
	--nbest=$NBEST $TUNE_FILE "$REFDIR"/"$TUNE_SET_NAME"/ref \
	$OBJECTIVE $TUNE_INI_FILE \
	>& logs/"$TUNEDIR".log
}

#
# Decode text
#
function decode {
    rm $TUNEDIR/phrasal.best.ini || true
    eval `$SCRIPTDIR/link-best-ini $TUNEDIR`
    "$SCRIPTDIR"/update_ini \
	-f "$RUNNAME"."$NBEST"best \
	-n $NBEST $TUNE_SET_NAME $DECODE_SET_NAME \
	< "$TUNEDIR"/phrasal.best.ini > "$RUNNAME".ini
    java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.Phrasal \
	-config-file "$RUNNAME".ini -moses-n-best-list true \
	< $DECODE_FILE > "$RUNNAME".out 2> logs/"$RUNNAME".log
}

#
# Evaluate the target output
#
function evaluate {
    if [ $NBEST -gt 1 ]; then
	cat "$RUNNAME"."$NBEST"best \
	    | "$SCRIPTDIR"/nbest_sort \
	    | "$SCRIPTDIR"/nbest2uniq \
	    > "$RUNNAME"."$NBEST"best.uniq 2> /dev/null
    fi
    cat "$RUNNAME".out | "$SCRIPTDIR"/phrasal_sort > "$RUNNAME".out.1best
    cat "$RUNNAME".out.1best | "$SCRIPTDIR"/bleu "$REFDIR"/"$DECODE_SET_NAME"/ref* > "$RUNNAME".out.bleu
}


######################################################################
######################################################################

# Synthetic parameters and commands
mkdir -p logs
TUNE_PTABLE_DIR="$TUNE_SET_NAME".tables
TUNE_FILE="$TUNE_SET_NAME".prep
TUNE_INI_FILE="$TUNE_SET_NAME"."$SYS_NAME".ini
TUNEDIR="$TUNE_SET_NAME"."$SYS_NAME"."$NBEST".tune
DECODE_PTABLE_DIR="$DECODE_SET_NAME".tables
DECODE_FILE="$TUNE_SET_NAME".prep
RUNNAME="$DECODE_SET_NAME"."$TUNE_SET_NAME"."$SYS_NAME"

if [ $START_STEP -le 1 ]; then
    echo "### Running Step 1 ###"
    extract $TUNE_SET $TUNE_PTABLE_DIR
fi
if [ $START_STEP -le 2 ]; then
    echo "### Running Step 2 ###"
    prep_source $TUNE_SET $TUNE_FILE $TUNE_PTABLE_DIR $TUNE_SET_NAME
fi
if [ $START_STEP -le 3 ]; then
    echo "### Running Step 3 ###"
    tune
fi
if [ $START_STEP -le 4 ]; then
    echo "### Running Step 4 ###"
    extract $DECODE_SET $DECODE_PTABLE_DIR
fi
if [ $START_STEP -le 5 ]; then
    echo "### Running Step 5 ###"
    prep_source $DECODE_SET $DECODE_FILE $DECODE_PTABLE_DIR $DECODE_SET_NAME
fi
if [ $START_STEP -le 6 ]; then
    echo "### Running Step 6 ###"
    decode
fi
if [ $START_STEP -le 7 ]; then
    echo "### Running Step 7 ###"
    evaluate
fi
