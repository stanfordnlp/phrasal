#!/usr/bin/env python
#
# Makes an anova table from actionlog,metafile tuples
#
import sys
import re
import codecs
import os
import csv
from os.path import basename
from collections import defaultdict,namedtuple
from csv_unicode import UnicodeReader,UnicodeWriter
from argparse import ArgumentParser

# Action log event
Event = namedtuple('Event', 'sourceid userid time event_name event_class target x y key')

# anova row
Row = namedtuple('Row', 'sourceid userid ui_id time')

def get_end_times(actionlog):
    """ Gets the end events for each action sequence in the log

    Args:
    Returns:
    Raises:
    """
    time_dict = {}
    with open(actionlog) as in_file:
        for event in map(Event._make, UnicodeReader(in_file)):
            if event.event_name == 'end':
                time_dict[event.sourceid] = event.time
    return time_dict

def tuples_to_rows(file_list):
    """ 

    Args:
      file_list -- list of (actionlog,metafile) tuples
    Returns:
    Raises:
    """

    rows = []
    for actionlog,metafile in file_list:
        user_id_1 = re.search('^(\d+)\.', basename(actionlog)).group(1)
        user_id_2 = re.search('^(\d+)\.', basename(metafile)).group(1)
        assert user_id_1 == user_id_2
        end_time_dict = get_end_times(actionlog)

        with open(metafile) as in_file:
            for i,line in enumerate(in_file):
                if i == 0:
                    continue
                toks = line.strip().split('\t')
                if len(toks) != 4:
                    print toks
                assert len(toks) == 4
                src_id = str(i-1)
                if end_time_dict.has_key(src_id):
                    time = end_time_dict[src_id]
                    ui_id = toks[2]
                    rows.append(Row(sourceid=src_id,
                                    userid=user_id_1,
                                    ui_id=ui_id,
                                    time=time))
                else:
                    sys.stderr.write('No translation time for %s (line %d)%s' % (metafile, i, os.linesep))
                    continue
    return rows

def make_anova_table(file_list, output_dir):
    """ Make an anova table for a list of (actionlog,metafile) tuples

    Args:
    Returns:
    Raises:
    """
    rows = tuples_to_rows(file_list)
    with open('%s/anova.csv' % (output_dir),'w') as out_file:
        out_csv = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)
        for i,row in enumerate(rows):
            if i == 0:
                out_csv.writerow(list(row._fields))
            out_csv.writerow([x for x in row._asdict().itervalues()])
    sys.stderr.write('Wrote %d rows%s' % (i+1, os.linesep))

def main():
    desc='Make an anova table for a set of translations'
    parser=ArgumentParser(description=desc)
    parser.add_argument('-l','--log_files',
                        dest='logfiles',
                        nargs='+',
                        required=True,
                        help='Action log file.')
    parser.add_argument('-m','--meta',
                        dest='metafiles',
                        nargs='+',
                        required=True,
                        help='Meta file for each target translation.')
    parser.add_argument('-o', '--output_dir',
                        dest='out_dir',
                        default=None,
                        type=str,
                        help='Output directory for files.')
    args = parser.parse_args()

    output_dir = args.out_dir if args.out_dir else './'
    files = zip(args.logfiles,args.metafiles)
    make_anova_table(files, output_dir)

if __name__ == '__main__':
    main()
