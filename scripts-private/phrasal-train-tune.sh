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
    echo "  5  Pre-process test set for OOVs"
    echo "  6  Decode test set"
    echo "  7  Output results file"
    echo "  8  Generate a learning curve from an online run"
    exit 0
fi
VAR_FILE=$1
EXEC_STEPS=$2
INI_FILE=$3
SYS_NAME=$4

# Process steps
let s=0
IFS=',' read -ra ADDR <<< "$EXEC_STEPS"
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
echo Loading local parameters from $VAR_FILE
source $VAR_FILE
echo System name: $SYS_NAME
echo Executing steps: $EXEC_STEPS
echo JavaNLP: $JAVANLP_HOME

######################################################################
######################################################################

#
# Extract a filtered phrase table.
#
function extract {
    FILTSET=$1
    PTABLEDIR=$2
    mkdir -p $PTABLEDIR
    java $JAVA_OPTS $EXTRACTOR_OPTS edu.stanford.nlp.mt.train.PhraseExtract -threads $THREADS_EXTRACT -extractors $EXTRACTORS $EXTRACT_SET -fFilterCorpus $FILTSET $LO_ARGS $OTHER_EXTRACT_OPTS -outputDir $PTABLEDIR 2> ${PTABLEDIR}/extract.gz.log
}

#
# Prepare a file for decoding by removing OOVs. Also extract a vocabulary.
#
function prep-source {
    INFILE=$1
    OUTFILE=$2
    PTABLEDIR=$3
    NAME=$4
    rm -f $OUTFILE
    remove_unk_before_decode $MAX_PHRASE_LEN "$PTABLEDIR"/phrase-table.gz \
	"$INFILE" "$OUTFILE".tmp >& "$OUTFILE".err 
    cat "$OUTFILE".tmp | sed 's/^ *$/null/' > "$OUTFILE"
    rm -f "$OUTFILE".tmp
}

function tune-setup {
    # Check to see if the user pre-processed the tuning file
    if [ ! -e $TUNE_FILE ]; then
	ln -s $TUNE_SET $TUNE_FILE
    fi
    
    # Setup the ini file and run online tuning
    update_ini -n $TUNE_NBEST SETID $TUNE_SET_NAME \
	< $INI_FILE > $TUNE_INI_FILE   
}

#
# Batch tuning (e.g., MERT, PRO)
#
function tune-batch {
    tune-setup    

    phrasal-mert.pl \
	--opt-flags="$OPT_FLAGS" \
	--working-dir="$TUNEDIR" \
	--phrasal-flags="" \
	--java-flags="$JAVA_OPTS $DECODER_OPTS" \
	--mert-java-flags="$JAVA_OPTS $MERT_OPTS" \
	--nbest=$TUNE_NBEST $TUNE_FILE $TUNE_REF \
	$OBJECTIVE $TUNE_INI_FILE \
	>& logs/"$TUNERUNNAME".mert.log
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
    tune-setup
    
    java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.tune.OnlineTuner \
	$TUNE_FILE $TUNE_REF \
	$TUNE_INI_FILE \
	$INITIAL_WTS \
	-n $TUNERUNNAME \
	$ONLINE_OPTS > logs/"$TUNERUNNAME".online.stdout 2>&1
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
    # Check to see if the user pre-processed the input file
    if [ ! -e $DECODE_FILE ]; then
	ln -s $DECODE_SET $DECODE_FILE
    fi
    
    java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.Phrasal \
	"$RUNNAME".ini \
	< $DECODE_FILE > "$RUNNAME".trans 2> logs/"$RUNNAME".log
}

#
# Evaluate the target output
#
function evaluate {
    cat "$RUNNAME".trans | bleu "$REFDIR"/"$DECODE_SET_NAME"/ref* > "$RUNNAME".bleu

    # Aggregate results from many decoding runs
    \grep -P "^BLEU" "$RUNNAME".bleu | awk '{ print $3 }' | tr -d ',' | echo $(cat -) "$TUNERUNNAME" >> "$DECODE_SET_NAME".BLEU
    cat "$DECODE_SET_NAME".BLEU | sort -nr -o "$DECODE_SET_NAME".BLEU
}

#
# Generate a learning curve from an online run
#
function create-learn-curve {
    REFS=`ls "$REFDIR"/$DECODE_SET_NAME/ref* | tr '\n' ','`
    java $JAVA_OPTS $DECODER_OPTS \
	edu.stanford.nlp.mt.tools.OnlineLearningCurve \
	"$RUNNAME".ini \
	$DECODE_FILE \
	$REFS \
	"$TUNERUNNAME".online.*.binwts > "$RUNNAME".learn-curve.tmp \
	2>/dev/null
    cat "$RUNNAME".learn-curve.tmp | tr '.' ' ' | awk '{ print $4 }' \
	| paste - "$RUNNAME".learn-curve.tmp | grep -v final | sort -n \
	>  "$RUNNAME".learn-curve
    rm -f "$RUNNAME".learn-curve.tmp
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
	prep-source $TUNE_SET $TUNE_FILE $TUNE_PTABLE_DIR $TUNE_SET_NAME
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
	prep-source $DECODE_SET $DECODE_FILE $DECODE_PTABLE_DIR $DECODE_SET_NAME
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
    if [ $step -eq 8 ]; then
	step-status $step
	create-learn-curve
    fi
done
