#!/usr/bin/env bash
#
# List of the scripts that need to be executed
# to configure a (Postgres) database from scratch.
#
# TODO: You might need to change the paths depending
# on your working copy.
#

# Database parameters
dbhost=localhost
dbname=ptm_django
dbadmin=django_admin

# Paths to various files
# TODO: Change these paths as needed
data_dir=/home/rayder441/sandbox/javanlp/projects/mt/ptm/data
script_dir=/home/rayder441/sandbox/javanlp/projects/mt/ptm/scripts

# Setup the default tables
./run_sql_script.sh "$script_dir"/sql/default_db.sql

# Load the country list
country_file="$data_dir"/country-list.csv
./csv_to_postgres.sh tm_country < "$country_file"

# Load the training documents
# tr interface (src: en)
./tsv_to_sql_csv.py 1 1 1 "$data_dir"/en/proc/training.tsv
./csv_to_postgres.sh tm_sourcetxt < training.tsv.csv

# meedan interface (src: en)
./tsv_to_sql_csv.py 4 1 2 "$data_dir"/en/proc/training.tsv
./csv_to_postgres.sh tm_sourcetxt < training.tsv.csv

# This statement should re-start the sourcetxt counter after
# the number of sentences that have been incremented
cmd='ALTER SEQUENCE tm_sourcetxt_id_seq RESTART WITH 7;'
psql -h "$host" "$dbname" "$dbadmin" -c "$cmd"