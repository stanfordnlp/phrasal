#!/usr/bin/env bash
#
# Wrapper script for selecting language-specific tokenizers
# and segmenters. Can also apply various helpful normalizations
# (heuristic cleaning, lowercasing, etc.)
#
# Author: Spence Green
# Changes: 
#   Thang Sep13: allow to specify outFile, decide to gzip or not, and update tokenization for English.
#   Thang Oct13: support Chinese

if [ $# -lt 5 ]; then
    echo "Usage: `basename $0` language inFile outFile isGzip isIBMPostProcessing [clean|tolower]"
    echo
    echo "  clean    : Heuristic data cleaning"
    echo "  tolower  : Convert to lowercase" 
    echo "  language : Arabic, Chinese, English, German, French"
    exit -1
fi

lang=$1
infile=$2
outfile=$3
isGzip=$4
isIBMPostProcessing=$5
shift 5

gzipCmd=""
if [ $isGzip -eq 1 ]; then
  gzipCmd="| gzip -c"
fi

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
SCRIPT_PRIVATE_DIR=$JAVANLP_HOME/projects/mt/scripts-private

# Arabic word segmenter setup
AR_MODEL=/scr/spenceg/atb-lex/1-Raw-All.utf8.txt.model.gz
AR_TOK="java $JAVA_OPTS -Xmx6g -Xms6g edu.stanford.nlp.international.arabic.process.ArabicSegmenter -loadClassifier $AR_MODEL -prefixMarker # -suffixMarker + -nthreads 4"

# English tokenizer setup
EN_TOK="java $JAVA_OPTS edu.stanford.nlp.process.PTBTokenizer -preserveLines -options ptb3Escaping=false,asciiQuotes=true,splitAssimilations=false" # Thang: shorten options and add splitAssimilations as Chris suggested. 

# French tokenizer setup
FR_TOK="java $JAVA_OPTS edu.stanford.nlp.international.french.process.FrenchTokenizer -noSGML -options ptb3Escaping=false,ptb3Dashes=false"

# German segmentation and tokenization setup
DE_TOK="java $JAVA_OPTS edu.stanford.nlp.process.PTBTokenizer -preserveLines -options ptb3Escaping=false,ptb3Dashes=false,americanize=false,latexQuotes=false,asciiQuotes=true"

# Chinese tokenizer setup
ZH_TOK="java $JAVA_OPTS -cp $CLASSPATH:/u/nlp/data/StanfordCoreNLPModels/stanford-chinese-corenlp-models-current.jar -mx4g edu.stanford.nlp.ie.crf.CRFClassifier -sighanCorporaDict edu/stanford/nlp/models/segmenter/chinese -inputEncoding UTF-8 -sighanPostProcessing true -keepAllWhitespaces false -loadClassifier edu/stanford/nlp/models/segmenter/chinese/ctb.gz -serDictionary edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz -keepEnglishWhitespaces true -multiThreadClassifier 4"

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
	fixnl="python2.7 $SCRIPT_PRIVATE_DIR/cleanup_txt.py --sgml --sql"
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
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $AR_TOK | $tolower $gzipCmd > ${outfile}

elif [ $lang == "French" ]; then
    if [ "$fixnl" != "tee" ]; then
	fixnl="$fixnl --latin"
    fi
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $FR_TOK $gzipCmd > ${outfile}

elif [ $lang == "German" ]; then
    if [ "$fixnl" != "tee" ]; then
	fixnl="$fixnl --latin"
    fi
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $DE_TOK | $tolower $gzipCmd > ${outfile}
# WMT2013 command (with compound splitting)
#$CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $DE_TOK | $DE_SEG | $DE_PP | $tolower $gzipCmd > ${outfile}

# Thang Oct13: add Chinese
elif [ $lang == "Chinese" ]; then
  $ZH_TOK -textFile $infile $gzipCmd > ${outfile}

elif [ $lang == "English" ]; then
    if [ "$fixnl" != "tee" ]; then
	fixnl="$fixnl --latin"
    fi
    
    $CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $EN_TOK > ${outfile}.notPostprocess

    # post processing
    if [ $isIBMPostProcessing -eq 1 ]; then
      $SCRIPT_PRIVATE_DIR/bolt_en_postprocess.py ${outfile}.notPostprocess $SCRIPT_PRIVATE_DIR/token.map ${outfile}
      rm ${outfile}.notPostprocess
    else
      mv ${outfile}.notPostprocess ${outfile}
    fi

    if [ $isGzip -eq 1 ]; then
      gzip ${outfile}
    fi
fi
