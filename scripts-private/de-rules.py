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

reload(sys)
sys.setdefaultencoding('utf-8')

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)


# load dictionary
compound_file = codecs.open('/scr/nlp/data/WMT/scripts/compound.dict','r','utf-8')
compound_dict = {}
'''
for line in compound_file:
    pieces = line.split()[0] # comma-separated pieces
    full = line.split()[1]
    compound_dict[pieces] = full
'''
# load backup dict
backoff_file = codecs.open('/scr/nlp/data/WMT/scripts/backoff.dict','r','utf-8')
backoff_dict = {}
for line in backoff_file:
    backoff_dict[line.split()[0]] = line.split()[1]

# load last backup suffix dict
suffix_file = codecs.open('/scr/nlp/data/WMT/scripts/suffix.dict','r','utf-8')
suffix_dict = {}
for line in suffix_file:
    suffix_dict[line.split()[0]] = line.split()[1]
    
## Specific rules
p_apos = re.compile(r'\\\'', re.U)


# General catch-call rule to be applied at the end
p_all = re.compile(r'(\w) #(\w)', re.U)



for line in sys.stdin:
    # Apply specific rules
    line = p_apos.sub('\'',line)


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
            to_replace = re.compile(span, re.U)
            if ','.join(pieces) in compound_dict: # if its in the full dict just use that word
                line = to_replace.sub(compound_dict[','.join(pieces)], line)

            else:
                # else use the backoff dict to find suffixes for individual words
                newpieces = []
                for j, piece in enumerate(pieces):
                    #piece = piece.encode('utf-8')
                    if j == len(pieces)-1:
                        newpieces.append(piece)
                        break
                    if piece in backoff_dict:
                        if backoff_dict[piece] != '*NONE*':
                            newpieces.append(piece+backoff_dict[piece])
                        else:
                            newpieces.append(piece)
                    elif piece[-3:] in suffix_dict:
                        if suffix_dict[piece[-3:]] != '*NONE*':
                            newpieces.append(piece+suffix_dict[piece[-3:]])
                        else:
                            newpieces.append(piece)
                    else:
                        newpieces.append(piece)                        
                pieces = newpieces
                
                line = to_replace.sub(''.join(pieces),line)
                pieces = []
                span = ''
    
    



    # Catch-all rule
    line = p_all.sub(r'\1\2',line)

    print line.strip()

