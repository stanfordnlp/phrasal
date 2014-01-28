#!/usr/bin/env bash
#
# Executes a script on the django sqlite3 database and pipes the output to the given file
#
# Author: Spence, Colin
#

if [ $# -ne 3 ]; then
    echo Usage: `basename $0` dbname sql_file outputfile
    exit -1
fi

db_name=$1
sql_file=$2
output_file=$3

sqlite3 "$db_name" < "$sql_file" > "$output_file"

