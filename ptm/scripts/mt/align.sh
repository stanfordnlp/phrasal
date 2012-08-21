#!/usr/bin/env bash
#
# Splits and aligns a bitext using
# Berkeley aligner 2.1 and the Phrasal implementation
# of symmetrization heuristics.
#
if [ $# -ne 3 ]; then
    echo Usage: `basename $0` src_file_gz tgt_file_gz lines_per_split
    echo
    echo "NOTE: Expects that the bitext is gzip'd."
    exit -1
fi

# Uncomment the line below to run on the cluster
MEM=15g
#EXEC=
EXEC="nlpsub -m${MEM} -c4"

src_file=$1
tgt_file=$2
split_size=$3

scriptdir=${JAVANLP_HOME}/projects/mt/ptm/scripts/mt
mkconf=${scriptdir}/mkconf.py
conf_template=${scriptdir}/ucb-align.conf
align=${JAVANLP_HOME}/projects/mt/scripts/align

filename=`basename "$src_file"`
src_ext="${filename##*.}"
#filename="${filename%.*}"

filename=`basename "$tgt_file"`
tgt_ext="${filename##*.}"
#filename="${filename%.*}"

# Step 1: Split the bitext
zcat ${src_file} | split -l "$split_size" -d - split."$src_ext".
zcat ${tgt_file} | split -l "$split_size" -d - split."$tgt_ext".

# Step 2: Align each split
for src_split in `ls split."$src_ext".*`
do
    splitnum="${src_split##*.}"
    modeldir=split.${splitnum}
    mkdir $modeldir
    mkdir ${modeldir}/data
    cd ${modeldir}/data
    ln -s ../../${src_split} split."$src_ext"
    ln -s ../../split."$tgt_ext"."$splitnum" split."$tgt_ext"
    cd ../../
    $mkconf $modeldir ${modeldir}/data $src_ext $tgt_ext < $conf_template > ${modeldir}.conf
    $EXEC $align $MEM ${modeldir}.conf 
done

# Don't need to do symmetrization because the Phrasal phrase extractor now does symmetrization on the fly.
# However, DanC says that using the raw Berkeley outputs (which are an intersection), works well in practice.
