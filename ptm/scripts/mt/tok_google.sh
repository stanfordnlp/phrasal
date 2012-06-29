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

cat $* > merged.tmp
$tokenizer --language="$lang" < merged.tmp > merged.tok
gzip -c merged.tok > corpus.preproc.gz

rm -f merged.*

