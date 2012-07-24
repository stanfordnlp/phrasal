#!/usr/bin/env python
#
# Normalizes EOL character for the current platform.
#
import sys
import codecs

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

for line in sys.stdin:
    print line.strip()

