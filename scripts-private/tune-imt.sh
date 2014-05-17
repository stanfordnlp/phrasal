#!/usr/bin/env bash
#
# Train and tune Phrasal. Steps are configurable with the variables
# at the top of this script.
# 
# Author: Spence Green
#
if [[ $# -ne 6 ]]; then
    echo "Usage: `basename $0` var_file ini_file sys_name imt_src imt_tgt tune_alias"
    echo
    exit 0
fi
VAR_FILE=$1
INI_FILE=$2
SYS_NAME=$3
IMT_SRC=$4
IMT_TGT=$5
TUNE_ALIAS=$6

echo ===========================
echo Phrasal training and tuning
echo ===========================
echo Loading local parameters from $VAR_FILE
source $VAR_FILE
echo System name: $SYS_NAME
echo JavaNLP: $JAVANLP_HOME

######################################################################
######################################################################

function tune-setup {
    # Check to see if the user pre-processed the tuning file
    if [ ! -e $TUNE_FILE ]; then
	ln -s $TUNE_SET $TUNE_FILE
    fi
    
    # Setup the ini file and run online tuning
    execute "update_ini -n $TUNE_NBEST SETID $TUNE_ALIAS \
	< $INI_FILE > $TUNE_INI_FILE"   
}

#
# Online tuning (e.g., Mira, PRO+SGD)
#
function tune-online {
  tune-setup
    
  execute "java $JAVA_OPTS $DECODER_OPTS edu.stanford.nlp.mt.tune.OnlineTuner \
	$IMT_SRC $IMT_TGT \
	$TUNE_INI_FILE \
	$INITIAL_WTS \
	-n $TUNERUNNAME \
	$ONLINE_OPTS > logs/$TUNERUNNAME.online.stdout 2>&1"
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

######################################################################
######################################################################

function execute {
  eval $1
}

function step-status {
    echo "### Running Step $1"
}

function bookmark {
    cp $VAR_FILE "$RUNNAME".vars
    if [ -n "$JAVANLP_HOME" ]; then
	WDIR=`pwd`
	cd $JAVANLP_HOME
	if hash git 2>/dev/null; then
	    git log | head -n 1 | awk '{ print $2 }' > $WDIR/"$RUNNAME".version
	fi
	cd $WDIR
    fi
}

# Synthetic parameters and commands
mkdir -p logs
TUNE_PTABLE_DIR="$TUNE_SET_NAME".tables
TUNE_FILE="$TUNE_SET"
TUNERUNNAME="$TUNE_SET_NAME"."$SYS_NAME"
TUNEDIR="$TUNERUNNAME".tune
TUNE_INI_FILE="$TUNERUNNAME".ini

DECODE_PTABLE_DIR="$DECODE_SET_NAME".tables
DECODE_FILE="$DECODE_SET"

RUNNAME="$DECODE_SET_NAME"."$TUNERUNNAME"

# Log some info about this run
bookmark

step-status "$step -- Run tuning"
tune-online

