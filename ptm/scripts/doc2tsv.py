#!/usr/bin/env python
#
# Converts a textfile to tmapp tsv format.
#
# Assumes utf-8 encoding.
#
import sys
import codecs
from os.path import basename,splitext

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

args = sys.argv[1:]
if len(args) != 1:
    sys.stderr.write('Usage: python doc2tsv.py filename > filename.tsv\n')
    sys.exit(-1)

fname,fext = splitext(basename(args[0]))
infile = codecs.open(args[0],encoding='utf-8')
i_seg = 1
for line in infile:
    print '%s\t%d\t%s' % (fname,i_seg,line.strip())
    i_seg += 1

