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


# load dictionary
compound_file = codecs.open('/scr/nlp/data/WMT/scripts/compound.dict','r','utf-8')
compound_dict = {}
for line in compound_file:
    pieces = line.split()[0] # comma-separated pieces
    full = line.split()[1]
    compound_dict[pieces] = full
    
## TODO: Specific rules
# recht #kräftig => rechtskräftig


# General catch-call rule to be applied at the end
p_all = re.compile(r'(\w) #(\w)', re.U)

for line in sys.stdin:
    # Apply specific rules
    
    # catch consecutive pieces broken apart by hashes, replace their span with the full word from the compound_dict
    split = line.split()
    pieces = []
    span = ''
    for i, word in enumerate(split):
        if word.startswith('#'):
            if len(pieces) == 0:
                pieces.append(split[i-1])
                span = span + split[i-1] + ' '
            pieces.append(word[1:])
            span += word
        if len(pieces) != 0 and (i == len(split)-1 or not split[i+1].startswith('#')): # reached the end of a set of pieces
            if ','.join(pieces) in compound_dict:
                to_replace = re.compile(span, re.U)
                line = to_replace.sub(compound_dict[','.join(pieces)], line)
                pieces = []
                #print >> sys.stderr, 'FOUND ONE'
            else:
                #print >> sys.stderr, 'couldnt find ', ','.join(pieces), ' in dict'
                #print >> sys.stderr, line
                pieces = []


    
    # Catch-all rule
    line = p_all.sub(r'\1\2',line)

    print line.strip()
