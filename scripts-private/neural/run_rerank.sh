#!/bin/sh
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Fri Sep 13 16:47:58 PDT 2013

if [[ $# -gt 8 || $# -lt 6 ]]; then
  echo "# Num arguments = $#"
  echo "`basename $0` outDir tuneNbestFile tuneRefPrefix testNbestFile testRefPrefix featureStr [mertOpt] [evalOpt]"
  echo "  tuneRefPrefix: the code automatically check if tuneRefPrefix is a file on its own or if there are other files like tuneRefPrefix1, tuneRefPrefix2, etc., to build up a list of all references."
  echo "  testRefPrefix: similar to tuneRefPrefix."
  echo "  featureStr: comma-separated list of weights to tune, e.g., \"rnnlm,nnlm\" implies that our nbest file is of the following format: \"id ||| translation ||| rnnlm: value1 nnlm: value2 dm: value3\", here the decoder score (dm) is always present."
  echo "  mertOpt: same as featureStr but we tell Mert to ignore those\n"
  echo "  evalOpt: 0 -- default, 1 -- Jun14 system, 2 -- Sep14 system" 
  exit
fi

outDir=$1
tuneNbestFile=$2
tuneRefPrefix=$3
testNbestFile=$4
testRefPrefix=$5
featureStr=$6
#evalMetric="bleu"
evalMetric="(ter-bleu)/2"
mertOpt=""
if [ $# -ge 7 ]; then
  mertOpt=$7
fi
evalOpt="0"
if [ $# -eq 8 ]; then
  evalOpt=$8
fi

echo "tuneRefPrefix=$tuneRefPrefix"
echo "testRefPrefix=$testRefPrefix"
echo "featureStr=$featureStr"
echo "mertOpt=$mertOpt"
echo "evalOpt=$evalOpt"
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

## compile comma-separated reference list (mimic code from mt/scripts/phrasal-mert.pl) 
function get_ref_list { 
  refPrefix=$1
  delimiter=$2
  refList=""
  if [ "$refPrefix" != "" ]; then
    nextIndex=1
    if [ -f "${refPrefix}" ]; then 
      refList="${refPrefix}"
    else 
      if [ -f "${refPrefix}0" ]; then 
        refList="${refPrefix}0"
      else 
        if [ -f "${refPrefix}1" ]; then 
          refList="${refPrefix}1"
          nextIndex=2
        else
          echo "! no tune reference found $refPrefix. Make sure you input a reference prefix instead of a full path."
          exit
        fi  
      fi
    fi
    for (( i=$nextIndex; i<=10; i++ )); do
      if [ -f "$refPrefix$i" ]; then 
        refList="${refList}${delimiter}${refPrefix}${i}"
      fi
    done
  fi

  echo "$refList"
}

function rerank_eval {
  transFile=$1
  nbestFile=$2
  wtsFile=$3
  refList=$4
  evalOpt=$5

  ### run reranker on test ###
  echo "# run reranker..."
  execute_check $transFile "java edu.stanford.nlp.mt.tools.NBestReranker $nbestFile $wtsFile $transFile"

  ### evaluation on test ###
  if [ "$refList" != "" ]; then
    echo "# Evaluation"
    execute_check "" "java edu.stanford.nlp.mt.tools.Evaluate \"bleu\" $refList < $transFile"
    execute_check "" "java edu.stanford.nlp.mt.tools.Evaluate \"ter\" $refList < $transFile"
  else
    if [ "$evalOpt" = "1" ]; then # Jun14 system
      execute_check "" "perl /scr/mkayser/mt/experiments/BOLT/jun_2_2014_dryrun/commands_for_thang/score_thang.pl $transFile /scr/mkayser/mt/experiments/BOLT/jun_2_2014_dryrun/commands_for_thang/all_test_sets_thang.regions"
    fi

    if [ "$evalOpt" = "2" ]; then # Sep14 system
      execute_check "" "perl /scr/nlp/data/gale/BOLT/zhen-2014/translate_thang/scoring/score.pl $transFile /juicy/scr42/scr/nlp/data/gale/BOLT/zhen-2014/translate_thang/scoring/test_set.info_for_ibm_prep"
    fi
  fi
}

# check outDir exists
echo "# outDir $outDir"
execute_check "$outDir" "mkdir -p $outDir"

tuneRefList=$( get_ref_list "$tuneRefPrefix" "," ) 
tuneSpaceRefList=$( get_ref_list "$tuneRefPrefix" " " ) 
echo "# tuneRefList=$tuneRefList"
echo "# tuneSpaceRefList=$tuneSpaceRefList"

testRefList=$( get_ref_list "$testRefPrefix" "," ) 
testSpaceRefList=$( get_ref_list "$testRefPrefix" " " ) 
echo "# testRefList=$testRefList"
echo "# testSpaceRefList=$testSpaceRefList"

# split featureStr
IFS=',' read -a features <<< "$featureStr"

### Train weights ###
# tuning with Mert
initWtsFile="$outDir/init.mert.wts"
for index in "${!features[@]}"; do
  echo "${features[index]} 1.0" >> $initWtsFile
done
#echo "dm 1.0" >> $initWtsFile

trainWtsFile="$outDir/train.wts" #$initWtsFile #
echo ""
echo "# tuning with Mert ..."
date
execute_check $trainWtsFile  "java edu.stanford.nlp.mt.tune.MERT $mertOpt -o koehn -t 12 -p 32 \"$evalMetric\" $tuneNbestFile $tuneNbestFile $initWtsFile $tuneRefList $trainWtsFile > $outDir/mert.log 2>&1"

# show weights
echo ""
echo "# show weights ..."
execute_check "" "java edu.stanford.nlp.mt.tools.PrintWeights $trainWtsFile"

### Run reranker on tune ###
echo "### Run reranker on tune ###"
date
tuneTransFile="$outDir/tune.trans"
rerank_eval $tuneTransFile $tuneNbestFile $trainWtsFile "$tuneSpaceRefList" $evalOpt

### Run reranker on test ###
echo "### Run reranker on test ###"
date
testTransFile="$outDir/test.trans"
rerank_eval $testTransFile $testNbestFile $trainWtsFile "$testSpaceRefList" $evalOpt


### Baseline results with only decoder scores ##
## create init weight file
#initWtsFile="$outDir/init.wts"
##for index in "${!features[@]}"; do
##  echo "${features[index]} 0.0" >> $initWtsFile
##done
#echo "dm 1.0" >> $initWtsFile
#
## run reranker on tune
#echo ""
#echo "## Baseline resutls on tune ##"
#tuneTransFile="$outDir/tune.base.trans"
#rerank_eval $tuneTransFile $tuneNbestFile $initWtsFile "$tuneSpaceRefList" $evalOpt
#
## run reranker on test
#echo ""
#echo "## Baseline resutls on test ##"
#testTransFile="$outDir/test.base.trans"
#rerank_eval $testTransFile $testNbestFile $initWtsFile "$testSpaceRefList" $evalOpt



