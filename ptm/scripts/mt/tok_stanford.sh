#!/usr/bin/env bash
#
# Tokenize English and Arabic using appropriate
# packages from Stanford JavaNLP.
#

if [ $# -lt 2 ]; then
    echo Usage: `basename $0` language file [files]
    echo
    echo lang = Arabic,English,German,French
    exit -1
fi

lang=$1
shift
outfile=corpus."$lang".tok

# Whitespace and newline normalizer
# Do this externally to guard against any differences between
# the underlying Flex tokenizers.
scriptdir=${JAVANLP_HOME}/projects/mt/ptm/scripts/mt
fixnl=${scriptdir}/cleanup_txt.py

# Arabic word segmenter setup
AR_MODEL=/scr/spenceg/atb-lex/1-Raw-All.utf8.txt.model.gz
AR_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g -Xmx6g -Xms6g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier $AR_MODEL -prefixMarker # -suffixMarker + -nthreads 4" 

# English tokenizer setup
EN_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.process.PTBTokenizer -preserveLines -lowerCase -options ptb3Escaping=false,asciiQuotes=true"

# French tokenizer setup
FR_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.international.french.process.FrenchTokenizer -lowerCase -noSGML"

# German segmentation and tokenization setup
DE_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.process.PTBTokenizer -preserveLines -options ptb3Escaping=false,asciiQuotes=true"
DE_SEG="/home/rayder441/sandbox/cdec/compound-split/compound-split.pl"
DE_PP="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.util.Lattice"

# Lowercase ASCII text that appears in the Arabic data.
if [ $lang == "Arabic" ]; then
    cat $* | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $AR_TOK | tr A-Z a-z | gzip -c > ${outfile}.gz

elif [ $lang == "French" ]; then
    cat $* | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $FR_TOK | gzip -c > ${outfile}.gz

elif [ $lang == "German" ]; then
    cat $* | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $DE_TOK | $DE_SEG | $DE_PP | gzip -c > ${outfile}.gz
    
elif [ $lang == "English" ]; then
    cat $* | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $EN_TOK | gzip -c > ${outfile}.gz
fi

