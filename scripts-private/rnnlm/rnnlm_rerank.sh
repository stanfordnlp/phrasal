#!/bin/sh
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Fri Sep  6 14:21:50 PDT 2013

if [[ ! $# -eq 6 && ! $# -eq 4  && ! $# -eq 5 ]]
then
    echo "`basename $0` rnnlm_model vocab_file nbest_in nbest_out [debug_opt] [nbest_dir]"
    echo "  nbest_dir: contains all nbest files (end with the suffix \"best\"), which we will concatenate and select only distinct sentences for the RNNLM to score."
    exit
fi

rnnlm_model=$1
vocab_file=$2
nbest_in=$3
nbest_out=$4
debug_opt=0
nbest_dir=""
if [ $# -ge 5 ]; then
  debug_opt=${5}
fi
if [ $# -eq 6 ]; then
  nbest_dir=${6}
fi
echo "# rnnlm_model=$rnnlm_model"
echo "# vocab_file=$vocab_file"
echo "# nbest_in=$nbest_in"
echo "# nbest_out=$nbest_out"
echo "# debug_opt=$debug_opt"
echo "# nbest_dir=$nbest_dir"

# check outDir exists
outDir=`dirname $nbest_out`
echo "# outDir $outDir"
if [ -d $outDir ]; then
  echo "# Directory exists $outDir"
else
  mkdir -p $outDir
fi


### Preprocessing ###
echo ""
echo "# Preprocessing ..."

# all_nbest
if [ "$nbest_dir" != "" ]; then # concat all nbest lists in this dir
  all_nbest="$nbest_dir/all.nbest"

  if [ -f "$all_nbest" ]; then
    echo "! File all_nbest $all_nbest exists. Skip."
  else 
    echo " # compiling all_nbest $all_nbest ..."
    echo "  cat $nbest_dir/*best > $all_nbest"
    cat $nbest_dir/*best > $all_nbest
  fi
else # copy the input nbest list
  all_nbest=${nbest_out}.in
  if [ -f "$all_nbest" ]; then
    echo "! File all_nbest $all_nbest exists. Skip."
  else
    echo "  cat $nbest_in > $all_nbest"
    cat $nbest_in > $all_nbest
  fi
fi
echo "# all_nbest=$all_nbest"

# all_nbest_distinct: select distinct translations
all_nbest_distinct="$all_nbest.distinct"
if [ -f "$all_nbest_distinct" ]; then
  echo "! File all_nbest_distinct $all_nbest_distinct exists. Skip."
else
  echo " # selecting distinct translations ..."
  echo "  $JAVANLP_HOME/projects/mt/scripts-private/rnnlm/nbest_uniq.py $all_nbest $all_nbest_distinct"
  $JAVANLP_HOME/projects/mt/scripts-private/rnnlm/nbest_uniq.py $all_nbest $all_nbest_distinct
fi

# escape <U+FFFA> symbols
all_nbest_distinct_escaped=$all_nbest_distinct.escaped
if [ -f "$all_nbest_distinct_escaped" ]; then
  echo "! File all_nbest_distinct_escaped $all_nbest_distinct_escaped exists. Skip."
else
  cp $all_nbest_distinct $all_nbest_distinct_escaped
  echo "perl -C -pi -e 's/\x{FFFA}/ /g' $all_nbest_distinct_escaped"
  perl -C -pi -e 's/\x{FFFA}/ /g' $all_nbest_distinct_escaped
fi

# all_nbest_distinct_escaped_unk: convert all oov words into <UNK>
all_nbest_distinct_escaped_unk="$all_nbest_distinct_escaped.unk"
if [ -f "$all_nbest_distinct_escaped_unk" ]; then
  echo "! File all_nbest_distinct_escaped_unk $all_nbest_distinct_escaped_unk exists. Skip."
else
  echo " # converting oov words into <UNK> ..."
  echo "  $JAVANLP_HOME/projects/mt/scripts-private/rnnlm/add_unk_corpus.py $vocab_file $all_nbest_distinct $all_nbest_distinct_escaped_unk"
  $JAVANLP_HOME/projects/mt/scripts-private/rnnlm/add_unk_corpus.py $vocab_file $all_nbest_distinct $all_nbest_distinct_escaped_unk
fi

### RNNLM rescoring ###
rnnlm_basename=`basename $rnnlm_model`
all_nbest_scores="$all_nbest_distinct_escaped_unk.$rnnlm_basename"
if [ -f "$all_nbest_scores" ]; then
  echo "! File $all_nbest_scores exists. Skip"
else
  echo ""
  echo "# RNNLM scoring ..."
  date
  echo "  ~lmthang/rnnlm_ext/rnnlm -rnnlm $rnnlm_model -test $all_nbest_distinct_escaped_unk -nbest -debug $debug_opt > $all_nbest_scores"
  ~lmthang/rnnlm_ext/rnnlm -rnnlm $rnnlm_model -test $all_nbest_distinct_escaped_unk -nbest -debug $debug_opt > $all_nbest_scores
  date
fi

### Postprocessing ###
echo ""
echo "# Add rnnlm scores ..."
echo "$JAVANLP_HOME/projects/mt/scripts-private/rnnlm/nbest_add_rnnlm_score.py $nbest_in $all_nbest_distinct $all_nbest_scores $nbest_out" 
$JAVANLP_HOME/projects/mt/scripts-private/rnnlm/nbest_add_rnnlm_score.py $nbest_in $all_nbest_distinct $all_nbest_scores $nbest_out 

