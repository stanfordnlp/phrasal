#!/usr/bin/env bash
#
# List of the scripts that need to be executed
# to configure a (Postgres) database from scratch.
#

# Database parameters
# TODO: Change as needed.
dbhost=localhost
dbname=djangodb
dbadmin=django_admin

# Paths to various files
# TODO: Change these paths as needed
data_dir=/home/rayder441/sandbox/javanlp/projects/mt/ptm/data
script_dir=/home/rayder441/sandbox/javanlp/projects/mt/ptm/scripts

# Setup the default tables
echo Installing default tables with SQL script...
./run_sql_script.sh "$dbhost" "$dbname" "$dbadmin" "$script_dir"/sql/default_db.sql

# Load the country list
echo Installing the list of countries...
country_file="$data_dir"/country-list.csv
./csv_to_postgres.sh "$dbhost" "$dbname" "$dbadmin" tm_country < "$country_file"

echo Loading the training documents...

# Load the training documents
# tr interface (src: en)
./tsv_to_sql_csv.py 1 1 1 "$data_dir"/en/proc/training.tsv
./csv_to_postgres.sh  "$dbhost" "$dbname" "$dbadmin" tm_sourcetxt < training.tsv.csv
seq_start=`wc -l training.tsv.csv | awk '{print $1}'`

# meedan interface (src: en)
./tsv_to_sql_csv.py 4 1 2 "$data_dir"/en/proc/training.tsv
./csv_to_postgres.sh  "$dbhost" "$dbname" "$dbadmin" tm_sourcetxt < training.tsv.csv
n=`wc -l training.tsv.csv | awk '{print $1}'`
let seq_start="$seq_start + $n"

echo Altering the SourceTxt table sequence counter for the training docs...
# This statement should re-start the sourcetxt counter after
# the number of sentences that have been incremented
let seq_start="$seq_start + 1"
cmd='ALTER SEQUENCE tm_sourcetxt_id_seq RESTART WITH '"$seq_start"';'
psql -h "$dbhost" "$dbname" "$dbadmin" -c "$cmd"
