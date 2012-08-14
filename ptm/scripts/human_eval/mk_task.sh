#!/usr/bin/env bash

if [ $# -ne 1 ]; then
	echo Usage: `basename $0` task_specs
	exit -1
fi

java -cp lib/ CreateServerInfo $1

