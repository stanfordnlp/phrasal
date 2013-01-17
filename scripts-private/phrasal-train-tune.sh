#!/usr/bin/env bash
#
# Train and tune Phrasal. Steps are configurable with the variables
# at the top of this script.
# 
# Author: Spence Green
#
if [ $# -ne 4 ]; then
    echo Usage: `basename $0` var_file steps ini_file sys_name
    echo
    echo "Use dashes and commas in the steps specification e.g. 1-3,6,7"
    echo
    echo Step definitions:
    echo "  1  Extract phrases from dev set"
    echo "  2  Pre-process dev set for OOVs"
    echo "  3  Run tuning"
    echo "  4  Extract phrases from test set"
    echo "  5  Pre-process dev set for OOVs"
    echo "  6  Decode test set"
    echo "  7  Output results file"
    exit 0
fi
VAR_FILE=$1
START_STEP=$2
INI_FILE=$3
SYS_NAME=$4

# Process steps
let s=0
IFS=',' read -ra ADDR <<< "$START_STEP"
for i in "${ADDR[@]}"; do
    IFS='-' read -ra SEQ <<< "$i"
    if [ ${#SEQ[@]} -eq 1 ]; then
	STEPS[s]=${SEQ[0]}
	let s=s+1
    else
	for j in `seq ${SEQ[0]} ${SEQ[1]}`;
	do
	    STEPS[s]=$j
	    let s=s+1
	done
    fi
done

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
    zcat "$PTABLEDIR"/merged.gz | filter_po_tables "$PTABLEDIR"/phrase-table.gz "$PTABLEDIR"/lo-hier.msd2-bidirectional-fe.gz -99999999 8 >& "$PTABLEDIR"/phrase-table.gz.log
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
    remove_unk_before_decode $MAX_PHRASE_LEN "$PTABLEDIR"/phrase-table.gz \
	"$INFILE" "$OUTFILE".tmp >& "$OUTFILE".err 
    cat "$OUTFILE".tmp | sed 's/^ *$/null/' > "$OUTFILE"
    rm -f "$OUTFILE".tmp
    cat "$OUTFILE" | ngram-count -text - -write-vocab "$NAME".f.vocab -order 1 -unk
}

#
# Batch tuning (e.g., MERT, PRO)
#
function tune-batch {
    # Setup the input ini file and run batch tuning
    update_ini SETID $TUNE_SET_NAME < $INI_FILE > $TUNE_INI_FILE
    
    phrasal-mert.pl \
	--opt-flags="$OPT_FLAGS" \
	--working-dir="$TUNEDIR" \
	--phrasal-flags="" \
	--java-flags="$JAVA_OPTS $DECODER_OPTS" \
	--mert-java-flags="$JAVA_OPTS $MERT_OPTS" \
	--nbest=$NBEST $TUNE_FILE $TUNE_REF \
	$OBJECTIVE $TUNE_INI_FILE \
	>& logs/"$TUNEDIR".log
}

#
# Create the Phrasal ini file from a batch tuning run
#
function make-ini-from-batch-run {
    # Setup the final weights and ini files
    rm $TUNEDIR/phrasal.best.ini || true
    eval `link-best-ini $TUNEDIR`
    if [ \( -n $NBEST \) -a \( $NBEST -gt 1 \) ]; then
	update_ini \
	    -f "$RUNNAME"."$NBEST"best \
	    -n $NBEST $TUNE_SET_NAME $DECODE_SET_NAME \
	    < "$TUNEDIR"/phrasal.best.ini > "$RUNNAME".ini
    else
	update_ini $TUNE_SET_NAME $DECODE_SET_NAME \
	    < "$TUNEDIR"/phrasal.best.ini > "$RUNNAME".ini
    fi
}

#
# Online tuning (e.g., Mira, PRO+SGD)
#
function tune-online {
    # Setup the ini file and run online tuning
    update_ini -n $TUNE_NBEST SETID $TUNE_SET_NAME \
	< $INI_FILE > $TUNE_INI_FILE    

    java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.tune.OnlineTuner \
	$TUNE_FILE $TUNE_REF \
	$TUNE_INI_FILE \
	$INITIAL_WTS \
	-n $TUNERUNNAME \
	$ONLINE_OPTS
}

#
# Create the Phrasal ini file from an online tuning run
#
function make-ini-from-online-run {
    # Setup the final weights and ini files
    FINAL_WTS="$TUNERUNNAME".online.final.binwts
    if [ \( -n $NBEST \) -a \( $NBEST -gt 1 \) ]; then
	update_ini \
	    -w $FINAL_WTS \
	    -f "$RUNNAME"."$NBEST"best \
	    -n $NBEST $TUNE_SET_NAME $DECODE_SET_NAME \
	    < $TUNE_INI_FILE > "$RUNNAME".ini
    else
	update_ini -w $FINAL_WTS $TUNE_SET_NAME $DECODE_SET_NAME \
	    < $TUNE_INI_FILE > "$RUNNAME".ini
    fi
}

#
# Decode an input file given an ini file from a tuning run
#
function decode {
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
	    | nbest_sort \
	    | nbest2uniq \
	    > "$RUNNAME"."$NBEST"best.uniq 2> /dev/null
    fi
    cat "$RUNNAME".out | bleu "$REFDIR"/"$DECODE_SET_NAME"/ref* > "$RUNNAME".bleu

    # TODO Output Tune BLEU

    # TODO Convert eval data to Json

    # TODO Copy over the html from JavaNLP (if necesary)
}


######################################################################
######################################################################

function step-status {
    echo "### Running Step $1"
}

function bookmark {
    cp $VAR_FILE "$RUNNAME".vars
    WDIR=`pwd`
    cd $JAVANLP_HOME
    git log | head -n 1 | awk '{ print $2 }' > $WDIR/"$RUNNAME".version
    cd $WDIR
}

# Synthetic parameters and commands
mkdir -p logs
TUNE_PTABLE_DIR="$TUNE_SET_NAME".tables
TUNE_FILE="$TUNE_SET_NAME".prep
TUNERUNNAME="$TUNE_SET_NAME"."$SYS_NAME"
TUNEDIR="$TUNERUNNAME".tune
TUNE_INI_FILE="$TUNERUNNAME".ini

DECODE_PTABLE_DIR="$DECODE_SET_NAME".tables
DECODE_FILE="$DECODE_SET_NAME".prep

RUNNAME="$DECODE_SET_NAME"."$TUNERUNNAME"

# Log some info about this run
bookmark

for step in ${STEPS[@]};
do
    if [ $step -eq 1 ]; then
	step-status $step
	extract $TUNE_SET $TUNE_PTABLE_DIR
    fi
    if [ $step -eq 2 ]; then
	step-status $step
	prep_source $TUNE_SET $TUNE_FILE $TUNE_PTABLE_DIR $TUNE_SET_NAME
    fi
    if [ $step -eq 3 ]; then
	step-status $step
	if [ $TUNE_MODE == "batch" ]; then
	    tune-batch
	else
	    tune-online
	fi
    fi
    if [ $step -eq 4 ]; then
	step-status $step
	extract $DECODE_SET $DECODE_PTABLE_DIR
    fi
    if [ $step -eq 5 ]; then
	step-status $step
	prep_source $DECODE_SET $DECODE_FILE $DECODE_PTABLE_DIR $DECODE_SET_NAME
    fi
    if [ $step -eq 6 ]; then
	step-status $step
	if [ $TUNE_MODE == "batch" ]; then
	    make-ini-from-batch-run
	else
	    make-ini-from-online-run
	fi
	decode
    fi
    if [ $step -eq 7 ]; then
	step-status $step
	evaluate
    fi
done
