#!/usr/bin/env python
# coding: utf-8
#
# Using POS tagged, truecased german text, find morphologically implausible ADJ/NOUN pairs and replace them with something plausible
#
# Author: Rob Voigt
#
import sys
import re
import codecs
import string
import itertools

reload(sys)
sys.setdefaultencoding('utf-8')

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)


morphy_file = codecs.open('/user/robvoigt/scr/morph/morphydict','r','utf-8')
morphy_dict = {}
cur = ''
counted = 0

for l in morphy_file:
    print l.strip()
    if l.startswith('#'):
        continue
    if l.startswith('<form>'):
        cur = re.sub(r'<form>(.+?)</form>','\g<1>',l)
        morphy_dict[cur]=set([])
        counted = 0
        continue
    if counted < 6:
        counted += 1
        if 'wrt' in l and 'kas' in l and 'gen' in l:
            try:
                morphy_dict[cur].add(l.split()[2:4]+l.split()[4].split('>')[0])
            except:
                print l

        
for key in morphy_dict:
    print key,'\t',morphy_dict[key]


cased_file = codecs.open(sys.argv[1],'utf-8')
pos_file = codecs.open(sys.argv[2],'utf-8')
for cased_line, pos_line in itertools.izip(cased_file,pos_file):
    # this assumes splitting on whitespace gives equal length splits
    cased_split = cased_line.split()
    pos_split = pos_line.split()
    
    for i, pos_word in enumerate(pos_split):
        if pos_word.split('#')[1] == 'NN': # fix this for whatever the tag for noun is if different
            if i > 0 and pos_split[i-1].split('#')[1] == 'ADJ': # or again whatever this tag is
                pass
        
                                                 

    print line.strip()

