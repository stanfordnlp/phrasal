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

def clean_corpus(file1_name, file2_name, min_chars, max_tokens):
    """

    Args:
    Returns:
    Raises:
    """
    out1 = get_outfile_name(file1_name)
    out2 = get_outfile_name(file2_name)
    with get_reader(file1_name) as infile1:
        with get_reader(file2_name) as infile2:
            with gz_utf8_writer(out1) as outfile1:
                with gz_utf8_writer(out2) as outfile2:
                    n_filtered = 0
                    for i,line1 in enumerate(infile1):
                        line2 = infile2.readline()
                        if line2:
                            line1 = line1.strip()
                            line2 = line2.strip()
                            if len(line1) > min_chars \
                               and len(line2) > min_chars \
                               and len(line1.split()) < max_tokens \
                               and len(line2.split()) < max_tokens:
                                outfile1.write(line1 + os.linesep)
                                outfile2.write(line2 + os.linesep)
                            else:
                                n_filtered += 1
                        else:
                            sys.stderr.write('file2 exhausted after %d lines%s' % (i, os.linesep))
                            sys.exit(-1)
                    # Ensure that infile2 is exhausted
                    if infile2.readline():
                        sys.stderr.write('file1 exhausted after %d lines)%s'\
                                          % (i, os.linesep))
                        sys.exit(-1)
    print 'Filtered %d / %d lines' % (n_filtered, i+1)

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
    args = parser.parse_args()
    
    clean_corpus(args.file1, args.file2, args.min_chars, args.max_tokens)

if __name__ == '__main__':
    main()
