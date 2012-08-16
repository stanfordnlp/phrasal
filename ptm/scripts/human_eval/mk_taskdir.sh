#!/usr/bin/env bash
#
#
#
if [ $# -ne 1 ]; then
    echo Usage: `basename $0` task_name
    exit -1
fi

task=$1

mkdir -p $task
cd $task
mkdir systems
mkdir references
mkdir sources
mkdir output
touch task_specs.txt
touch batch_specs.txt
