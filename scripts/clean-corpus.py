#!/usr/bin/env python
#
# Filters a bitext by minimum character length
# and maximum token length.
#
# Writes to gzip'd files.
#
# Assumes utf-8 encoding.
#
# Author: Spence Green
#
import sys
from argparse import ArgumentParser
import codecs
import os
import gzip
from os.path import basename,splitext
from itertools import izip_longest

sys.stdin = codecs.getreader('utf-8')(sys.stdin)
sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

def get_reader(fname):
    (root,ext) = splitext(basename(fname))
    if ext == '.gz':
        return codecs.getreader('utf-8')(gzip.open(fname))
    else:
        return codecs.open(fname, encoding='utf-8')

def gz_utf8_writer(fname):
    return codecs.getwriter('utf-8')(gzip.open(fname, 'w'))

def get_outfile_name(fname):
    fname = basename(fname)
    (root,ext) = splitext(fname)
    return '%s.filt.gz' % (root) if ext == '.gz' else fname+'.filt.gz'

def clean_corpus(file1_name, file2_name, min_chars, max_tokens, do_dedup):
    """

    Args:
    Returns:
    Raises:
    """
    out1 = get_outfile_name(file1_name)
    out2 = get_outfile_name(file2_name)
    dup_hashes = set()
    fv="(*&(*&@@#$$@*)@#"
    with get_reader(file1_name) as infile1, get_reader(file2_name) as infile2, gz_utf8_writer(out1) as outfile1, gz_utf8_writer(out2) as outfile2:
        n_filtered = 0
        n_dup = 0
        n_lines = 0
        for line1,line2 in izip_longest(infile1,infile2,fillvalue=fv):
            assert not line1 == fv
            assert not line2 == fv
            n_lines += 1
            line1 = line1.strip()
            line2 = line2.strip()
            if do_dedup:
                item_key = hash('%s|||%s' % (line1,line2))
                if item_key in dup_hashes:
                    n_dup += 1
                    continue
                dup_hashes.add(item_key)
            line1_tokens = line1.split()
            len_line1 = len(line1_tokens)
            line2_tokens = line2.split()
            len_line2 = len(line2_tokens)
            if len_line1 == 0 or len_line2 == 0:
                fertility = 1000
            else:
                fertility = len_line1 / len_line2 if len_line1 > len_line2 else len_line2 / len_line1
            # Max fertility is the value from GIZA++
            if fertility < 9 \
               and len(line1) > min_chars \
               and len(line2) > min_chars \
               and len(line1_tokens) < max_tokens \
               and len(line2_tokens) < max_tokens:
                outfile1.write(line1 + os.linesep)
                outfile2.write(line2 + os.linesep)
            else:
                n_filtered += 1
    print 'Filtered %d / %d lines' % (n_filtered, n_lines)
    if do_dedup:
        print 'Duplicates: %d / %d lines' % (n_dup, n_lines)

def main():
    desc = 'Filter a parallel bitext.'
    parser = ArgumentParser(description=desc)
    parser.add_argument('file1',
                        help='First side of bitext.')
    parser.add_argument('file2',
                        help='Second side of bitext.')
    parser.add_argument('-l','--length-max',
                        dest='max_tokens',
                        type=int,
                        default=100,
                        help='Maximum line length in tokens (default: 100).')
    parser.add_argument('-m','--length-min',
                        dest='min_chars',
                        type=int,
                        default=1,
                        help='Minimum line length in characters (default: 1)')
    parser.add_argument('-d','--dedup',
                        dest='dedup',
                        action='store_true',
                        help='Discard duplicate lines.')
    args = parser.parse_args()
    
    clean_corpus(args.file1, args.file2,
                 args.min_chars, args.max_tokens, args.dedup)

if __name__ == '__main__':
    main()
