#!/usr/bin/env bash
#
# Import a CSV file into PostgreSQL
#
if [ $# -ne 4 ]; then
    echo Usage: `basename $0` host dbname dbadmin tbl_name '< csv_file'
    exit -1
fi

# Non-superuser can load a csv file so long as it is
# read from STDIN
# Script will prompt for password
host=$1
db_name=$2
user=$3
tbl_name=$4

cmd='COPY '"$4"' FROM STDIN DELIMITERS '\'','\'' CSV';

psql -h "$host" "$db_name" "$user" -c "$cmd"
