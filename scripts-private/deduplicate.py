#!/usr/bin/env python
#
# Simple deduplication.
#
# Author: Spence Green
#
import sys
import codecs

sys.stdin = codecs.getreader(encoding='utf-8')(sys.stdin)
sys.stdout = codecs.getwriter(encoding='utf-8')(sys.stdout)

dup_hashes = set()
for line in sys.stdin:
    line = line.strip()
    item_key = hash(line)
    if item_key in dup_hashes:
        continue
    dup_hashes.add(item_key)
    print line
