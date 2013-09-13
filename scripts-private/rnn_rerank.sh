#!/bin/sh
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Fri Sep  6 14:21:50 PDT 2013

if [[ ! $# -eq 3 && ! $# -eq 4 ]]
then
    echo "`basename $0` rnn_model nbest_in nbest_out [debug_opt]" 
    exit
fi

rnn_model=$1
nbest_in=$2
nbest_out=$3
if [ $# -eq 4 ]; then
  debug_opt=${4}
else
  debug_opt=0
fi
echo "# nbest_in=$nbest_in"
echo "# nbest_out=$nbest_out"

# check outDir exists
outDir=`dirname $nbest_out`
echo "# outDir $outDir"
if [ -d $outDir ]; then
  echo "# Directory exists $outDir"
else
  mkdir -p $outDir
fi

echo ""
echo "# Preprocessing ..."
cp $nbest_in ${nbest_out}.in
nbest_in_processed=${nbest_out}.in

echo " removing the last part ..."
echo "  perl -pi -e 's/ \|\|\| [^\|]+$/\n/g' $nbest_in_processed"
perl -pi -e 's/ \|\|\| [^\|]+$/\n/g' $nbest_in_processed

echo " removing the front ||| part ..."
echo "  perl -pi -e 's/ \|\|\| / /g' $nbest_in_processed"
perl -pi -e 's/ \|\|\| / /g' $nbest_in_processed

echo ""
echo "# RNNLM scoring"
scoreFile="$nbest_out.score"
if [ -f "$scoreFile" ]; then
  echo "File $scoreFile exists!"
else
  date
  echo "  ~lmthang/rnnlm/rnnlm -rnnlm $rnn_model -test $nbest_in_processed -nbest -debug $debug_opt > $scoreFile"
  ~lmthang/rnnlm/rnnlm -rnnlm $rnn_model -test $nbest_in_processed -nbest -debug $debug_opt > $scoreFile
  date
fi

# concat scores line by line
echo ""
echo "# Add rnnlm scores ..."
echo "~lmthang/bin/mt/add_nbest_score.py $nbest_in $scoreFile $nbest_out" 
~lmthang/bin/mt/add_nbest_score.py $nbest_in $scoreFile $nbest_out 

#echo "rm -rf $nbest_in_processed"
#rm -rf $nbest_in_processed
#echo "rm -rf $scoreFile"
#rm -rf $scoreFile

