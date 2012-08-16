#!/usr/bin/env bash
#
# Create a task from a task specification.
#
if [ $# -ne 1 ]; then
    echo Usage: `basename $0` task_specs
    exit -1
fi

classpath=${JAVANLP_HOME}/projects/mt/ptm/maise/lib

java -cp "$classpath" CreateServerInfo $1

