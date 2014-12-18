#!/bin/bash
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Fri Sep 13 16:47:58 PDT 2013

if [[ $# -gt 4 || $# -lt 4 ]]; then
  echo "# Num arguments = $#"
  echo "`basename $0` nbestFile trainWtsFile outputNBestFile output1BestTrans"
  exit
fi

nbestFile=$1
trainWtsFile=$2
outputNBestFile=$3
output1BestTransFile=$4

SCRIPTS_DIR="${JAVANLP_HOME}/projects/mt/scripts-private"
PROMOTE_1BEST="$SCRIPTS_DIR/bolt/promote_new_1best.pl"

function execute_check {
  file=$1 
  cmd=$2
  
  if [[ -f $file || -d $file ]]; then
    echo "! File/directory $file exists. Skip executing: $cmd."
  else
    echo "# Executing: $cmd"
    eval $cmd
  fi
}


### Run reranker  ###
echo "### Run reranker  ###"
date
execute_check $output1BestTransFile "java edu.stanford.nlp.mt.tools.NBestReranker $nbestFile $trainWtsFile $output1BestTransFile $output1BestTransFile.indices"
execute_check $outputNBestFile "perl $PROMOTE_1BEST $nbestFile $output1BestTransFile.indices $outputNBestFile"

