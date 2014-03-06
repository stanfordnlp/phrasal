#!/bin/sh
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Fri Sep  6 14:21:50 PDT 2013

if [[ ! $# -eq 6 && ! $# -eq 5 ]]
then
    echo "`basename $0` model vocab_file nbest_in nbest_out model_opt [nbest_dir]"
    echo "  model_opt: 1 -- RNNLM, 2 -- NPLM"
    echo "  nbest_dir: contains all nbest files (end with the suffix \"best\"), which we will concatenate and select only distinct sentences for the neural model to score."
    exit
fi

model=$1
vocab_file=$2
nbest_in=$3
nbest_out=$4
model_opt=$5
nbest_dir=""
if [ $# -eq 6 ]; then
  nbest_dir=${6}
fi
echo "# model=$model"
echo "# vocab_file=$vocab_file"
echo "# nbest_in=$nbest_in"
echo "# nbest_out=$nbest_out"
echo "# model_opt=$model_opt"
echo "# nbest_dir=$nbest_dir"
outDir=`dirname $nbest_out`
echo "# outDir=$outDir"
scriptDir="$JAVANLP_HOME/projects/mt/scripts-private/neural"
echo "# scriptDir=$scriptDir"

VERBOSE=1
function execute_check {
  file=$1 
  cmd=$2
  
  if [[ -f $file || -d $file ]]; then
    echo ""
    echo "! File/directory $file exists. Skip."
  else
    echo ""
    if [ $VERBOSE -eq 1 ]; then
      echo "# Executing: $cmd"
    fi
    
    eval $cmd
  fi
}

execute_check $outDir "mkdir -p $outDir"


### Preprocessing ###
echo ""
echo "# Preprocessing ..."
# date

# all_nbest
if [ "$nbest_dir" != "" ]; then # concat all nbest lists in this dir
  all_nbest="$nbest_dir/allNbest"
  execute_check $all_nbest "cat $nbest_dir/*best > $all_nbest"
else # copy the input nbest list
  all_nbest=`basename $nbest_in`
  all_nbest=$outDir/$all_nbest
  execute_check $all_nbest "ln -s $nbest_in $all_nbest"
fi
echo "# all_nbest=$all_nbest"

# all_nbest_distinct: select distinct translations
echo ""
echo "# Selecting distinct translation ..."
# date
all_nbest_distinct="$all_nbest.distinct"
execute_check $all_nbest_distinct "$scriptDir/nbest_uniq.py $all_nbest $all_nbest_distinct"


# all_nbest_distinct_unk: convert all oov words into <unk>
echo ""
echo "# Processing <unk> ..."
# date
all_nbest_distinct_unk="$all_nbest_distinct.unk"
execute_check $all_nbest_distinct_unk "$scriptDir/add_unk_corpus.py $vocab_file $all_nbest_distinct $all_nbest_distinct_unk"

### Rescoring ###
echo ""
echo "# Rescoring ..."
# date
model_basename=`basename $model`
all_nbest_scores="$all_nbest_distinct_unk.$model_basename"
if [ $model_opt -eq 1 ]; then
  echo "# RNNLM"
  execute_check $all_nbest_scores "~lmthang/rnnlm_ext/rnnlm -independent -rnnlm $model -test $all_nbest_distinct_unk -nbest -debug 2 > $all_nbest_scores"
  execute_check $nbest_out "$scriptDir/nbest_add_neural_score.py rnnlm $nbest_in $all_nbest_distinct $all_nbest_scores $nbest_out"
else
  echo "# NPLM"
  execute_check $all_nbest_scores "~lmthang/nplm/src/testNeuralLM --model_file $model --test_file $all_nbest_distinct_unk --minibatch_size 1000 > $all_nbest_scores" 
  execute_check $nbest_out "$scriptDir/nbest_add_neural_score.py nplm $nbest_in $all_nbest_distinct $all_nbest_scores $nbest_out"
fi

