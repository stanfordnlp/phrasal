#!/usr/bin/env python
#
# Computes t-tests for time from action log events.
# Reads the output of actionlog_to_csv.py
#
import sys
from collections import Counter,namedtuple
from csv_unicode import UnicodeReader
from argparse import ArgumentParser

# Format of the CSV log files
import ptm_file_io

from ptm_stats import paired_diff_t_test


def paired_difference_test(a_counts, a_nums, b_counts, b_nums):
    """ Run a paired difference t-test on the event counts
    represented by the two input parameters.

    Args:
    Returns:
    Raises:
    """
    assert len(set(a_counts.keys()) ^ set(b_counts.keys())) == 0
    assert len(set(a_nums.keys()) ^ set(b_nums.keys())) == 0
    
    a_means = []
    b_means = []
    for src_id in sorted(a_counts.keys()):
        a_cnt = a_counts[src_id]
        a_num = a_nums[src_id]
        a_mean = float(a_cnt) / float(a_num)
        b_cnt = b_counts[src_id]
        b_num = b_nums[src_id]
        b_mean = float(b_cnt) / float(b_num)

        print '%.2f\t%.2f\t%d\t%d' % (a_mean,b_mean,a_num,b_num)
        a_means.append(a_mean)
        b_means.append(b_mean)

    paired_diff_t_test(a_means, b_means)
        

def get_times_ordered_by_srcid(filename):
    """

    Args:
    Returns:
    Raises:
    """
    end_events = ptm_file_io.load_actionlog_events(filename)
    end_events = [(int(x.sourceid),int(x.ui_id),int(x.time)) for x in end_events if x.event_name == 'end']
    return sorted(end_events, key=lambda x:x[0])

    
def run_test(file_list):
    """ Reads action logs that have been parsed with
    actionlog_to_csv.py. Counts events.

    Args:
    Returns:
    Raises:
    """
    ui1_counts = Counter()
    ui1_nums = Counter()
    ui2_counts = Counter()
    ui2_nums = Counter()

    for filename in file_list:
        time_tuple_list = get_times_ordered_by_srcid(filename)
        for src_id,ui_id,time in time_tuple_list:
            if ui_id == 1:
                ui1_counts[src_id] += time
                ui1_nums[src_id] += 1
            elif ui_id == 2:
                ui2_counts[src_id] += time
                ui2_nums[src_id] += 1
            else:
                raise RuntimeError
            
    paired_difference_test(ui1_counts, ui1_nums, ui2_counts, ui2_nums)

    
def main():
    desc='Compute similarity of translations to a reference'
    parser = ArgumentParser(description=desc)
    parser.add_argument('meta_csv_files',
                        nargs='+',
                        help='Meta CSV files.')

    args = parser.parse_args()

    run_test(args.meta_csv_files)
    
if __name__ == '__main__':
    main()
