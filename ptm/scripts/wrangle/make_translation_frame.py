#!/usr/bin/env python
#
# Makes the translation characteristics data frame
#
import sys
import re
import codecs
import os
import csv
from os.path import basename,dirname
from collections import namedtuple
from csv_unicode import UnicodeReader,UnicodeWriter
from argparse import ArgumentParser

# Data frame definition
Row = namedtuple('Row', 'time norm_time src_id ui_id tgt_len user_id')

def get_rows(directory, meta_file, action_file):
    """

    Args:
    Returns:
    Raises:
    """
    # Get user id (also a sanity check)
    idx = meta_file.find('.')
    user_id = meta_file[0:idx]
    idx = action_file.find('.')
    assert user_id == action_file[0:idx]
    
    # Get end times
    end_times = []
    with open(directory+'/'+action_file) as in_file:
        for i,line in enumerate(in_file):
            event_list = line.split('|')
            event_fields = event_list[-1].strip().split()
            if len(event_fields) == 1 and event_fields[0] == 'ERROR':
                sys.stderr.write('Discarding translation from %s: ln:%d %s%s' % (directory, i, action_file, os.linesep))
                end_times.append(-1)
            elif len(event_fields) == 2:
                end_times.append(int(event_fields[1]))
            else:
                sys.stderr.write('Error: ' + action_file + os.linesep)
    
    # Get translation data
    row_list = []
    with open(directory+'/'+meta_file) as in_file:
        for i,line in enumerate(in_file):
            if i == 0:
                # Skip the header
                continue
            fields = line.strip().split('\t')
            assert len(fields) == 6
            src_id = i-1
            if end_times[src_id] < 0:
                continue
            ui_id = fields[2]
            src_len = fields[4]
            tgt_len = fields[5]
            norm_time = str(int(end_times[src_id] / int(src_len)))
            row_list.append(Row(time=str(end_times[src_id]),
                                norm_time=norm_time,
                                src_id=str(src_id),
                                ui_id='ui'+ui_id,
                                tgt_len=tgt_len,
                                user_id=user_id))
    return row_list
            

def make_frame(directory_list, out_prefix):
    """

    Args:
    Returns:
    Raises:
    """
    for directory in directory_list:
        file_list = os.listdir(directory)
        meta_list = [x for x in file_list if x.find('meta')>0]
        action_list = [x for x in file_list if x.find('action')>0]
        assert len(meta_list) == len(action_list)

        file_tuples = zip(sorted(meta_list),sorted(action_list))
        dir_name = basename(dirname(directory))
        out_file_name = '%s.%s.csv' % (out_prefix, dir_name)
        with open(out_file_name,'w') as out_file:
            csv_file = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)
            write_header = True
            for meta_file,action_file in file_tuples:
                for row in get_rows(directory, meta_file, action_file):
                    if write_header:
                        write_header = False
                        csv_file.writerow(list(row._fields))
                    csv_file.writerow([x for x in row._asdict().itervalues()])

def main():
    desc='Make translation characteristics frame'
    parser=ArgumentParser(description=desc)
    parser.add_argument('lang_dirs',
                        metavar='DIR',
                        nargs='+',
                        help='Language directories with translations')
    parser.add_argument('-o', '--output_prefix',
                        dest='out_prefix',
                        default='trans_frame',
                        type=str,
                        help='Output filename.')
    args = parser.parse_args()

    make_frame(args.lang_dirs, args.out_prefix)

if __name__ == '__main__':
    main()
    
