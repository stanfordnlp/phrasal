#!/usr/bin/env python
#
# Convert the output of
# edu.stanford.nlp.mt.tools.MinimumBayesRisk
# to a plain text file.
#
# Author: Spence Green
#
#
import sys
import codecs

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

last_id = -1
for line in sys.stdin:
    parts = line.strip().split('|||')
    sid = int(parts[0])
    if not sid == last_id:
        print parts[1].strip()
        last_id = sid
