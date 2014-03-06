#!/usr/bin/env python
import sys
import codecs
import csv
import dateutil.parser
import json
from os.path import basename,split
from datetime import datetime
from collections import namedtuple,defaultdict
from csv_unicode import UnicodeReader

DumpRow = namedtuple('DumpRow', 'username src_doc tgt_lang interface order create_time start_time end_time complete training valid text log')

str2bool = lambda x:True if x == '1' else False

args = sys.argv[1:]
dump_file = args[0]
doc_to_timing = defaultdict(dict)
session_id = 0
print 'id\tuser\tgenre\tfile\tui\tdb_time\tlog_time\tlog_num_events'
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
        doc_name = row.src_doc.replace('/static/data/fren/','')
        genre,file_name = split(doc_name)
        log = json.loads(row.log)
        log_time = str(log[-1]['time'])
        num_events = len(log)
        print '%d\t%s\t%s\t%s\t%s\t%s\t%s\t%d' % (session_id,
                                                  row.username,
                                                  genre,
                                                  file_name,
                                                  row.interface,
                                                  str(t_elapsed.total_seconds()),
                                                  log_time,
                                                  num_events)
        session_id += 1


