#!/usr/bin/env bash
#
# Convert raw answer file to CSV.
#
# OPTIONAL ARGUMENTS:
#
#   filterList=AID_list.txt   // Line-delimited AID list
#
#
#
if [ $# -lt 3 ]; then
    echo Usage: `basename $0` answer_file batch_name server_info "[OPTIONS]"
    exit -1
fi

classpath=${JAVANLP_HOME}/projects/mt/ptm/maise/lib

ans_file=$1
batch=$2
server_file=$3
shift 3

java -cp $classpath AnalyzeRNKResults answers="$ans_file" collection="$batch" serverInfo="$server_file" $*

