#!/usr/bin/env python
#
# Makes the translation characteristics data frame.
#
import sys
import codecs
import os
import csv
from os.path import basename,dirname,exists
from collections import namedtuple,Counter,defaultdict
from csv_unicode import UnicodeReader,UnicodeWriter
from argparse import ArgumentParser

import ptm_file_io

# Edit distance computations
from edit_distance import dameraulevenshtein

# Output format
TranslationAnovaRow = namedtuple('TranslationAnovaRow', 'time prev_time pause_mean pause_cnt pause_cnt1s pause_ratio pause_ratio1s event_cnt event_keyboard_cnt event_mouse_cnt event_focus_cnt event_browser_cnt src_id ui_id tgt_len user_id edit_distance rank')

# Event delay that indicates a pause (unit: ms)
# From Joort1996
MIN_PAUSE_DURATION = 300

# Duration used by Jakobsen1998 (Translog) and Krings2001
MIN_PAUSE_DURATION2 = 1000

def process_action_file(action_file):
    """

    Args:
    Returns:
    Raises:
    """
    global MIN_PAUSE_DURATION,MIN_PAUSE_DURATION2
    time_list = []
    pause_counts = Counter()
    pause_counts2 = Counter()
    pause_means = []
    pause_ratio = []
    pause_ratio2 = []
    event_counters = defaultdict(Counter)

    last_src_id = 0
    last_event_time = 0
    durations = 0
    durations2 = 0
    action_rows = ptm_file_io.load_actionlog_events(action_file)
    for row in action_rows:
        src_id = int(row.sourceid)
        event_time = int(row.time)
        event_class = row.event_class
        if row.event_name == 'start':
            last_src_id = src_id
            last_event_time = 0
            durations = 0
            durations2 = 0
            
        elif row.event_name == 'end':
            assert pause_counts[src_id] > 0
            mean = float(durations) / float(pause_counts[src_id])
            pause_means.append(mean)
            
            ratio = float(durations) / float(event_time)
            pause_ratio.append(ratio)
            ratio1s = float(durations2) / float(event_time)
            pause_ratio2.append(ratio1s)

            time_list.append(event_time)

        else:
            event_counters[src_id][event_class] += 1
            pause_duration = event_time - last_event_time
            if pause_duration > MIN_PAUSE_DURATION:
                pause_counts[src_id] += 1
                durations += pause_duration
            if pause_duration > MIN_PAUSE_DURATION2:
                pause_counts2[src_id] += 1
                durations2 += pause_duration
            last_event_time = event_time
    return time_list,pause_counts,pause_counts2,pause_means,event_counters,pause_ratio,pause_ratio2


def get_edit_distances(tgt_segments, ref_segments):
    """

    Args:
    Returns:
    Raises:
    """
    edit_distances = []
    for tgt_line,ref_line in zip(tgt_segments,ref_segments):
        distance = dameraulevenshtein(ref_line,tgt_line,True)
        edit_distances.append(distance)
    return edit_distances


def get_rows(directory, user_id, ref_segments, rankings):
    """

    Args:
    Returns:
    Raises:
    """
    # Sanity check: assert that all of the files are present
    tgt_file = '%s/%s.tgt.txt' % (directory,user_id)
    assert exists(tgt_file)
    meta_file = '%s/%s.tgt.meta.txt' % (directory,user_id)
    assert exists(meta_file)
    action_file = '%s/%s.tgt.actionlog.txt.csv' % (directory,user_id)
    assert exists(action_file)

    with codecs.open(tgt_file,encoding='utf-8') as tgt_infile:
        tgt_segments = [x.strip() for x in tgt_infile.readlines()]
    assert len(tgt_segments) == len(ref_segments)

    meta_rows = ptm_file_io.load_meta_file(meta_file)

    # Action log response variables
    time_list,pause_counts,pause_counts2,pause_means,event_counters,pause_ratio,pause_ratio2 = process_action_file(action_file)

    # Text clustering
    tgt_edist_list = get_edit_distances(tgt_segments, ref_segments)

    # Output the frame
    output_row_list = []
    for i in xrange(len(tgt_segments)):
        src_len = meta_rows[i].src_len
        tgt_len = meta_rows[i].tgt_len
        ui_id = meta_rows[i].ui_id
        score = rankings[i][int(user_id)]
        prev_time = time_list[i-1] if i>0 else 1
        row = TranslationAnovaRow(time=str(time_list[i]),
                                  prev_time=str(prev_time),
                                  pause_mean=str(pause_means[i]),
                                  pause_cnt=str(pause_counts[i]),
                                  pause_cnt1s=str(pause_counts2[i]),
                                  pause_ratio=str(pause_ratio[i]),
                                  pause_ratio1s=str(pause_ratio2[i]),
                                  event_cnt=str(sum(event_counters[i].values())),
                                  event_keyboard_cnt=str(event_counters[i]['keyboard']),
                                  event_mouse_cnt=str(event_counters[i]['mouse']),
                                  event_focus_cnt=str(event_counters[i]['focus']),
                                  event_browser_cnt=str(event_counters[i]['browser']),
                                  src_id=str(i),
                                  ui_id=ui_id,
                                  tgt_len=tgt_len,
                                  user_id=user_id,
                                  edit_distance=str(tgt_edist_list[i]),
                                  rank=str(score))
        output_row_list.append(row)
    
    return output_row_list
            

def make_frame(directory, user_ids, out_prefix, ref_filename, ranking_file):
    """

    Args:
    Returns:
    Raises:
    """
    rankings = ptm_file_io.load_ranking_file(ranking_file)
    
    with codecs.open(ref_filename,encoding='utf-8') as ref_infile:
        ref_file = [x.strip() for x in ref_infile.readlines()]
    
    dir_name = basename(dirname(directory))
    out_file_name = '%s.%s.csv' % (out_prefix, dir_name)
    with open(out_file_name,'w') as out_file:
        csv_file = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)
        write_header = True
        for user_id in user_ids:
            for row in get_rows(directory, user_id, ref_file, rankings):
                if write_header:
                    write_header = False
                    csv_file.writerow(list(row._fields))
                csv_file.writerow([x for x in row._asdict().itervalues()])

def main():
    desc='Make translation characteristics frame.'
    parser=ArgumentParser(description=desc)
    parser.add_argument('reference',
                        help='Target reference for computing edit distance.')
    parser.add_argument('directory',
                        help='Language directory with translations')
    parser.add_argument('ranking_file',
                        help='Translations rankings from csv2ranking.py')
    parser.add_argument('-o', '--output_prefix',
                        dest='out_prefix',
                        default='trans_frame',
                        type=str,
                        help='Output filename.')
    parser.add_argument('-u', '--user_ids',
                        dest='user_ids',
                        required=True,
                        type=str,
                        help='CSV list of user ids.')
    args = parser.parse_args()

    user_ids = set(args.user_ids.split(',')) if args.user_ids else set()
    make_frame(args.directory, user_ids,
               args.out_prefix,
               args.reference,
               args.ranking_file)

        
if __name__ == '__main__':
    main()
    
