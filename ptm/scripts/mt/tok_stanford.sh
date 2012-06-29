#!/usr/bin/env bash
#
# Tokenize English and Arabic using appropriate
# packages from JavaNLP
#
# TODO: update AR_MODEL this with the new segmenter model
#

if [ $# -ne 2 ]; then
	echo Usage: `basename $0` language file
	echo
	echo lang = Arabic,English
	exit -1
fi

lang=$1
textfile=$2

# Arabic word segmenter setup
AR_MODEL=/scr/spenceg/arsegmenter/model.ser.gz

AR_TOK="java edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier=$AR_MODEL -prefixMarker='#' -suffixMarker='+'" 

# English tokenizer setup
EN_TOK="java edu.stanford.nlp.process.PTBTokenizer -preserveLines -options ptb3Escaping=false,asciiQuotes=true"

if [ $lang == "Arabic" ]; then
	$AR_TOK < $textfile

elif [ $lang == "English" ]; then
  $EN_TOK $textfile
fi

