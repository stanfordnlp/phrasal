#!/usr/bin/env python
#
# Compute the source or target OOV rate
# given a phrase table and a file.
#
# Author: Spence Green
import sys
import re
import codecs
from argparse import ArgumentParser
import gzip
from os.path import basename,splitext
import os

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)
stderr = lambda x:sys.stderr.write(x + os.linesep)

# Delimiter of the phrase table
DELIM = '|||'

def get_reader(fname):
    (root,ext) = splitext(basename(fname))
    if ext == '.gz':
        return codecs.getreader('utf-8')(gzip.open(fname))
    else:
        return codecs.open(fname, encoding='utf-8')

def load_pt(phrase_table, do_target):
    """
    """
    stderr('Loading: ' + phrase_table)
    with get_reader(phrase_table) as in_file:
        index = 1 if do_target else 0
        vocab = set()
        for line in in_file:
            fields = line.strip().split(DELIM)
            for token in fields[index].split():
                vocab.add(token)
        return vocab

def load_file(target_file):
    """
    """
    stderr('Loading: ' + target_file)
    with get_reader(target_file) as in_file:
        vocab = set()
        for line in in_file:
            for token in line.strip().split():
                vocab.add(token)
        return vocab
    
def compute_oov(target_file, phrase_table, do_target, do_word):
    """
    Compute the OOV rate.
    """
    phrase_table_vocab = load_pt(phrase_table, do_target)
    file_vocab = load_file(target_file)
    oov_set = file_vocab - phrase_table_vocab
    oov_rate = float(len(oov_set)) / float(len(file_vocab))
    print 'OOV items'
    for word in oov_set:
        print word

    print
    print '|file vocab| = %d' % (len(file_vocab))
    print '|pt vocab| = %d' % (len(phrase_table_vocab))
    print 'OOV rate: {:.2%}'.format(oov_rate)

def main():
    """
    """
    desc='Compute the OOV rate given a file and a phrase table'
    parser = ArgumentParser(description=desc)
    parser.add_argument('target_file',
                        help='Compute OOV rate relative to this file.')
    parser.add_argument('phrase_table',
                        help='Phrasal phrase table file')
    parser.add_argument('-t','--target',
                        dest='do_target',
                        action='store_true',
                        help='Compute relative to the target side.')
    parser.add_argument('-w','--word',
                        dest='do_word',
                        action='store_true',
                        help='Compute word-type OOV rate instead of token-level.')
    args = parser.parse_args()

    compute_oov(args.target_file,
                args.phrase_table,
                args.do_target,
                args.do_word)
    
if __name__ == '__main__':
    main()
