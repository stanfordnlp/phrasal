#!/usr/bin/env python
#
# Convert a database dump created with sql/dumpdb.sql to
# a format appropriate for analysis.
#
import sys
import codecs
import csv
from collections import defaultdict
from os import mkdir
from os.path import basename
from csv_unicode import UnicodeReader
from argparse import ArgumentParser

def mkdir_safe(path, print_error=False):
    """

    Args:
    Returns:
    Raises:
    """
    try:
        mkdir(path)
    except OSError:
        if print_error:
            sys.stderr.write('Warning: directory %s already exists!\n' % (path))        

def process_src(src_file, lang_dict, out_path):
    """
    Args:
    Returns:
    Raises:
    """
    file_desc = open(src_file)
    in_file = UnicodeReader(file_desc)
    n_cols = 0
    for row in in_file:
        if n_cols:
            assert len(row) == n_cols
            lang_code = lang_dict[row[1]]

            # Write source text ordered by id
            file_name = '%s/src.%s.txt' % (out_path, lang_code)
            out_file = codecs.open(file_name,'a',encoding='utf-8')
            out_file.write(row[2].strip() + '\n')
            out_file.close()

            # Write source text metadata ordered by id
            meta_file_name = '%s/src.%s.meta.txt' % (out_path, lang_code)
            out_file = codecs.open(meta_file_name,'a',encoding='utf-8')
            out_file.write('%s\t%s\n' % (row[3],row[4]))
            out_file.close()
        else:
            sys.stderr.write('%s header:\n  %s\n' % (basename(src_file),
                                                     ' '.join(row)))
            n_cols = len(row)
    file_desc.close()

def get_lang_dict(lang_file):
    """
    Args:
      lang_file -- Path to CSV dump of language spec
    Returns:
      lang_dict -- str(id) --> str(lang_code)
    Raises:
    """
    file_desc = open(lang_file)
    in_file = UnicodeReader(file_desc)
    seen_header = False
    lang_dict = {}
    for row in in_file:
        if seen_header:
            lang_dict[row[0]] = row[1]
        seen_header = True
    file_desc.close()
    return lang_dict

def process_tgt(tgt_file, lang_dict, out_path):
    """ Processes user submitted translations. Emits them into three files:

      user_id.tgt.txt -- the target segments
      user_id.tgt.meta.txt -- meta data about each target segment
      user_id.tgt.actionlog.txt -- action log for each target segment

    NOTE: This method also removes duplicate submissions.

    Args:
    Returns:
    Raises:
    """
    file_desc = open(tgt_file)
    in_file = UnicodeReader(file_desc)
    user_src_ids = defaultdict(dict)
    col_headers = None
    for row in in_file:
        if col_headers:
            assert len(row) == len(col_headers)
            src_id = row[1]
            user_id = row[7]
            if user_src_ids[user_id].has_key(src_id):
                sys.stderr.write('Discarding duplicate: user: %s src: %s\n' % (user_id, src_id))
                continue

            # Haven't seen this src_id yet
            lang_code = lang_dict[row[2]]
            file_path = '%s/%s' % (out_path, lang_code)
            mkdir_safe(file_path)
            
            # Write translations ordered by id for this user
            file_name = '%s/%s/%s.tgt.txt' % (out_path, lang_code, user_id)
            out_file = codecs.open(file_name, 'a', encoding='utf-8')
            out_file.write(row[5].strip() + '\n')
            out_file.close()
            
            # Write translation metadata
            file_name = '%s/%s/%s.tgt.meta.txt' % (out_path,
                                                   lang_code,
                                                   user_id)
            out_file = codecs.open(file_name, 'a', encoding='utf-8')
            if len(user_src_ids[user_id].keys()) == 0:
                out_file.write('%s\t%s\t%s\t%s\n' % (col_headers[3],
                                                     col_headers[4],
                                                     col_headers[6],
                                                     col_headers[9]))
            out_file.write('%s\t%s\t%s\t%s\n' % (row[3],
                                                 row[4],
                                                 row[6],
                                                 row[9]))
            out_file.close()
            
            # Write action log for this translation
            file_name = '%s/%s/%s.tgt.actionlog.txt' % (out_path,
                                                        lang_code,
                                                        user_id)
            out_file = codecs.open(file_name, 'a', encoding='utf-8')
            out_file.write(row[8].strip() + '\n')
            out_file.close()

            # Finished processing this src_id
            user_src_ids[user_id][src_id] = 1
        else:
            sys.stderr.write('%s header:\n  %s\n' % (basename(tgt_file),
                                                     ' '.join(row)))
            col_headers = row
    file_desc.close()
    

def process_dump(src_file, tgt_file, lang_file, out_path):
    """ Convert the dump to the following format:

      out_path/
        src.txt
        lang1/
          userid.tgt.txt
          userid.actionlogs.txt
          userid.tgt.meta.txt
        lang2/
          ...
        lang3/
          ...
          
    Args:
    Returns:
    Raises:
    """
    mkdir_safe(out_path, True)
    lang_dict = get_lang_dict(lang_file)
    process_src(src_file, lang_dict, out_path)
    process_tgt(tgt_file, lang_dict, out_path)

def main():
    """ Process command-line arguments.

    Args:
    Returns:
    Raises:
    """
    desc='Convert a tsv file to csv file for SQL import'
    parser=ArgumentParser(description=desc)
    parser.add_argument('src',
                        metavar='src_csv',
                        type=str,
                        help='CSV dump of source documents.')
    parser.add_argument('tgt',
                        metavar='tgt_csv',
                        type=str,
                        help='CSV dump of target documents.')
    parser.add_argument('lang',
                        metavar='lang_csv',
                        type=str,
                        help='CSV dump of language ids.')
    parser.add_argument('output_path',
                        metavar='output_path',
                        type=str,
                        help='Output path for processed dump.')
    args = parser.parse_args()

    process_dump(args.src, args.tgt, args.lang, args.output_path)

    sys.stderr.write('Done!\n')
    
if __name__ == '__main__':
    main()

