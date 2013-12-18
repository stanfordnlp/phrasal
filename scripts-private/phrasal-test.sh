#!/usr/bin/env bash
#
# Convenience script (around phrasal.sh) for decoding multiple
# test sets given a weight vector.
#
# Author: Sida Wang, Spence Green
#
if [ $# -lt 5 ]; then
    echo Usage: `basename $0` tuneset name iteration oldtestset newtestset:filepath "[newtestset:filepath]"
    echo e.g. `basename $0` mt06 l1-001-cluster 5 mt05 mt04:zhdata/mt04.seg.zh
    exit -1
fi

#
# Take the results of a tuning run and decode a test set
#
function decodetest {
    echo Decoding on $testset tuned on $tuneset runname $name
    if [ -L $tuneset.$name.online.final.binwts ]; then
	rm $tuneset.$name.online.final.binwts
    fi
    if [ -e $tuneset.$name.online.final.binwts ]; then
	mv $tuneset.$name.online.final.binwts $tuneset.$name.online.final.binwts.bak
    fi

    # Symlink the appropriate iteration
    ln -s  $tuneset.$name.online.$iter.binwts $tuneset.$name.online.final.binwts
    
    varfile=$testset.$tuneset.$name.vars
    if [ -e $testset.$tuneset.$name.vars ]; then
	echo var file already exists, using the existing one
    else
	echo var file does not exist, making new one
	cat $oldtestset.$tuneset.$name.vars | grep -v "DECODE_SET" > $varfile
	echo "DECODE_SET=$testsetloc" >> $varfile
	echo "DECODE_SET_NAME=$testset" >> $varfile
    fi

    phrasal.sh $varfile 4-5 $tuneset.$name.ini $name
    mv $testset.$tuneset.$name.bleu $testset.$tuneset.$name.$iter.bleu
}

tuneset=$1
name=$2
iter=$3
oldtestset=$4
shift 4

# Iterate over test sets
for tuple in $@; do
    echo $tuple
    testset=${tuple%:*}
    testsetloc=${tuple#*:}
    decodetest
done
