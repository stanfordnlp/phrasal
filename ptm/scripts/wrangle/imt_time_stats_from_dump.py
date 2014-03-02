#!/usr/bin/env python
import sys
import codecs
import csv
import dateutil.parser
from os.path import basename
from datetime import datetime
from collections import namedtuple,defaultdict
from csv_unicode import UnicodeReader

DumpRow = namedtuple('DumpRow', 'username src_doc tgt_lang interface order create_time start_time end_time complete training valid text log')

str2bool = lambda x:True if x == '1' else False

args = sys.argv[1:]
dump_file = args[0]
doc_to_timing = defaultdict(dict)
with open(dump_file) as in_file:
    r = UnicodeReader(in_file, delimiter='|', quoting = csv.QUOTE_NONE)
    for row in map(DumpRow._make, r):
        if not row.log or len(row.log.strip()) == 0:
            continue
        if str2bool(row.training):
            continue
        start = dateutil.parser.parse(row.start_time)
        end = dateutil.parser.parse(row.end_time)
        t_elapsed = end - start
        doc_name = basename(row.src_doc)
        doc_to_timing[doc_name][row.interface] = '%s:%s' % (row.username, str(t_elapsed.total_seconds()))

print 'Doc\tInteractive\tPostEdit'
for src_id in sorted(doc_to_timing.keys()):
    time_str = ''
    for ui in sorted(doc_to_timing[src_id].keys()):
        time_str += '\t%s' % (doc_to_timing[src_id][ui])
    print '%s%s' % (src_id,time_str)

