#!/usr/bin/env bash
#
# Tokenize English and Arabic using appropriate
# packages from JavaNLP
#

if [ $# -lt 2 ]; then
	echo Usage: `basename $0` language file [files]
	echo
	echo lang = Arabic,English
	exit -1
fi

lang=$1
shift
outfile=corpus.tok

# Arabic word segmenter setup
AR_MODEL=/scr/spenceg/atb-lex/1-Raw-All.utf8.txt.model.gz
AR_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g -Xmx6g -Xms6g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier $AR_MODEL -prefixMarker # -suffixMarker + -nthreads 4" 

# English tokenizer setup
EN_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.process.PTBTokenizer -preserveLines -lowerCase -options ptb3Escaping=false,asciiQuotes=true"

# Choose language-specific tokenizer
# These tokenizers perform newline normalization internally.
if [ $lang == "Arabic" ]; then
	# Lowercase ASCII text that appears in the Arabic data.
	cat $* | $AR_TOK | tr A-Z a-z > "$outfile" 

elif [ $lang == "English" ]; then
	cat $* | $EN_TOK > "$outfile"
fi

