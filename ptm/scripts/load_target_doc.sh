#!/usr/bin/env bash
#
# Loads a txt document as tmapp.TargetTxt objects
#

if [ $# -ne 4 ]; then
    echo Usage: `basename $0` filename start_pk lang_pk src_pk
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
src_pk=$4

./doc2tsv.py "$fname" > doc.tsv
./tsv_to_targettxt_csv.py "$start_pk" "$src_pk" "$lang_pk" doc.tsv
./csv_to_postgres.sh  "$dbhost" "$dbname" "$dbadmin" tm_targettxt < doc.tsv.csv
n_lines=`wc -l doc.tsv.csv | awk '{print $1}'`

# This statement should re-start the targettxt counter after
# the number of sentences that have been incremented
let seq_start="$start_pk + $n_lines"
cmd='ALTER SEQUENCE tm_targettxt_id_seq RESTART WITH '"$seq_start"';'
psql -h "$dbhost" "$dbname" "$dbadmin" -c "$cmd"

echo Loaded "$n_lines" lines from "$fname" as TargetTxt objects
echo Start loading the next document at pk "$seq_start"
echo Done!
