#!/usr/bin/env python
#
#  Export mouse hover events from parsed action
#  logs (actionlog_to_csv.py) to JSON for vis in
#  D3.
#
import sys
import re
import codecs
import json
import os
from collections import defaultdict,namedtuple,Counter
from argparse import ArgumentParser

import ptm_file_io
from ptm_file_io import Event

def parse_annote_file(filename):
    """ Try to discard tokenized punctuation.
    Args:
    Returns:
    Raises:
    """
    p_poss = re.compile('\'s')
    p_punct = re.compile('[\'\.,%:;]+')    
    tagged_tokens = defaultdict(dict)
    with codecs.open(filename,encoding='utf-8') as infile:
        tok_id = 0
        last_src_id = 0
        for line in infile:
            (src_id,this_tok_id,token,tag) = line.strip().split('\t')
            src_id = int(src_id)
            if src_id != last_src_id:
                last_src_id += 1
                tok_id = 0
            m = p_punct.match(token)
            if m or token == '-LRB-' or token == '-RRB-' \
                    or token == '\'s':
                sys.stderr.write('Discarding: %s%s' % (token,os.linesep))
                continue
            tagged_tokens[src_id][tok_id] = tag
            tok_id += 1
    return tagged_tokens


def create_metadata(filename, annote_file):
    meta_data = defaultdict(dict)
    tagged_tokens = parse_annote_file(annote_file)
    with codecs.open(filename,encoding='utf-8') as infile:
        for i,line in enumerate(infile):
            tokens = line.strip().split()
            for j,token in enumerate(tokens):
                tag = tagged_tokens[i][j]
                meta_data[i][j] = (token,tag)
            # Sanity check
            if j+1 in tagged_tokens[i]:
                print i
                print tagged_tokens[i]
                raise RuntimeError
    return meta_data


def logs_to_json(file_list, src_file, annote_file):
    """
    Args:
    Returns:
    Raises:
    """
    meta_data = create_metadata(src_file, annote_file)
    counts = defaultdict(Counter)

    for filename in file_list:
        sys.stderr.write('Reading: %s%s' % (filename,os.linesep))
        event_list = ptm_file_io.load_actionlog_events(filename)
        for event in event_list:
            if event.event_name == 'mouseover' and event.target == 'token':
                src_id = int(event.sourceid)
                tok_id = int(event.src_tok_id)
                counts[src_id][tok_id] += 1


    # Add in the counts to make the final data structure
    for src_id in sorted(meta_data.keys()):
        for tok_id in sorted(meta_data[src_id].keys()):
            count = counts[src_id][tok_id]
            meta_data[src_id][tok_id] += (count,)
    
    print json.dumps(meta_data,sort_keys=True,indent=4)


def main():
    desc='Export mouse hover events to JSON'
    parser = ArgumentParser(description=desc)
    parser.add_argument('-s','--src_file',
                        metavar='file',
                        dest='src_file',
                        required=True,
                        help='Source file.')
    parser.add_argument('-a','--annotations',
                        metavar='file',
                        dest='annote',
                        required=True,
                        help='Annotations TSV file (from CoreNLP).')
    parser.add_argument('log_csv',
                        nargs='+',
                        help='List of parsed log files')
    args = parser.parse_args()

    logs_to_json(args.log_csv, args.src_file, args.annote)

if __name__ == '__main__':
    main()
