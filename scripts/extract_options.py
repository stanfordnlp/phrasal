#!/usr/bin/env python

# extract_options.py
#
# author:  Spence Green
##############################

import sys
from optparse import OptionParser
import re
import codecs

def extract_options(infile,outfile):
    INFILE = codecs.open(infile,encoding='utf-8')
    OUTFILE = codecs.open(outfile,'w',encoding='utf-8')
    DELIM = ' ||| '
    
    p_start = re.compile('>> Translation Options <<',re.U)
    p_end = re.compile('>> End translation options <<',re.U)
    
    saw_start = False
    reading = False
    for line in INFILE:
        line = line.strip()
        saw_start = p_start.search(line)
        if saw_start or reading:
            if saw_start and not reading:
                reading = True
                continue
            
            reading = not p_end.search(line)
            if reading:
                OUTFILE.write(line + '\n')
    
    INFILE.close()
    OUTFILE.close()

def main():
    usage = 'usage: %prog [options] input_file output_file'
    parser = OptionParser(usage=usage)
    (opts,args) = parser.parse_args()
    if len(args) != 2:
        parser.print_help()
        sys.exit(-1)
    extract_options(args[0],args[1])

if __name__ == '__main__':
    main()
