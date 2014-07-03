#!/usr/bin/env bash
#
# Wrapper script for phrasal.sh that allows parameter sweeping
# 
# 
# Author: Sebastian Schuster
#

if [[ $# -lt 8 ]]; then
    echo
    echo "Usage: `basename $0` var_file steps ini_file sys_name -verbose \
location parameter short_name possible_values"
    echo "eg. `basename $0` phrasal.vars 2,4-6 phrasal.ini baseline ini \
distortion-limit dlimit 5 6 7 8 9 10"
    echo
    echo "Use dashes and commas in the steps specification e.g. 1-3,6"
    echo
    echo Step definitions:
    echo "  1  Extract phrases from dev set"
    echo "  2  Run tuning"
    echo "  3  Extract phrases from test set"
    echo "  4  Decode test set"
    echo "  5  Output results file"
    echo "  6  Generate a learning curve from an online run"
    echo

    echo "  -verbose (optional): output detailed commands that get executed"
    echo "  location: ini or vars, the location of the parameter"
    echo "  parameter: the name of the parameter that should be replaced"
    echo "  short_name: a name that will be used for the \
system name and the file name of the parameter files"
    echo "  possible values: a space separated list of possible values for \
the parameter"
    echo
    exit 0
fi
     

#read the parameters
VAR_FILE=$1
EXEC_STEPS=$2
INI_FILE=$3
SYS_NAME=$4

VERBOSE=0
shift 4

if [ "$1" = "-verbose" ]
then
    VERBOSE=1
    shift
fi


PARAM_LOCATION=$1
PARAM_NAME=$2
PARAM_SHORT_NAME=$3
shift 3

#parses parameter ranges with dashes and commas
function parse-range {
    unset VALS
    # Process steps
    let s=0
    IFS=',' read -ra ADDR <<< "$1"
    for i in "${ADDR[@]}"; do
        IFS='-' read -ra SEQ <<< "$i"
        if [ ${#SEQ[@]} -eq 1 ]; then
        VALS[s]=${SEQ[0]}
        let s=s+1
        else
        for j in `seq ${SEQ[0]} ${SEQ[1]}`;
        do
            VALS[s]=$j
            let s=s+1
        done
        fi
    done
}


function run-ini-parameter-experiments {
    PARAM=$1
    SHORT_NAME=$2
    POSSIBLE_VALUES=$3
    
    INI_FILE_BASENAME="${INI_FILE%.*}"
    
    
    INI_PARAM_LINE=$(grep  -n "\[$PARAM\]" $INI_FILE | egrep -o "^[0-9]+")
    
    #generate ini files with varied parameters
    for val in $POSSIBLE_VALUES;
    do
        INI_FILE_MODIFIED=${INI_FILE_BASENAME}.${SHORT_NAME}-${val}.ini
        if [ "$INI_PARAM_LINE" != "" ]
        then
            sed "${INI_PARAM_LINE}d" $INI_FILE | sed "${INI_PARAM_LINE}d" \
                > $INI_FILE_MODIFIED
        else
            cp $INI_FILE $INI_FILE_MODIFIED
        fi
        
        echo "" >> $INI_FILE_MODIFIED
        echo "[$PARAM]" >> $INI_FILE_MODIFIED
        echo $val >> $INI_FILE_MODIFIED
    
        for step in $STEPS;
        do
            phrasal.sh $VAR_FILE $step \
                $INI_FILE_MODIFIED \
                ${SYS_NAME}.${SHORT_NAME}-${val} $VERBOSE
        done
    done
    
}

function run-vars-parameter-experiments {
    PARAM=$1
    SHORT_NAME=$2
    POSSIBLE_VALUES=$3
    
    VAR_FILE_BASENAME="${VAR_FILE%.*}"
    
    
    VAR_PARAM_LINE=$(grep  -n "^${PARAM}=" $VAR_FILE | egrep -o "^[0-9]+")
    
    #generate vars files with varied parameters
    for val in $POSSIBLE_VALUES;
    do
        VAR_FILE_MODIFIED=${VAR_FILE_BASENAME}.${SHORT_NAME}-${val}.vars
        if [ "$VAR_PARAM_LINE" != "" ]
        then
            sed "s/${PARAM}=.*/${PARAM}=${val}/" $VAR_FILE > $VAR_FILE_MODIFIED
        else
            cp $VAR_FILE $VAR_FILE_MODIFIED
            echo "" >> $VAR_FILE_MODIFIED
            echo "${PARAM}=${val}" >> $VAR_FILE_MODIFIED
        fi
    
        for step in $STEPS;
        do
            phrasal.sh $VAR_FILE_MODIFIED $step \
                $INI_FILE \
                ${SYS_NAME}.${SHORT_NAME}-${val} $VERBOSE
        done
    done
    
}


#parse the steps
parse-range $EXEC_STEPS
STEPS=${VALS[@]}


case "$PARAM_LOCATION" in
    ini)
        run-ini-parameter-experiments $PARAM_NAME $PARAM_SHORT_NAME "$*"
        ;;
    vars)
        run-vars-parameter-experiments $PARAM_NAME $PARAM_SHORT_NAME "$*"
        ;;
    *)
        echo "Invalid location parameter. Specify ini for a parameter in the ini\
or vars for a parameter in the vars file."

esac
