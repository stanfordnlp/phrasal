#!/usr/bin/env bash
#
# Extracts an unfiltered phrase table using Moses
#
#
# Author: Spence Green
#
MOSES=/u/nlp/packages/moses
TRAIN=$MOSES/scripts/training/train-model.perl 
ALIGN=grow-diag

echo $TRAIN

# Extract ptable:
perl $TRAIN --root-dir . --model-dir . --f ar --e en --corpus corpus --factor-delimiter="|||" --alignment $ALIGN --alignment-file corpus --first-step 4 --last-step 7 --reordering=distance,msd-bidirectional-fe 
