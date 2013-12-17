#!/usr/bin/env python
#
# Extract genre information for each segment
# in a NIST source SGML file.
#
# Requires package: beautifulsoup4
#
# Author: Spence Green
#
import sys
import codecs
from bs4 import BeautifulSoup
import os
from os.path import basename

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

GENRE_MAP = {'newsgroup' : 'ng', 'newswire': 'nw', 'broadcast_news' : 'bn'}

args = sys.argv[1:]
if len(args) < 1:
    sys.stderr.write('Usage: python %s in_file [out_file] > genres%s' % (basename(sys.argv[0]), os.linesep))
    sys.exit(-1)

soup = BeautifulSoup(codecs.open(args[0],encoding='utf-8'))
out_file = codecs.open(args[1], 'w', encoding='utf-8') if len(args) == 2 else None
n_segments = 0
genre_dict = {}
for doc in soup.find_all('doc'):
    genre = doc['genre']
    if genre in GENRE_MAP:
        genre = GENRE_MAP[genre]
    genre_dict[genre] = 1
    segment_list = doc.find_all('seg')
    for line in segment_list:
        n_segments += 1
        print genre
        if out_file:
            out_file.write(line.get_text().strip() + os.linesep)
if out_file:
    out_file.close()
sys.stderr.write('#segments: %d%s' % (n_segments, os.linesep))
sys.stderr.write('genres: %s%s' % (','.join(genre_dict.keys()),
                                   os.linesep))

