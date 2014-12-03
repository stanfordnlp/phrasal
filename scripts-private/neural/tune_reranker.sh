#!/bin/bash
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Fri Sep 13 16:47:58 PDT 2013

if [[ $# -gt 6 || $# -lt 5 ]]; then
  echo "# Num arguments = $#"
  echo "`basename $0` outDir tuneNbestFile tuneRefPrefix featureStr evalMetric [mertOpt]"
  echo "  tuneRefPrefix: the code automatically check if tuneRefPrefix is a file on its own or if there are other files like tuneRefPrefix1, tuneRefPrefix2, etc., to build up a list of all references."
  echo "  featureStr: comma-separated list of weights to tune, e.g., \"rnnlm,nnlm\" implies that our nbest file is of the following format: \"id ||| translation ||| rnnlm: value1 nnlm: value2 dm: value3\", here the decoder score (dm) is always present."
  echo "  mertOpt: same as featureStr but we tell Mert to ignore those\n"
  exit 1
fi

outDir=$1
tuneNbestFile=$2
tuneRefPrefix=$3
featureStr=$4
evalMetric=$5
mertOpt=""
if [ $# -ge 6 ]; then
  mertOpt=$6
fi

echo "tuneRefPrefix=$tuneRefPrefix"
echo "featureStr=$featureStr"
echo "mertOpt=$mertOpt"

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



# check outDir exists
echo "# outDir $outDir"
execute_check "$outDir" "mkdir -p $outDir"

tuneRefList=`ls $tuneRefPrefix* | perl -ne 's/\s+/,/; print' | perl -ne 's/,$//; print'`
echo "# tuneRefList=$tuneRefList"

# split featureStr
IFS=',' read -a features <<< "$featureStr"

### Train weights ###
# tuning with Mert
initWtsFile="$outDir/init.mert.wts"
for index in "${!features[@]}"; do
  echo "${features[index]} 1.0" >> $initWtsFile
done

trainWtsFile="$outDir/train.wts" #$initWtsFile #
echo ""
echo "# tuning with Mert ..."
date
execute_check $trainWtsFile  "java edu.stanford.nlp.mt.tune.MERT $mertOpt -o koehn -t 12 -p 32 \"$evalMetric\" $tuneNbestFile $tuneNbestFile $initWtsFile $tuneRefList $trainWtsFile > $outDir/mert.log 2>&1"

# show weights
echo ""
echo "# show weights ..."
execute_check "" "java edu.stanford.nlp.mt.tools.PrintWeights $trainWtsFile"

