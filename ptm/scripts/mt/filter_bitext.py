#!/usr/bin/env python
#
# Removes lines shorter than <min_chars> characters from
# a bitext.
#
# Reads and writes to gzip'd files.
#
# Assumes utf-8 encoding.
#
# Author: Spence Green
#
import sys
import codecs
import os
import gzip
from os.path import basename,splitext

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

def gz_utf8_reader(fname):
    return codecs.getreader('utf-8')(gzip.open(fname))

def gz_utf8_writer(fname):
    return codecs.getwriter('utf-8')(gzip.open(fname, 'w'))

args = sys.argv[1:]
if len(args) != 3:
    sys.stderr.write('Usage: python %s min_chars file1.gz file2.gz%s' % (basename(sys.argv[0]), os.linesep))
    sys.exit(-1)

min_chars = int(args[0])
filename1 = args[1]
prefix1,temp = splitext(basename(filename1))
out1 = prefix1 + '.filtered.gz'

filename2 = args[2]
prefix2,temp = splitext(basename(filename2))
out2 = prefix2 + '.filtered.gz'

n_lines = 0
n_filtered = 0
with gz_utf8_reader(filename1) as infile1:
    with gz_utf8_reader(filename2) as infile2:
        with gz_utf8_writer(out1) as outfile1:
            with gz_utf8_writer(out2) as outfile2:
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

