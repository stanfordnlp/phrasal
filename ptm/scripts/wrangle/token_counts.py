#!/usr/bin/env python
import sys
import codecs
from collections import Counter

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

vocab = Counter()
for line in sys.stdin:
    tokens = line.strip().split()
    for token in tokens:
        vocab[token] += 1

for tok,count in vocab.iteritems():
    print '%s\t%d' % (tok,count)
