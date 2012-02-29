#!/usr/bin/env bash
#
# Executes a script on the django database.
#
# Author: Spence
#
if [ $# -ne 4 ]; then
    echo Usage: `basename $0` host dbname dbadmin sql_file
    exit -1
fi

host=$1
db_name=$2
user=$3
sql_file=$4

psql -h "$host" -f "$sql_file" "$db_name" "$user"

