#!/usr/bin/env python
#
# Deterministically convert an input file
# into a prefix file for tuning. Output is written
# to stdout.
#
# Author: Spence Green
import sys
import codecs
import os
from os.path import basename
from argparse import ArgumentParser

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

def make_prefixes(filename, samples_per_sent, step_size):
    """
    Make a prefix file deterministically by selecting suffixes.
    """
    err = lambda x : sys.stderr.write(x + os.linesep)
    with codecs.open(filename, encoding='utf-8') as infile:
        lines = [x.strip().split() for x in infile]
        err('# input lines %d' % (len(lines)))
        split = len(lines[-1])
        err('Seed %d' % (split))
        n_examples = 0
        n_full = 0
        n_force = 0
        for i in range(0,samples_per_sent):
            for j,line in enumerate(lines):
                split_this = split % (len(line)+1)
                prefix_len = len(line) - split_this
                # err(str('%d %d %d' % (len(line), split, split_this)))
                assert prefix_len <= len(line)
                if prefix_len == len(line):
                    n_force += 1
                if prefix_len > 0:
                    prefix = line[:prefix_len]
                    sys.stdout.write(' '.join(prefix) + os.linesep)
                else:
                    #err('%d: Length 0 (%d)' % (j+1, split_this))
                    n_full += 1
                    print
                n_examples += 1
                split += step_size
        err('')
        err('Results:')
        err('# examples %d' % (n_examples))
        err('# full %d' % (n_full))
        err('# force %d' % (n_force))

def main():
    """
    Main method
    """
    desc = 'Convert an input file to a prefix file for tuning.'
    parser = ArgumentParser(description=desc)
    parser.add_argument('filename',
                        help='Input file.')
    parser.add_argument('-p','--samples-per-sent',
                        dest='samples',
                        type=int,
                        default=1,
                        help='Number of samples per sentence (default: 1).')
    parser.add_argument('-s', '--step',
                        dest='step',
                        type=int,
                        default=2,
                        help='Step size (default: 2)')
    args = parser.parse_args()
    
    make_prefixes(args.filename, args.samples, args.step)

if __name__ == '__main__':
    main()
