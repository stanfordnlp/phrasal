#!/usr/bin/env bash

if [ $# -ne 2 ]; then
	echo Usage: `basename $0` "[up|clean|get]" upload_file
	exit -1
fi

hit_file=$2
batch="${hit_file%.*}"

if [ $1 == "up" ]; then
    ant uploader -Dfile="$hit_file"

elif [ $1 == "clean" ]; then
    ant cleaner -DdelAssignable=true -DdelCompleted=true -Dfield=keywords -Dquery="$batch"

elif [ $1 == "get" ]; then
    ant retriever -Danswers="$batch".answers.log -Dfield=keywords -Dquery="$batch"
fi
