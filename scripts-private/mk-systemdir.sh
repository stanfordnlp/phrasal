#!/usr/bin/env bash

if [ $# -ne 1 ]; then
  echo Usage: `basename $0` dir_name
	exit -1
fi

dir_name=$1

mkdir $dir_name
mkdir $dir_name/align
mkdir $dir_name/corpora
mkdir $dir_name/translate
mkdir $dir_name/lm
mkdir $dir_name/wordcls
mkdir $dir_name/postproc
mkdir $dir_name/corpora/bitext
mkdir $dir_name/corpora/bitext_preproc
mkdir $dir_name/corpora/mono
mkdir $dir_name/corpora/mono_preproc
