#!/usr/bin/env bash
#
# Wrapper for the Google tokenizer that ships
# with the LDC Google 1T European n-grams corpus.
#
#
if [ $# -lt 2 ]; then
  echo Usage: `basename $0` lang file [files]
	echo 
	echo   lang = GERMAN,FRENCH
	exit -1
fi

lang=$1
shift

scriptdir=${JAVANLP_HOME}/projects/mt/ptm/scripts/mt
fixnl="$scriptdir"/fix_cr.py
tokenizer="$scriptdir/tokenizer.pl --datafile=$scriptdir/tokenizer.data"

# Normalize newlines and whitespace for the current platform,
# strip control characters, tokenize, and lowercase.
for infile in $*
do
    outfile=`basename "$infile"`
    cat $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $tokenizer --language="$lang" | tr A-Z a-z > ${outfile}.tok
done

