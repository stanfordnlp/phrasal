#!/usr/bin/env python
#
# Removes lines shorter than <min_chars> characters from
# a bitext.
#
# Assumes utf-8 encoding.
#
# Author: Spence Green
#
import sys
import codecs
import os
from os.path import basename

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

args = sys.argv[1:]
if len(args) != 3:
    sys.stderr.write('Usage: python %s min_chars file1 file2 < file%s' % (basename(sys.argv[0]), os.linesep))
    sys.exit(-1)

min_chars = int(args[0])
filename1 = args[1]
out1 = basename(filename1) + '.out'
filename2 = args[2]
out2 = basename(filename2) + '.out'

n_lines = 0
n_filtered = 0
with codecs.open(filename1, encoding='utf-8') as infile1:
    with codecs.open(filename2, encoding='utf-8') as infile2:
        with codecs.open(out1, 'w', encoding='utf-8') as outfile1:
            with codecs.open(out2, 'w', encoding='utf-8') as outfile2:
                for i,line1 in enumerate(infile1):
                    line2 = infile2.readline()
                    if line2:
                        n_lines += 1
                        line1 = line1.strip()
                        line2 = line2.strip()
                        if len(line1) > min_chars and len(line2) > min_chars:
                            outfile1.write(line1 + os.linesep)
                            outfile2.write(line2 + os.linesep)
                        else:
                            n_filtered += 1
                    else:
                        sys.stderr.write('file2 exhausted after %d lines%s' % (i, os.linesep))
                        sys.exit(-1)
                # Ensure that infile2 is exhausted
                if infile2.readline():
                    sys.stderr.write('file1 exhausted after %d lines)%s' % (i, os.linesep))
                    sys.exit(-1)

print 'Filtered %d / %d lines' % (n_filtered, n_lines)

