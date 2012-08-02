#!/usr/bin/env python
#
# Filters an srilm counts file after it has been tokenized. 
#
import sys
import codecs
import os
from os.path import basename

sys.stdout = codecs.getwriter('utf-8')(sys.stdin)

prog = basename(sys.argv[0])
args = sys.argv[1:]
if len(args) != 2:
    sys.stderr.write('Usage: python %s order counts_file%s' % (prog, os.linesep))
    sys.exit(-1)

lm_order = int(args[0])
fname = args[1]
    
with codecs.open(fname, encoding='utf-8') as infile:
    for line in infile:
        (ngram,count) = line.strip().split('\t')
        if len(ngram.split()) <= lm_order:
            print '\t'.join(ngram,count)

