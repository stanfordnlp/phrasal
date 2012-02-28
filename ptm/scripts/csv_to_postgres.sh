#!/usr/bin/env bash
#
# Import a CSV file into PostgreSQL
#
if [ $# -ne 1 ]; then
    echo Usage: `basename $0` tbl_name '< csv_file'
    exit -1
fi

# Non-superuser can load a csv file so long as it is
# read from STDIN
# Script will prompt for password
host=localhost
db_name=ptm_django
user=django_admin

cmd='COPY '"$1"' FROM STDIN DELIMITERS '\'','\'' CSV';

psql -h "$host" "$db_name" "$user" -c "$cmd"
