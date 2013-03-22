#!/usr/bin/env python
#
# Randomly shuffle a bitext. This should be run after FDA selection
# to eliminate any artifacts of the data selection procedure.
#
# Author: Spence Green
#
import sys
import codecs
from random import shuffle
import os
from os.path import basename

args = sys.argv[1:]
if len(args) != 3:
    prog = basename(sys.argv[0])
    sys.stderr.write('Usage: %s size file1 file2%s' % (prog,os.linesep))
    sys.exit(-1)
file_size = int(args[0])
lines = [None]*file_size
with codecs.open(args[1],encoding='utf-8') as infile1:
    with codecs.open(args[2],encoding='utf-8') as infile2:
        i = 0
        for line in infile1:
            line2 = infile2.readline()
            lines[i] = (line,line2)
            i += 1

sys.stderr.write('Finished reading input. Shuffling...' + os.linesep)
shuffle(lines)
sys.stderr.write('Writing output.' + os.linesep)

outfile1 = basename(args[1])+'.shuffled'
outfile2 = basename(args[2])+'.shuffled'
with codecs.open(outfile1,'w',encoding='utf-8') as out1:
    with codecs.open(outfile2,'w',encoding='utf-8') as out2:
        for line1,line2 in lines:
            out1.write(line1.strip() + os.linesep)
            out2.write(line2.strip() + os.linesep)
