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
# En documents
./load_source_doc.sh "$data_dir"/en/proc/training.txt 1 1

echo Done with default database setup!

