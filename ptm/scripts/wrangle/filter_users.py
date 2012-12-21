#!/usr/bin/env python

import sys
import csv

from make_user_frame import Row

args = sys.argv[1:]
user_ids = set(args[0].split(','))

with open(args[1]) as infile:
    r = csv.reader(infile)
    # Skip header
    r.next()
    with open('outfile.txt','w') as outfile:
        w = csv.writer(outfile)
        write_header = True
        for row in map(Row._make, r):
            if write_header:
                write_header = False
                header_fields = list(row._fields)
                del header_fields[1]
                w.writerow(header_fields)
            if row.user_id in user_ids:
                w.writerow([v for (k,v) in row._asdict().iteritems() if not k == 'user_name'])
