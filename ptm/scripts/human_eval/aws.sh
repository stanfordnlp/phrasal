#!/usr/bin/env bash
#
# Communicate with MTurk. The site does not update immediately, so wait
# 30s+ before verifying the results of these operations.
#
# This script should be executed in the root maise folder:
#  ${JAVANLP_HOME}/projects/mt/ptm/maise
#
#
# UPLOADER operation:
#  Default is to upload the HITs specified in the uploadinfo file.
#  Make sure that mturk.properties points to the right upload
#  target! (sandbox or production)
#
#
# CLEANER operation:
#  Default is to remove everything in "Assignable" or "Completed" state.
#  HITs in the "Submitted" state must first be approved using the Retriever
#  operation.
#
#
# RETRIEVER operation:
#  Default is to download results of all HITs in the "Submitted" state.
#
#  To approve or reject hits, you must run a decision pass. The following
#  arguments are relevant:
#
#    -DdecisionPass=true        // What it says.
#
#    -DrejectList=myfile.tsv    // An TSV list of <AID,reason> tuples
#
#    -Drelist=true              // Relist the rejected HITs
#
#
if [ $# -lt 2 ]; then
    echo Usage: `basename $0` "[up|clean|get]" batch_name "[extra_args]"
    exit -1
fi

op=$1
batch=$2
shift 2

if [ $op == "up" ]; then
    ant uploader -Dfile="$batch".uploadinfo $*

elif [ $op == "clean" ]; then
    ant cleaner -DdelAssignable=true -DdelCompleted=true -Dfield=keywords -Dquery="$batch" $*

elif [ $op == "get" ]; then
    ant retriever -Danswers="$batch".answers.log -Dfield=keywords -Dquery="$batch" $*
fi
