#!/usr/bin/env bash

if [ $# -lt 2 ]; then
  echo Usage: `basename $0` lang file [files]
	echo 
	echo   lang = GERMAN,FRENCH
	exit -1
fi

lang=$1
shift
outfile=corpus."$lang".tok

scriptdir=/u/spenceg/javanlp/projects/mt/ptm/scripts/mt
fixnl="$scriptdir"/fix_cr.py
tokenizer="$scriptdir/tokenizer.pl --datafile=$scriptdir/tokenizer.data"

# Normalize newlines for the current platform, tokenize,
# and lowercase.
cat $* | $fixnl | $tokenizer --language="$lang" | tr A-Z a-z > $outfile

