#!/usr/bin/env python
import sys
import codecs
import csv
import dateutil.parser
import json
import os
import re
from os.path import basename,split
from datetime import datetime
from collections import namedtuple,defaultdict
from csv_unicode import UnicodeReader

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

DumpRow = namedtuple('DumpRow', 'username src_doc tgt_lang interface order create_time start_time end_time complete training valid text log')

str2bool = lambda x:True if x == '1' else False
stderr = lambda x:sys.stderr.write(str(x) + os.linesep)
url2doc = lambda x:basename(x).replace('.json','').replace('src','tgt')

def load_refs(filename_list):
    """
    """
    filename_to_lines = {}
    for filename in filename_list:
        doc_id = url2doc(filename)
        with codecs.open(filename, encoding='utf-8') as infile:
            filename_to_lines[doc_id] = [x.strip() for x in infile.readlines()]
    return filename_to_lines

args = sys.argv[1:]
if len(args) < 2:
    sys.stderr.write('Usage: python %s dump_file ref_file [ref_file]%s' % (basename(sys.argv[0]), os.linesep))
    sys.exit(-1)
    
dump_file = args[0]

stderr('Loading references...')
doc_to_ref = load_refs(args[1:])
doc_to_timing = defaultdict(dict)
session_id = 0

stderr('Loading database dump...')
doc_to_user_txt = defaultdict(dict)
doc_to_user_time = defaultdict(dict)
with open(dump_file) as in_file:
    r = UnicodeReader(in_file, delimiter='|', quoting = csv.QUOTE_NONE)
    for row in map(DumpRow._make, r):
        if not row.log or len(row.log.strip()) == 0:
            continue
        if str2bool(row.training):
            continue
        text = json.loads(row.text)
        doc_name = url2doc(row.src_doc)
        log = json.loads(row.log)
        log_time = str(log[-1]['time'])
        for line_id in sorted(text.keys()):
            doc_id = doc_name + ':' + line_id
            user_id = row.username + ':' + row.interface
            doc_to_user_txt[doc_id][user_id] = text[line_id]
            doc_to_user_time[doc_id][user_id] = log_time
            
for doc_name in sorted(doc_to_ref.keys()):
    for i,ref in enumerate(doc_to_ref[doc_name]):
        doc_id = '%s:%d' % (doc_name, i)
        print doc_id
        print '%s\t%s\t%.3f\t%s' % ('ref', 'ref', 0.0, ref)
        for user_id in sorted(doc_to_user_txt[doc_id].keys()):
            username,ui = user_id.split(':')
            tgt_txt = doc_to_user_txt[doc_id][user_id]
            doc_time = doc_to_user_time[doc_id][user_id]
            tgt_txt = re.sub('\s+', ' ', tgt_txt)
            print '%s\t%s\t%s\t%s' % (username, ui, doc_time, tgt_txt)
        print
        
        
