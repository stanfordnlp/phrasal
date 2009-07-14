#!/usr/bin/env python

# nbest2phrasetable
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
    trans_id = 0
    in_option = False
    opt_part = -1
    
    outline = ''
    for line in INFILE:
        line = line.strip()
        is_start = p_start.search(line)
        if is_start or in_option:
            in_option = True
            is_end = p_end.search(line)
            if is_end:
                in_option = False
                trans_id = trans_id + 1
                opt_part = -2
            elif opt_part == 0:
                (o_source,o_target) = line.split('=>')
                outline = str(trans_id) + DELIM + o_source + DELIM + o_target + DELIM
            elif opt_part == 1:
                o_score = line.split()[1]
                outline = outline + o_score + DELIM
            elif opt_part == 2:
                o_coverage = line.split(':')[1]
                OUTFILE.write(outline + o_coverage + '\n')
                opt_part = -1
            opt_part = opt_part + 1
    
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

