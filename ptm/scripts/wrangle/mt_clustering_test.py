#!/usr/bin/env python
#
# Computes clustering of target translations around a reference
# translation according to various edit distance metrics
#
#
import sys
import codecs
from collections import Counter
from argparse import ArgumentParser
import ptm_file_io
from ptm_stats import paired_diff_t_test
from edit_distance import levenshtein,dameraulevenshtein


def load_segments(filename):
    with codecs.open(filename, encoding='utf-8') as infile:
        return [x.strip() for x in infile]

def ids_from_meta_file(filename):
    rows = ptm_file_io.load_meta_file(filename)
    return [int(row.ui_id) for row in rows]

def diff_test(counts_a, nums_a, counts_b, nums_b):
    """ Paired difference t-test.
    """
    # Sanity check
    keys_a = counts_a.keys()
    keys_b = counts_b.keys()
    assert len(set(keys_a) ^ set(keys_b)) == 0

    means_a = []
    means_b = []
    print 'A\tB\t#A\t#B'
    for i in sorted(keys_a):
        means_a.append(float(counts_a[i]) / float(nums_a[i]))
        means_b.append(float(counts_b[i]) / float(nums_b[i]))
        print '%.2f\t%.2f\t%d\t%d' % (means_a[i],means_b[i],nums_a[i],nums_b[i])

    paired_diff_t_test(means_a,means_b)
    
def run_test(ref_file, tgt_list, tgt_meta_list):
    """

    Args:
    Returns:
    Raises:
    """
    ref_segments = load_segments(ref_file)

    # Sufficient statistics
    counts_lev_a = Counter()
    nums_lev_a = Counter()
    counts_lev_b = Counter()
    nums_lev_b = Counter()
    counts_dlev_a = Counter()
    nums_dlev_a = Counter()
    counts_dlev_b = Counter()
    nums_dlev_b = Counter()
    for (tgt_file,meta_file) in zip(tgt_list,tgt_meta_list):
        tgt_segments = load_segments(tgt_file)
        ui_ids = ids_from_meta_file(meta_file)
        for i,tgt_txt in enumerate(tgt_segments):
            ref_txt = ref_segments[i]
            lev_dist = levenshtein(ref_txt,tgt_txt,True)
            dlev_dist = dameraulevenshtein(ref_txt,tgt_txt,True)
            ui_id = ui_ids[i]
            if ui_id == 1:
                counts_lev_a[i] += lev_dist
                counts_dlev_a[i] += dlev_dist
                nums_lev_a[i] += 1
                nums_dlev_a[i] += 1
            elif ui_id == 2:
                counts_lev_b[i] += lev_dist
                counts_dlev_b[i] += dlev_dist
                nums_lev_b[i] += 1
                nums_dlev_b[i] += 1
            else:
                raise RuntimeError

    print 'Levenshtein distance'
    diff_test(counts_lev_a, nums_lev_a, counts_lev_b, nums_lev_b)
    print
    print 'Damerau-Levenshtein distance'
    diff_test(counts_dlev_a, nums_dlev_a, counts_dlev_b, nums_dlev_b)

def main():
    desc='Compute similarity of translations to a reference'
    parser = ArgumentParser(description=desc)
    parser.add_argument('-r,', '--ref',
                        type=str,
                        required=True,
                        help='Reference translations')
    parser.add_argument('-t', '--tgt_files',
                        dest='tgt_files',
                        nargs='+',
                        required=True,
                        help='Target translation files')
    parser.add_argument('-m',  '--meta_files',
                        dest='meta_files',
                        nargs='+',
                        required=True,
                        help='Meta translation files')

    args = parser.parse_args()

    assert len(args.tgt_files) == len(args.meta_files)
    
    run_test(args.ref, args.tgt_files, args.meta_files)
    
if __name__ == '__main__':
    main()
