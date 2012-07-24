#!/usr/bin/env python
#
# Normalizes EOL character for the current platform.
#
import sys
import re
import codecs

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

# Match various forms of whitespace. Normalize to
# a single ASCII space character.
p_ws = re.compile(ur'[ \t\u00A0\u2000-\u200A\u3000]+')

for line in sys.stdin:
    line = p_ws.sub(' ', line.strip())
    print line

