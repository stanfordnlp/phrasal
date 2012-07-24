#!/usr/bin/env bash

if [ $# -lt 2 ]; then
  echo Usage: `basename $0` lang file [files]
	echo 
	echo   lang = GERMAN,FRENCH
	exit -1
fi

lang=$1
shift

scriptdir=/scr/spenceg/phrasal/google/
tokenizer="$scriptdir/tokenizer.pl --datafile=$scriptdir/tokenizer.data"

cat $* | $tokenizer --language="$lang" | tr A-Z a-z > corpus.tok


