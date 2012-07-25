#!/usr/bin/env bash
#
# Tokenize English and Arabic using appropriate
# packages from Stanford JavaNLP.
#

if [ $# -lt 2 ]; then
    echo Usage: `basename $0` language file [files]
    echo
    echo lang = Arabic,English
    exit -1
fi

lang=$1
shift

# Whitespace and newline normalizer
# Do this externally to guard against any differences between
# the underlying Flex tokenizers.
scriptdir=/u/spenceg/javanlp/projects/mt/ptm/scripts/mt
fixnl="$scriptdir"/fix_cr.py

# Arabic word segmenter setup
AR_MODEL=/scr/spenceg/atb-lex/1-Raw-All.utf8.txt.model.gz
AR_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g -Xmx6g -Xms6g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier $AR_MODEL -prefixMarker # -suffixMarker + -nthreads 4" 

# English tokenizer setup
EN_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.process.PTBTokenizer -preserveLines -lowerCase -options ptb3Escaping=false,asciiQuotes=true"

# Lowercase ASCII text that appears in the Arabic data.
if [ $lang == "Arabic" ]; then
    for infile in $*
    do
	outfile=`basename "$infile"`
	cat $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $AR_TOK | tr A-Z a-z > ${outfile}.tok 
    done
    
elif [ $lang == "English" ]; then
    for infile in $*
    do
	outfile=`basename "$infile"`
	cat $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $EN_TOK > ${outfile}.tok
    done
fi

