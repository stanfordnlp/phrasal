#!/usr/bin/env python
#
# Cleans weird stuff from bitexts. Includes:
#
#   1. EOL normalization for the current platform
#   2. Whitespace normalization
#   3. Cleaning of *ML tags
#
import sys
import re
import codecs

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

# Match various forms of whitespace. Normalize to
# a single ASCII space character.
p_ws = re.compile(ur'[ \t\u00A0\u2000-\u200A\u3000]+')

# Remove html tags
p_tag = re.compile(r'<[^<>]*>')

for line in sys.stdin:
    line = p_tag.sub('', line.strip())
    line = p_ws.sub(' ', line)
    print line

