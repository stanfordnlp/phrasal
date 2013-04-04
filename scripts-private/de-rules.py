#!/usr/bin/env python
# coding: utf-8
#
# Apply German detokenization rules to MT output that
# was tokenized with tokenize.sh
#
# Author: Spence Green
#
import sys
import re
import codecs

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

## TODO: Specific rules
# recht #kräftig => rechtskräftig


# General catch-call rule to be applied at the end
p_all = re.compile(r'(\w) #(\w)', re.U)

for line in sys.stdin:
    # Apply specific rules
    
    # Catch-all rule
    line = p_all.sub(r'\1\2',line)

    print line.strip()
