#!/usr/bin/env python
#
# Get a database log dump and extract out the log json object
#

import sys
import json
import time

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
                "valid",
                "text",
                "log"]

# Read in a row from the file
dbdump = open(sys.argv[1])
row = dbdump.readline().split('|')

# Parse the log
log = json.loads(row[SrcInputCols.index("log")])

# Get a list of elements touched at each time
start_time = log[0]['keyValues']['referenceTime']
print "Start time: " + time.ctime(start_time)

for entry in log:
    #Capture source words
    if entry['element'] == 'ptm' and 'segments' in entry['keyValues']:
        if len(entry['keyValues']['segments']) > 0:
            source = entry['keyValues']['segments']
    #Capture source suggestions
    if entry['element'] == 'sourceSuggestions' and 'targets' in entry['keyValues']:
        if len(entry['keyValues']['targets']) > 0:
            sourceSuggestions = entry['keyValues']['targets']
    #When user hovers over source word
    if entry['element'] == 'sourceBoxes' and 'hoverTokenIndex' in entry['keyValues'] and entry['keyValues']['hoverTokenIndex'] is not None:
        print 'User hovered over source word: ' + source['0']['tokens'][entry['keyValues']['hoverTokenIndex']] + ' at ' +  str(entry['time'])
    #When user hovers over source suggestion
    if entry['element'] == 'sourceSuggestions' and 'optionIndex' in entry['keyValues'] and entry['keyValues']['optionIndex'] is not None:
        print 'User hovered over source suggestion: ' + sourceSuggestions[entry['keyValues']['optionIndex']] + ' at ' +  str(entry['time'])
