#!/usr/bin/env bash
#
# Wrapper script for selecting language-specific tokenizers
# and segmenters. Also applies various helpful normalizations
# (lowercasing, control character stripping, newline normalization)
#
# Author: Spence Green
#
#
if [ $# -ne 3 ]; then
    echo "Usage: `basename $0` language file [clean|noclean]"
    echo
    echo lang = Arabic,English,German,French
    exit -1
fi

lang=$1
infile=$2
filter=$3
outfile=`basename $infile`.tok

# Detect file encoding
ext="${infile##*.}"
CAT=cat
if [ $ext == "gz" ]; then 
    CAT=zcat
elif [ $ext == "bz2" ]; then
    CAT=bzcat
fi

# Path to cdec installation
CDEC_PATH=/home/rayder441/sandbox/cdec/

# Whitespace and newline normalizer
# Do this externally to guard against any differences between
# the underlying tokenizers.
SCRIPT_DIR=${JAVANLP_HOME}/projects/mt/scripts-private

if [ $filter == "clean" ]; then
    fixnl=${SCRIPT_DIR}/cleanup_txt.py
else
    fixnl=tee
fi

# Arabic word segmenter setup
AR_MODEL=/scr/spenceg/atb-lex/1-Raw-All.utf8.txt.model.gz
AR_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g -Xmx6g -Xms6g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier $AR_MODEL -prefixMarker # -suffixMarker + -nthreads 4" 

# English tokenizer setup
EN_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.process.PTBTokenizer -preserveLines -lowerCase -options ptb3Escaping=false,asciiQuotes=true"

# French tokenizer setup
FR_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.international.french.process.FrenchTokenizer -lowerCase -noSGML"

# German segmentation and tokenization setup
DE_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.process.PTBTokenizer -preserveLines -options ptb3Escaping=false,asciiQuotes=true"
DE_SEG="${CDEC_PATH}/compound-split/compound-split.pl"
DE_PP="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.util.Lattice"

#
# Run the tokenizers for each language
#
if [ $lang == "Arabic" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $AR_TOK | tr [:upper:] [:lower:] | gzip -c > ${outfile}.gz

elif [ $lang == "French" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $FR_TOK | gzip -c > ${outfile}.gz

elif [ $lang == "German" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $DE_TOK | $DE_SEG | $DE_PP | gzip -c > ${outfile}.gz
    
elif [ $lang == "English" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $EN_TOK | gzip -c > ${outfile}.gz
fi

