#!/usr/bin/env bash
#
# Apply re-casing followed by detokenization.
#
# Author: Spence Green, Julia Neidert
#

if [ $# -ne 1 ]; then
    echo Usage: $(basename $0) file
    exit -1
fi

infile=$1

# De-compounding
#cat $infile | de-rules.py > $infile.decompound

# Truecasing using part of speech tag rules (all nouns uppercase)
de_recase_by_POS_tag.pl $infile > $infile.pos_cased 

# Truecasing using language model
recase.sh German $infile > $infile.hmm_cased

# Merge truecasings by uppercasing any letter either method uppercased
merge_casings.py $infile.hmm_cased $infile.pos_cased > $infile.cased

# Detokenization
cat $infile.cased | en_detokenizer | de-post.py > $infile.postproc

rm -f $infile.{pos_cased,pos,hmm_cased,cased}
