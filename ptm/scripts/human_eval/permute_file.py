#!/usr/bin/env python
#
# Permutes the lines in an input file, failing if
# any line is not shifted from its original index.
#
import sys
import codecs
import random

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

lines = [x.strip() for x in sys.stdin.readlines()]
indices = range(len(lines))
random.shuffle(indices)

for i,j in enumerate(indices):
    assert i != j
    print lines[j]
