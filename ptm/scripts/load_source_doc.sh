#!/usr/bin/env bash
#
# Loads a txt document as tmapp.SourceTxt objects
#

if [ $# -ne 3 ]; then
    echo Usage: `basename $0` filename start_pk lang_pk
    exit -1
fi

# Database parameters
# TODO: Change as needed.
dbhost=localhost
dbname=djangodb
dbadmin=django_admin

fname=$1
start_pk=$2
lang_pk=$3

./doc2tsv.py "$fname" > doc.tsv
./tsv_to_sql_csv.py "$start_pk" "$lang_pk" doc.tsv
./csv_to_postgres.sh  "$dbhost" "$dbname" "$dbadmin" tm_sourcetxt < doc.tsv.csv
n_lines=`wc -l doc.tsv.csv | awk '{print $1}'`

# This statement should re-start the sourcetxt counter after
# the number of sentences that have been incremented
let seq_start="$start_pk + $n_lines"
cmd='ALTER SEQUENCE tm_sourcetxt_id_seq RESTART WITH '"$seq_start"';'
psql -h "$dbhost" "$dbname" "$dbadmin" -c "$cmd"

echo Loaded "$n_lines" lines from "$fname" as SourceTxt objects
echo Start loading the next document at pk "$seq_start"
echo Done!
