#!/usr/bin/env python
# coding: utf-8
#
# Fix issues with the English detokenization of German text
#
# Author: Rob Voigt
#
import sys
import re
import codecs
import string

reload(sys)
sys.setdefaultencoding('utf-8')

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

## hyphens: attached left before und, left in place for certain words, otherwise split
hyphen_file = codecs.open('/scr/nlp/data/WMT/scripts/hyphens.dict','r','utf-8')
hyphen_dict = set([])
for line in hyphen_file:
    hyphen_dict.add(line.split()[1])
p_und_hyphen = re.compile(r'(\w)-und', re.U)
p_all_hyphen = re.compile(r'(\w)-(\w)',re.U)
p_postquote = re.compile(r'\"([a-z])',re.U)
word_list = ['eBay','U-Bahn','Canada','Wirtschaftliche','Berlin','iPhone','iPad','NeXT','TransCanada','MasterCard','LaBelle','PricewaterhouseCoopers','NASDAQ']

def replacement(match):
    return '\"'+match.group(1).upper()+match.group(2)+match.group(3)+'\"'

def mc_rep(match):
    return ' Mc'+match.group(1).upper()

for line in sys.stdin:
    # Apply specific rules
    line = p_und_hyphen.sub('\g<1>- und',line)

    for item in word_list:
        cur = re.compile('%s'%item.lower(),re.I)
        line = cur.sub(item,line)
    line = re.sub(r'\"([a-z])([^"]+)([!.?,])\"',replacement,line)
    line = re.sub(r' mc([a-z])',mc_rep,line)
    newline = []
    for word in line.split():
        if '-' in word and not word.endswith('-') and word.lower().strip(string.punctuation) not in hyphen_dict:
            word = p_all_hyphen.sub('\g<1> - \g<2>',word)
            newline.append(word)
        else:
            newline.append(word)
    line = ' '.join(newline)

    print line.strip()

