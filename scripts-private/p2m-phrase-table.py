#!/usr/bin/env python
#
# Convert Phrasal phrase table format to the new
# Moses format.
#
# Author: Spence Green
#
import sys
import codecs
import math
from os.path import basename
import os

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

args = sys.argv[1:]
lo = False
if len(args) == 1:
    if args[0] == '-lo':
        lo = True
    else:
        sys.stderr.write('Usage: python %s [-lo|-h] < file > file%s' % (basename(sys.argv[0]), os.linesep))
        sys.exit(-1)

for line in sys.stdin:
    src = None
    tgt = None
    scores = None
    if lo:
        (src,tgt,scores) = line.strip().split('|||')
    else:
        (src,tgt,_,_,scores) = line.strip().split('|||')
    scores = [str(math.exp(float(x))) for x in scores.split()]
    scores = ' '.join(scores)
    print '%s ||| %s ||| %s' % (src.strip(),tgt.strip(),scores)
