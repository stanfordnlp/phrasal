#!/usr/bin/env python
#
# Get a database log dump and extract out the log json object
#

import sys
import json

# Cols in the source dump file
SrcInputCols = ["id",
                "user_id",
                "src_document_id",
                "tgt_language_id",
                "interface",
                "order",
                "create_time",
                "start_time",
                "end_time",
                "complete",
                "training",
                "text",
                "log"]

# Read in a row from the file
dbdump = open(sys.argv[1])
row = dbdump.readline().split('|')
row = dbdump.readline().split('|')

# Parse the log
log = json.loads(row[SrcInputCols.index("log")])

# Get a list of elements touched at each time
print "Start time: " + str(log[0]['keyValues']['referenceTime'])
for entry in log:
    if entry['subElement'] == '0':
        print entry['element']+":"+entry['subElement']+":"+str(entry['time'])
        if entry['element'] == 'targetSuggestions':
            print entry['keyValues']
        if entry['element'] == 'targetBoxes':
            print entry['keyValues']
