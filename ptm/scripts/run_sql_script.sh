#!/usr/bin/env bash
#
# Executes a script on the django database.
#
# Author: Spence
#
if [ $# -ne 1 ]; then
    echo Usage: `basename $0` sql_file
    exit -1
fi

host=localhost
db_name=ptm_django
user=django_admin

psql -h "$host" -f $1 "$db_name" "$user"

