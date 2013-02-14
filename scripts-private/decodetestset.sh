#!/usr/bin/env bash
# Author: sidaw
if [ $# -ne 5 ]; then
    echo Usage: `basename $0` tuneset name testsetname testsetloc iterationnumber
    echo e.g. `basename $0` mt06 l1-001-cluster mt04 zhdata/mt04.seg.zh 5
    exit 0
fi

function decodetest {
echo decoding on $testset tuned on $tuneset runname  $name
mv $tuneset.$name.online.final.binwts $tuneset.$name.online.final.binwts.$RANDOM
ln -s  $tuneset.$name.online.$iter.binwts $tuneset.$name.online.final.binwts
if [ -e $testset.$tuneset.$name.vars ]; then
echo var file already exists, using the exisitng one
else
echo var file does not exisit, making new one by concatenatiing DECODE_SET and DECODE_SET_NAME to the end
varname=$testset.$tuneset.$name.vars
cat test5k.$tuneset.$name.vars > $varname
echo '' >> $varname
echo DECODE_SET=$testsetloc >> $varname
echo DECODE_SET_NAME=$testset >> $varname
fi

phrasal-train-tune.sh $testset.$tuneset.$name.vars 6-7 $tuneset.$name.ini $name
mv $testset.$tuneset.$name.bleu $testset.$tuneset.$name.$iter.bleu
}


tuneset=$1
name=$2
testset=$3
testsetloc=$4
iter=$5
decodetest

