#!/usr/bin/env python
#
# Create a frame of segment-level translation 
#
#
#
import sys
import codecs
import csv
import dateutil.parser
import json
import os
from os.path import basename,split
from datetime import datetime
from collections import namedtuple,defaultdict
from csv_unicode import UnicodeReader
import imt_utils

stderr = lambda x:sys.stderr.write(str(x) + os.linesep)

args = sys.argv[1:]
if len(args) < 1:
    stderr('Usage: python %s dump_file' % (basename(sys.argv[0])))
    sys.exit(-1)
    
dump_file = args[0]
doc_to_timing = defaultdict(dict)
dump_row_list = imt_utils.load_middleware_dump(dump_file)
print 'id\tuser\tgenre\tfile\tui\tdb_time\tlog_time\tlog_num_events'
for row in dump_row_list:
    segment_to_tgt_txt = imt_utils.final_translations_from_dump_row(row)
    doc_name = imt_utils.url2doc(row.src_doc)
    log = json.loads(row.log)
    segment_to_time = imt_utils.segment_times_from_log(log)
    segment_to_mt = imt_utils.initial_translations_from_imt_log(log) if row.interface == 'imt' else initial_translations_from_pe_log(log)
    segment_to_src_txt = imt_utils.source_segments_from_log(log)
    for line_id in sorted(segment_to_tgt_txt.keys()):
        pass
    
