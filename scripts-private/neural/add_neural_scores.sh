#!/bin/bash

set -e

if [[ $# -gt 6 || $# -lt 6 ]]
then
  echo "`basename $0` sourceFile nbestInputFile nbestOutputFile modelPrefixes testFlags device" 
  echo "device: gpu0 or gpu1"
  exit
fi

function execute_check {
  file=$1 
  cmd=$2

  if [[ -f $file || -d $file ]]; then
    echo "! File/directory $file exists. Skip."
  else
    echo "# Output file: $file"
    echo "  executing: $cmd"
    eval $cmd
  fi
}

srcFile=$1
nbestInputFile=$2
nbestOutputFile=$3
modelPrefixes=$4
testFlags=$5 #  --act_func relu --nbest
device=$6 # gpu0 or gpu1

PYTHON="python2.7"
SCRIPT_DIR="$JAVANLP_HOME/projects/mt/scripts-private"
CLEAN="${SCRIPT_DIR}/neural/clean_nbest.py"
ADD_SCORE="$SCRIPT_DIR/neural/nbest_add_neural_score.py"
TEST_NNLM="${NNLM_HOME}/code/test_nnlm.py"

# joint model 
isJoint=0
if [[ "$testFlags" == *--joint* ]]; then
  isJoint=1
  echo "# Detect --joint option in test flags \"$testFlags\""
  testFlags="$testFlags --src_file $srcFile"
fi

echo "# nbestDir=$nbestDir"
echo "# modelPrefixes=$modelPrefixes"
echo "# testFlags=$testFlags"
echo "# nBestFile=$nbestFile"
echo "# device=$device"
echo "# opt=$opt"
date

# clean nbests
execute_check "$nbestInputFile.clean" "$CLEAN -o 4 $nbestInputFile $nbestInputFile.clean"


# go through each model
IFS=',' read -a models <<< "$modelPrefixes"

nnlmFiles=""

# compute nnlm scores 
for index in "${!models[@]}"; do
  let count=index+1
  modelPrefix=${models[index]}
  modelName=`basename $modelPrefix`
  echo "# modelPrefix=$modelPrefix"
  echo "# modelName=$modelName"

  if [ $isJoint -eq 1 ]; then
      execute_check "$nbestInputFile.clean.nnlm.$modelName" "THEANO_FLAGS='device=$device' $PYTHON $TEST_NNLM $testFlags $modelPrefix.model $modelPrefix.vocab $nbestInputFile $nbestInputFile.clean.nnlm.$modelName > $nbestInputFile.clean.nnlm.$modelName.stderr 2>&1"
  else
      execute_check "$nbestInputFile.clean.nnlm.$modelName" "THEANO_FLAGS='device=$device' $PYTHON $TEST_NNLM $testFlags $modelPrefix.model $modelPrefix.vocab $nbestInputFile.clean $nbestInputFile.clean.nnlm.$modelName > $nbestInputFile.clean.nnlm.$modelName.stderr 2>&1"
  fi
  nnlmFiles="${nnlmFiles}$nbestInputFile.clean.nnlm.$modelName,"
  date
done

echo "# nnlmFiles=$nnlmFiles"

# add nnlm scores
inFile=$nbestInputFile.clean
execute_check "$nbestOutputFile" "$ADD_SCORE nnlm $inFile $nnlmFiles $nbestOutputFile > $nbestOutputFile.stderr 2>&1"
