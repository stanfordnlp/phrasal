#!/usr/bin/env bash
#
# Splits and aligns a bitext using
# Berkeley aligner 2.1 and the Phrasal implementation
# of symmetrization heuristics.
#
if [ $# -ne 5 ]; then
    echo Usage: `basename $0` src_file src_lang tgt_file tgt_lang lines_per_split
    echo
    echo src_lang/tgt_lang should be two-letter ISO 639-1 language codes.
    exit -1
fi

# Uncomment the line below to run locally 
MEM=15g
#EXEC=echo
EXEC="nlpsub -m${MEM} -c4 -pbackground"

src_file=$1
src_lang=$2
tgt_file=$3
tgt_lang=$4
split_size=$5

src_name=$(basename "$src_file")
src_ext="${src_name##*.}"
CAT1=cat
if [ $src_ext == "gz" ]; then
	CAT1=zcat
elif [ $src_ext == "bz2" ]; then
	CAT1=bzcat
fi

tgt_name=$(basename "$tgt_file")
tgt_ext="${tgt_name##*.}"
CAT2=cat
if [ $tgt_ext == "gz" ]; then
	CAT2=zcat
elif [ $tgt_ext == "bz2" ]; then
	CAT2=bzcat
fi


scriptdir=${JAVANLP_HOME}/projects/mt/ptm/scripts/mt
mkconf=${scriptdir}/mkconf.py
conf_template=${scriptdir}/ucb-align.conf
align=${JAVANLP_HOME}/projects/mt/scripts/align


# Step 1: Split the bitext
echo Splitting the bitext...
$CAT1 ${src_file} | split -l "$split_size" -d - split."$src_name".
$CAT2 ${tgt_file} | split -l "$split_size" -d - split."$tgt_name".


# Step 2: Align each split
echo Creating alignment jobs...
for src_split in `ls split."$src_name".*`
do
    splitnum="${src_split##*.}"
    modeldir=split."$src_lang"-"$tgt_lang".${splitnum}
    mkdir -p $modeldir
    mkdir -p ${modeldir}/data
    cd ${modeldir}/data
    ln -s ../../${src_split} split."$src_lang"
    ln -s ../../split."$tgt_name"."$splitnum" split."$tgt_lang"
    cd ../../
    $mkconf $modeldir ${modeldir}/data $src_lang $tgt_lang < $conf_template > ${modeldir}.conf
    $EXEC $align $MEM ${modeldir}.conf 
done

# Don't need to do symmetrization because the Phrasal phrase extractor now does symmetrization on the fly.
# However, DanC says that using the raw Berkeley outputs (which are an intersection), works well in practice.
