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
    sid,segment,score = line.strip().split('|||')
    sid = int(sid)
    if not sid == last_id:
        print segment.strip()
        last_id = sid
