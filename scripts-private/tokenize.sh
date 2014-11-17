#!/usr/bin/env bash
#
# Wrapper script for selecting language-specific tokenizers
# and segmenters. Can also apply various helpful normalizations
# (heuristic cleaning, lowercasing, etc.)
#
#
# Author: Spence Green
#   Julia Neidert added segment_de and out_dir command line options Jan 2014, and clean_keep option Feb 2014
#
if [ $# -lt 3 ]; then
    echo "Usage: `basename $0` language in_file out_dir [clean|tolower|segment_de]"
    echo
    echo "  clean      : Heuristic data cleaning and filtering"
    echo "  clean_keep : Heuristic data cleaning, without filtering (you want this for test data)"
    echo "  tolower    : Convert to lowercase" 
    echo "  segment_de : Segment compounds in German"
    echo "  language   : Arabic, Chinese, English, German, French, Spanish"
    exit -1
fi

lang=$1
infile=$2
outfile=${3}/`basename $infile`

shift 3

# Detect file encoding
ext="${infile##*.}"
CAT=cat
if [ $ext == "gz" ]; then 
    CAT=zcat
    outfile="${outfile%.*}"
elif [ $ext == "bz2" ]; then
    CAT=bzcat
    outfile="${outfile%.*}"
fi
outfile=$outfile.tok

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

# Chinese segmenter path, where segment.sh is located
# Should point to latest distribution of the Chinese segmenter
ZH_SEG_PATH="/u/nlp/distrib/stanford-segmenter-2013-11-12"
ZH_SEG="$ZH_SEG_PATH/segment.sh"

# Spanish tokenizer setup (same parameters as French)
ES_TOK="java $JAVA_OPTS edu.stanford.nlp.international.spanish.process.SpanishTokenizer -noSGML -options ptb3Escaping=false,ptb3Dashes=false"

#
# Process command line options
#
fixnl=tee
tolower=tee
DE_SEG=tee
DE_PP=tee
for op in $*; do
    if [ $op == "clean" ]; then
	    fixnl="python2.7 $JAVANLP_HOME/projects/mt/scripts-private/cleanup_txt.py --sgml --sql"
        if [ $lang == "German" ] || [ $lang == "English" ] || [ $lang == "French" ]; then
            fixnl="$fixnl --latin"
        fi    

    elif [ $op == "clean_keep" ]; then
        fixnl="python2.7 $JAVANLP_HOME/projects/mt/scripts-private/cleanup_txt.py"

    elif [ $op == "tolower" ]; then
	EN_TOK="$EN_TOK -lowerCase"
    	FR_TOK="$FR_TOK -lowerCase"
	ES_TOK="%ES_TOK -lowerCase"

	# This applies to other languages
    	tolower=tolower-utf8.py

    elif [ $op == "segment_de" ]; then
        # spenceg[aug.2013] Segmentation was used in WMT2013, but German people
        # at ACL suggested that compound splitting is only good for De-En, not
        # for En-De.
        DE_SEG="${CDEC_PATH}/compound-split/compound-split.pl"
        # Use the line below to mark all segments that aren't the first in a compound with # 
        DE_PP="java $JAVA_OPTS edu.stanford.nlp.mt.tools.Lattice"
        # Use the line below to mark all segments that aren't the last (headword) in a compound with ^|
        #DE_PP="java $JAVA_OPTS edu.stanford.nlp.mt.tools.Lattice -mark_except_last -m^|"
    fi
done

#
# Run the tokenizers for each language
#
if [ $lang == "Arabic" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $AR_TOK | $tolower | gzip -c > ${outfile}.gz

elif [ $lang == "Chinese" ]; then
    $ZH_SEG ctb <($CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl) UTF-8 0 2> /dev/null | $tolower | gzip -c > ${outfile}.gz

elif [ $lang == "French" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $FR_TOK | gzip -c > ${outfile}.gz

elif [ $lang == "German" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $DE_TOK | $DE_SEG | $DE_PP | $tolower | gzip -c > ${outfile}.gz
    
elif [ $lang == "English" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $EN_TOK | gzip -c > ${outfile}.gz

elif [ $lang == "Spanish" ]; then
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $ES_TOK | gzip -c > ${outfile}.gz
fi

