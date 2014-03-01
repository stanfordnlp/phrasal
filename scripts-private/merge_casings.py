#!/usr/bin/env python
# coding: utf-8
#
# Merges two cased texts by uppercasing anything that is cased in either text. 
# Outputs merged text to stdout.
#
# Usage: merge_casings.py casing1 casing2
# The input files must match exactly except for case.
#
# Author: Julia Neidert
#

import sys
import codecs 

f1 = codecs.open(sys.argv[1], 'r', 'utf-8')
f2 = codecs.open(sys.argv[2], 'r', 'utf-8')

def choose_upper_char(line1, line2, char_ind):
    if line1[char_ind].isupper():
        return line1[char_ind]
    else:
        return line2[char_ind]
    

for line1 in f1:
    line1 = line1.strip()
    line2 = f2.readline()
    if len(line2) == 0:
        sys.exit("Merge error: File 1 has more lines than file 2.")
    line2 = line2.strip()
    if len(line1) != len(line2):
        sys.exit("Merge error: The lengths of a line of the input cased texts are not the same.\nFile 1: "+line1+"\nFile 2: "+line2)
    output_chars = [ choose_upper_char(line1, line2, i) for i in range(len(line1)) ]
    output = "".join(output_chars) 
    print output.encode('utf8')

if f2.readline() != "":
    sys.exit("Merge error: File 2 has more lines than file 1.")
