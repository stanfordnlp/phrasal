#!/usr/bin/env python
#
# Extract translation information from a database dump.
#
import sys
import codecs
import csv
import json
import os
import re
from os.path import basename,split
from datetime import datetime
from collections import namedtuple,defaultdict
from csv_unicode import UnicodeReader
import imt_utils
from imt_utils import segment_times_from_log,url2doc,initial_translations_from_imt_log,initial_translations_from_pe_log,load_references

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

stderr = lambda x:sys.stderr.write(str(x) + os.linesep)

args = sys.argv[1:]
if len(args) < 2:
    sys.stderr.write('Usage: python %s dump_file ref_file [ref_file]%s' % (basename(sys.argv[0]), os.linesep))
    sys.exit(-1)
    
dump_file = args[0]

stderr('Loading references...')
doc_to_ref = load_references(args[1:])
doc_to_timing = defaultdict(dict)
session_id = 0

stderr('Loading database dump...')
doc_to_user_txt = defaultdict(dict)
doc_to_user_time = defaultdict(dict)
dump_row_list = imt_utils.load_middleware_dump(dump_file)
for row in dump_row_list:
    segment_to_tgt_txt = imt_utils.final_translations_from_dump_row(row)
    doc_name = url2doc(row.src_doc)
    log = json.loads(row.log)
    segment_to_time = segment_times_from_log(log)
    segment_to_mt = initial_translations_from_imt_log(log) if row.interface == 'imt' else initial_translations_from_pe_log(log)
    for line_id in sorted(segment_to_tgt_txt.keys()):
        doc_id = '%s:%d' % (doc_name, line_id)
        user_id = row.username + ':' + row.interface
        mt_id = 'MT:' + row.interface
        doc_to_user_txt[doc_id][user_id] = segment_to_tgt_txt[line_id]
        doc_to_user_time[doc_id][user_id] = segment_to_time[line_id]
        doc_to_user_txt[doc_id][mt_id] = segment_to_mt[line_id]
        doc_to_user_time[doc_id][mt_id] = 0.0
            
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
        
        
