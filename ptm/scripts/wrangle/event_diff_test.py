#!/usr/bin/env python
#
# Computes t-tests for various subsets of events
# from the action logs. 
#
import sys
import codecs
from collections import defaultdict,namedtuple
from csv_unicode import UnicodeReader
from argparse import ArgumentParser

# Format of the CSV log files
from ptm_file_io import Event

from scipy import stats


def paired_difference_test(e_counts, e_nums):
    """ Run a paired difference t-test on the event counts
    represented by the two input parameters.

    Args:
    Returns:
    Raises:
    """
    means = []
    n_events = 0
    print 'MEANS:'
    for src_id in sorted(e_counts.keys()):
        mean_pair = []
        num_pair = []
        for i,ui_id in enumerate(sorted(e_counts[src_id].keys())):
            num = float(e_counts[src_id][ui_id])
            n_events += int(num)
            denom = float(e_nums[src_id][ui_id])
            mean = num / denom
            mean_pair.append(num / denom)
            num_pair.append(denom)
        print '\t'.join(['%.3f' % (x) for x in mean_pair]),
        print '\t',
        print '\t'.join(['%d' % (x) for x in num_pair])
        means.append(mean_pair)

    diffs = [x[0]-x[1] for x in means]
    x_d = stats.tmean(diffs)
    s_d = stats.tstd(diffs)
    n = len(diffs)
    dof = n-1
    t_d = x_d / (s_d / n)

    # sf() is the survival function (1-cdf)
    pval = stats.t.sf(abs(t_d), dof)

    print
    print '# events:\t%d' % (n_events)
    print 't-statistic:\t%.4f' % (t_d)
    print 'dof:\t%d' % (dof)
    print 'p-value:\t%.4f' % (pval)

def inc_counts(e_counts, e_nums, n_events, src_id, ui_id):
    """ Convenience function for incrementing the event counters.

    Args:
    Returns:
    Raises:
    """
    e_counts[src_id][ui_id] = e_counts[src_id].get(ui_id, 0) + n_events
    e_nums[src_id][ui_id] = e_nums[src_id].get(ui_id, 0) + 1
    
def run_test(log_files, uid_set, class_to_evaluate):
    """ Reads action logs that have been parsed with
    actionlog_to_csv.py. Counts events.

    Args:
    Returns:
    Raises:
    """
    # 2D counters
    e_counts = defaultdict(dict)
    e_nums = defaultdict(dict)

    # Look for those nasty scroll events
    n_scroll_events = 0
    
    # Open the log files and count events
    n_users = 0
    for fname in log_files:
        fname_uid = fname[0:fname.find('.')]
        if not fname_uid in uid_set:
            continue
        n_users += 1
        with open(fname) as in_file:
            last_src_id = 0
            last_ui_id = 0
            n_events = 0
            for i,row in enumerate(map(Event._make, UnicodeReader(in_file))):
                if i == 0:
                    # Skip header
                    continue
                e_src_id = int(row.sourceid)
                e_class = row.event_class
                e_ui_id = int(row.ui_id)
                e_name = row.event_name
                if e_src_id != last_src_id:
                    # Dump counts to the counters
#                    print fname_uid,last_src_id,last_ui_id
                    inc_counts(e_counts, e_nums, n_events, last_src_id, last_ui_id)
                    n_events = 0
                last_ui_id = e_ui_id
                last_src_id = e_src_id
                if class_to_evaluate == 'all' or e_class == class_to_evaluate:
                    n_events += 1
                if e_name == 'scroll':
                    n_scroll_events += 1
            inc_counts(e_counts, e_nums, n_events, last_src_id, last_ui_id)

    print '# users:',n_users
    print '# scroll events:',n_scroll_events
    if n_users > 0:
        paired_difference_test(e_counts, e_nums)
    
def main():
    desc='Perform various paired difference t-test on parsed action logs'
    parser=ArgumentParser(description=desc)
    parser.add_argument('log_files',
                        nargs='+',
                        help='Action log CSV files.')
    parser.add_argument('-u', '--uid',
                        dest='uid_list',
                        type=str,
                        help='CSV list of user ids to sample.')
    parser.add_argument('-c', '--class',
                        dest='event_class',
                        default='all',
                        type=str,
                        help='Event classes to consider.')
    args = parser.parse_args()

    uid_set = set(args.uid_list.split(','))
    
    print 'Computing two-sided paired t-test for event class:', args.event_class
    run_test(args.log_files, uid_set, args.event_class)
    print
    print 'Done!'
    
if __name__ == '__main__':
    main()
