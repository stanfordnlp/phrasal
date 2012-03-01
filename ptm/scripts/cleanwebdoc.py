#!/usr/bin/env python
#
# Strips html from a web document.
#
# Usage: python cleanwebdoc.py doc > output
#
# Assumes utf-8 encoding.
import sys
import codecs
from bs4 import BeautifulSoup

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

args = sys.argv[1:]
if len(args) != 1:
    sys.stderr.write('Usage: python cleanwebdoc.py doc > output\n')
    sys.exit(-1)

infile = codecs.open(args[0],encoding='utf-8')
soup = BeautifulSoup(infile)
print soup.get_text()
infile.close()
