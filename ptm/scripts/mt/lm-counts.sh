#!/usr/bin/env bash
#
# Step 1 of LM training.
#
# Generates counts files for a list of input files
#
# Uses the cluster to generate the files
#
#
if [ $# -lt 1 ]; then
    echo "Usage: `basename $0` file [files]"
    exit -1
fi

# Directories for logs and intermediate files
COUNTS_DIR=counts
mkdir -p ${COUNTS_DIR}

# Configure ngram-counts in the local file
source lm.local

for txtfile in $*
do
	outprefix=`basename $txtfile`
	echo nlpsub -m15g $COUNT $LMOPTS -text "$txtfile" -write ${COUNTS_DIR}/"$outprefix".counts.gz -sort
done
