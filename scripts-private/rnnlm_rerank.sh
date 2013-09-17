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

### Preprocessing ###
nbest_in_processed=${nbest_out}.in
if [ -f "$nbest_in_processed" ]; then
  echo "! File $nbest_in_processed exists. Skip."
else
  echo ""
  echo "# Preprocessing ..."
  echo "  $JAVANLP_HOME/projects/mt/scripts-private/nbest_uniq.py $nbest_in $nbest_in_processed"
  $JAVANLP_HOME/projects/mt/scripts-private/nbest_uniq.py $nbest_in $nbest_in_processed
fi

### RNNLM rescoring ###
scoreFile="$nbest_out.score"
if [ -f "$scoreFile" ]; then
  echo "! File $scoreFile exists. Skip"
else
  echo ""
  echo "# RNNLM scoring ..."
  date
  echo "  ~lmthang/rnnlm/rnnlm -rnnlm $rnn_model -test $nbest_in_processed -nbest -debug $debug_opt > $scoreFile"
  ~lmthang/rnnlm/rnnlm -rnnlm $rnn_model -test $nbest_in_processed -nbest -debug $debug_opt > $scoreFile
  date
fi

### Postprocessing ###
echo ""
echo "# Add rnnlm scores ..."
echo "$JAVANLP_HOME/projects/mt/scripts-private/nbest_add_rnnlm_score.py $nbest_in $nbest_in_processed $scoreFile $nbest_out" 
$JAVANLP_HOME/projects/mt/scripts-private/nbest_add_rnnlm_score.py $nbest_in $nbest_in_processed $scoreFile $nbest_out 

#echo "rm -rf $nbest_in_processed"
#rm -rf $nbest_in_processed
#echo "rm -rf $scoreFile"
#rm -rf $scoreFile


#cp $nbest_in $nbest_in_processed.nonUnique 

#echo "  removing the last part ..."
#echo "  perl -pi -e 's/ \|\|\| [^\|]+$/\n/g' $nbest_in_processed.nonUnique"
#perl -pi -e 's/ \|\|\| [^\|]+$/\n/g' $nbest_in_processed.nonUnique

#echo "  removing the front ||| part ..."
#echo "  perl -pi -e 's/^\d+ \|\|\| /0 /g' $nbest_in_processed.nonUnique"
#perl -pi -e 's/^\d+ \|\|\| /0 /g' $nbest_in_processed.nonUnique

#echo "  selecting unique sentences ..."
#echo "  uniq $nbest_in_processed.nonUnique > $nbest_in_processed"
#uniq $nbest_in_processed.nonUnique > $nbest_in_processed


