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

# Concatenate and lowercase
MERGED=merged."$lang"

# Arabic word segmenter setup
AR_MODEL=/scr/spenceg/atb-lex/1-Raw-All.utf8.txt.model.gz
AR_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g -Xmx3g -Xms3g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier $AR_MODEL -prefixMarker # -suffixMarker +" 

# English tokenizer setup
EN_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.process.PTBTokenizer -preserveLines -options ptb3Escaping=false,asciiQuotes=true"


if [ $lang == "Arabic" ]; then
	cat $@ | $AR_TOK > "$MERGED".tok 

elif [ $lang == "English" ]; then
	cat $@ > | tr A-Z a-z > $MERGED 
	$EN_TOK $MERGED > "$MERGED".tok
fi

# Cleanup...
gzip -c "$MERGED".tok > corpus.preproc."$lang".gz
rm -f "$MERGED"*

