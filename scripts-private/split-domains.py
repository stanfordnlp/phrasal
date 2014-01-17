#!/usr/bin/env python
#
# Partition a file according to per-segment
# domain information, which is usually generated
# from NIST files via nist-domains.py.
#
# Author: Spence Green
#
import sys
import codecs
import os
from os.path import basename

args = sys.argv[1:]
if len(args) != 2:
    sys.stderr.write('Usage: python %s file genre_list%s' % (basename(sys.argv[0]),
                                                             os.linesep))
    sys.exit(-1)

with codecs.open(args[0],encoding='utf-8') as in_file:
    with open(args[1]) as genre_file:
        genre_list = [x.strip() for x in genre_file.readlines()]
        genre_set = set(genre_list)
        genre_files = {}
        genre_counts = {}
        for genre in genre_set:
            fd = codecs.open(genre + '.txt','w',encoding='utf-8')
            genre_files[genre] = fd
            genre_counts[genre] = 0
        for i,line in enumerate(in_file):
            genre = genre_list[i]
            genre_counts[genre] += 1
            genre_files[genre].write(line.strip() + os.linesep)
        for genre,fd in genre_files.iteritems():
            sys.stderr.write('%s: %d segments%s' % (genre,genre_counts[genre],
                                                    os.linesep))
            fd.close()

