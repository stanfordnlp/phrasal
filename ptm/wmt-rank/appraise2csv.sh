#!/usr/bin/env bash

PTM_RAW=ptm-all.csv

head -n 1 $PTM_RAW > ptm-ende.csv
grep German $PTM_RAW >> ptm-ende.csv
./wmtformat.py ptm-ende.csv | perl -pe 's/uist14ende\.en\-de\.//g;' > ende.preproc

head -n 1 $PTM_RAW > ptm-fren.csv
grep French $PTM_RAW >> ptm-fren.csv

#
# Step 1: Lopez et al. 2013 analysis.
#
