#!/bin/sh
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Fri Sep 13 16:47:58 PDT 2013

if [[ $# -gt 6 || $# -lt 6 ]]; then
  echo "# Num arguments = $#"
  echo "`basename $0` outDir tuneNbestFile tuneRefPrefix testNbestFile testRefPrefix featureStr"
  echo "featureStr: comma-separated list of weights to tune, e.g., rnnlm,dm"
  exit
fi

outDir=$1
tuneNbestFile=$2
tuneRefPrefix=$3
testNbestFile=$4
testRefPrefix=$5
featureStr=$6
evalMetric="bleu"
#if [ $# -ge 4 ]; then
#  rnnlmModel=${4}
#fi

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

function rerank_eval {
  transFile=$1
  nbestFile=$2
  wtsFile=$3
  refList=$4

  ### run reranker on test ###
  echo ""
  echo "# run reranker..."
  execute_check $transFile "java edu.stanford.nlp.mt.tools.NBestReranker $nbestFile $wtsFile $transFile"

  ### evaluation on test ###
  echo ""
  echo "# Evaluation"
  execute_check "" "java edu.stanford.nlp.mt.metrics.BLEUMetric $refList < $transFile"
  execute_check "" "java edu.stanford.nlp.mt.metrics.TERMetric $refList < $transFile"

}

# check outDir exists
echo "# outDir $outDir"
execute_check "$outDir" "mkdir -p $outDir"

## compile comma-separated reference list (mimic code from mt/scripts/phrasal-mert.pl) 
# tuneRefList
if [ "$tuneRefPrefix" != "" ]; then
  tuneRefList=""
  nextIndex=1
  if [ -f "${tuneRefPrefix}" ]; then 
    tuneRefList="${tuneRefPrefix}"
  else 
    if [ -f "${tuneRefPrefix}0" ]; then 
      echo "# references are 0 base"
      tuneRefList="${tuneRefPrefix}0"
    else 
      if [ -f "${tuneRefPrefix}1" ]; then 
        echo "# references are 1 base";
        tuneRefList="${tuneRefPrefix}1"
        nextIndex=2
      else
        echo "! no reference found. Make sure you input a reference prefix instead of a full path."
        exit
      fi  
    fi
  fi
  for (( i=$nextIndex; i<=10; i++ )); do
    if [ -f "$tuneRefPrefix$i" ]; then 
      tuneRefList="$tuneRefList,$tuneRefPrefix$i"
    fi
  done
  
  echo "# tuneRefList=$tuneRefList"
  tuneBasename=`basename $tuneNbestFile`
fi

# testRefList
testRefList=""
nextIndex=1
if [ -f "${testRefPrefix}" ]; then 
  testRefList="${testRefPrefix}"
else 
  if [ -f "${testRefPrefix}0" ]; then 
    echo "# references are 0 base"
    testRefList="${testRefPrefix}0"
  else 
    if [ -f "${testRefPrefix}1" ]; then 
      echo "# references are 1 base";
      testRefList="${testRefPrefix}1"
      nextIndex=2
    else
      echo "! no reference found. Make sure you input a reference prefix instead of a full path."
      exit
    fi  
  fi
fi
for (( i=$nextIndex; i<=10; i++ )); do
  if [ -f "$testRefPrefix$i" ]; then 
    testRefList="$testRefList,$testRefPrefix$i"
  fi
done

echo "# testRefList=$testRefList"
testBasename=`basename $testNbestFile`

# split featureStr
IFS=',' read -a features <<< "$featureStr"

## Baseline results ##
# create init weight file
initWtsFile="$outDir/init.wts"
for index in "${!features[@]}"; do
  echo "${features[index]} 0.0" >> $initWtsFile
done
echo "dm 1.0" >> $initWtsFile

# run reranker on tune
if [ "$tuneRefPrefix" != "" ]; then
  tuneTransFile="$outDir/$tuneBasename.base.trans"
  rerank_eval $tuneTransFile $tuneNbestFile $initWtsFile $tuneRefList
fi

# run reranker on test
testTransFile="$outDir/$testBasename.base.trans"
rerank_eval $testTransFile $testNbestFile $initWtsFile $testRefList


### Train weights ###
# tuning with Mert
initWtsFile="$outDir/init.mert.wts"
for index in "${!features[@]}"; do
  echo "${features[index]} 1.0" >> $initWtsFile
done
echo "dm 1.0" >> $initWtsFile

trainWtsFile="$outDir/train.wts"
echo ""
echo "# tuning with Mert ..."
execute_check $trainWtsFile  "java edu.stanford.nlp.mt.tune.MERT -o koehn -t 12 -p 32 $evalMetric $tuneNbestFile $tuneNbestFile $initWtsFile $tuneRefList $trainWtsFile > $outDir/mert.log 2>&1"

# show weights
echo ""
echo "# show weights ..."
execute_check "" "java edu.stanford.nlp.mt.tools.PrintWeights $trainWtsFile"

### Run reranker on tune ###
if [ "$tuneRefPrefix" != "" ]; then
  tuneTransFile="$outDir/$tuneBasename.trans"
  rerank_eval $tuneTransFile $tuneNbestFile $trainWtsFile $tuneRefList
fi

### Run reranker on test ###
testTransFile="$outDir/$testBasename.trans"
rerank_eval $testTransFile $testNbestFile $trainWtsFile $testRefList

