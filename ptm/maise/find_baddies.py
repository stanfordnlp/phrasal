#!/usr/bin/env python
import sys
import csv
from collections import namedtuple


AnswerRow = namedtuple('AnswerRow', 'srclang,trglang,srcIndex,documentId,segmentId,judgeId,system1Number,system1Id,system2Number,system2Id,rank')


args = sys.argv[1:]
id = args[0]
ans_filename = args[1]

with open(ans_filename) as infile:
    r = csv.reader(infile)
    for i,row in enumerate(map(AnswerRow._make, r)):
        if i == 0:
            # Skip header
            continue
        if row.system1Id == id and row.rank == '1':
            print row.judgeId
        elif row.system2Id == id and row.rank == '2':
            print row.judgeId
        elif (row.system1Id == id or row.system2Id == id) and row.rank == '11':
            print row.judgeId
