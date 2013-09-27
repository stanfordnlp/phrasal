#!/usr/bin/env bash
#
# Wrapper script for selecting language-specific tokenizers
# and segmenters. Can also apply various helpful normalizations
# (heuristic cleaning, lowercasing, etc.)
#
# TODO(spenceg) Add Chinese
#
# Author: Spence Green
#
#
if [ $# -lt 2 ]; then
    echo "Usage: `basename $0` language file [clean|tolower]"
    echo
    echo "  clean    : Heuristic data cleaning"
    echo "  tolower  : Convert to lowercase" 
    echo "  language : Arabic, English, German, French"
    exit -1
fi

lang=$1
infile=$2
shift 2

outfile=`basename $infile`.tok

# Detect file encoding
ext="${infile##*.}"
CAT=cat
if [ $ext == "gz" ]; then 
    CAT=zcat
elif [ $ext == "bz2" ]; then
    CAT=bzcat
fi

# Path to cdec on the cluster
# Currently runs on CentOS 6 boxes only
CDEC_PATH=/u/nlp/packages/cdec

JAVA_OPTS="-server -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:PermSize=256m"

# Arabic word segmenter setup
AR_MODEL=/scr/spenceg/atb-lex/1-Raw-All.utf8.txt.model.gz
AR_TOK="java $JAVA_OPTS -Xmx6g -Xms6g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier $AR_MODEL -prefixMarker # -suffixMarker + -nthreads 4"

# English tokenizer setup
EN_TOK="java $JAVA_OPTS edu.stanford.nlp.process.PTBTokenizer -preserveLines -options ptb3Escaping=false,asciiQuotes=true,splitAssimilations=false"

# French tokenizer setup
FR_TOK="java $JAVA_OPTS edu.stanford.nlp.international.french.process.FrenchTokenizer -noSGML -options ptb3Escaping=false,ptb3Dashes=false"

# German segmentation and tokenization setup
DE_TOK="java $JAVA_OPTS edu.stanford.nlp.process.PTBTokenizer -preserveLines -options ptb3Escaping=false,asciiQuotes=true,splitAssimilations=false"

# spenceg[aug.2013] Segmentation was used in WMT2013, but German people
# at ACL suggested that compound splitting is only good for De-En, not
# for En-De.
DE_SEG="${CDEC_PATH}/compound-split/compound-split.pl"
DE_PP="java $JAVA_OPTS edu.stanford.nlp.util.Lattice"

#
# Process command line options
#
fixnl=tee
tolower=tee
for op in $*; do
    if [ $op == "clean" ]; then
	fixnl="python2.7 $JAVANLP_HOME/projects/mt/scripts-private/cleanup_txt.py --sgml --sql"
    elif [ $op == "tolower" ]; then
	EN_TOK="$EN_TOK -lowerCase"
	FR_TOK="$FR_TOK -lowerCase"
	tolower=tolower-utf8.py
    fi
done

#
# Run the tokenizers for each language
#
if [ $lang == "Arabic" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $AR_TOK | $tolower | gzip -c > ${outfile}.gz

elif [ $lang == "French" ]; then
    if [ "$fixnl" != "tee" ]; then
	fixnl="$fixnl --latin"
    fi
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $FR_TOK | gzip -c > ${outfile}.gz

elif [ $lang == "German" ]; then
    if [ "$fixnl" != "tee" ]; then
	fixnl="$fixnl --latin"
    fi
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $DE_TOK | $tolower | gzip -c > ${outfile}.gz
# WMT2013 command (with compound splitting)
#$CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $DE_TOK | $DE_SEG | $DE_PP | $tolower | gzip -c > ${outfile}.gz
    
elif [ $lang == "English" ]; then
    if [ "$fixnl" != "tee" ]; then
	fixnl="$fixnl --latin"
    fi
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $EN_TOK | gzip -c > ${outfile}.gz
fi

