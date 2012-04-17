#!/usr/bin/env python
#
# Convert a database dump created with sql/dumpdb.sql to
# a format appropriate for analysis.
#
import sys
import codecs
import csv
import os
from collections import defaultdict,namedtuple
from os import mkdir
from os.path import basename
from csv_unicode import UnicodeReader
from argparse import ArgumentParser

# Row in the source dump file
SrcInputRow = namedtuple('SrcRow','id,lang_id,txt,doc,seg')

# Row in the target dump file
TgtInputRow = namedtuple('TgtRow','id,src_id,lang_id,is_machine,date,txt,ui_id,user_id,action_log,is_valid')

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
            sys.stderr.write('Warning: directory %s already exists!%s' % (path,os.linesep))        

def process_src(src_file, lang_dict, out_path):
    """
    Args:
    Returns:
    Raises:
    """
    with open(src_file) as file_desc:
        src_lens = {}
        for i,row in enumerate(map(SrcInputRow._make, UnicodeReader(file_desc))):
            if i == 0:
                continue
            
            lang_code = lang_dict[row.lang_id]

            # Write source text ordered by id
            file_name = '%s/src.%s.txt' % (out_path, lang_code)
            with codecs.open(file_name,'a',encoding='utf-8') as out_file:
                src_lens[row.id] = str(len(row.txt.split()))
                out_file.write(row.txt.strip() + os.linesep)

            # Write source text metadata ordered by id
            meta_file_name = '%s/src.%s.meta.txt' % (out_path, lang_code)
            with codecs.open(meta_file_name,'a',encoding='utf-8') as out_file:
                out_file.write('%s\t%s%s' % (row.doc,row.seg,os.linesep))
    return src_lens

def get_lang_dict(lang_file):
    """
    Args:
      lang_file -- Path to CSV dump of language spec
    Returns:
      lang_dict -- str(id) --> str(lang_code)
    Raises:
    """
    with open(lang_file) as file_desc:
        in_file = UnicodeReader(file_desc)
        seen_header = False
        lang_dict = {}
        for row in in_file:
            if seen_header:
                lang_dict[row[0]] = row[1]
            seen_header = True

        return lang_dict

def process_tgt(tgt_file, lang_dict, src_lens, out_path):
    """ Processes user submitted translations. Emits them into three files:

      user_id.tgt.txt -- the target segments
      user_id.tgt.meta.txt -- meta data about each target segment
      user_id.tgt.actionlog.txt -- action log for each target segment

    NOTE: This method also removes duplicate submissions.

    Args:
    Returns:
    Raises:
    """
    with open(tgt_file) as file_desc:
        user_src_ids = defaultdict(dict)
        for i,row in enumerate(map(TgtInputRow._make, UnicodeReader(file_desc))):
            if i == 0:
                # Headers
                continue
            if user_src_ids[row.user_id].has_key(row.src_id):
                sys.stderr.write('Discarding duplicate: user: %s src: %s%s' % (row.user_id, row.src_id, os.linesep))
                continue

            # Haven't seen this src_id yet
            lang_code = lang_dict[row.lang_id]
            file_path = '%s/%s' % (out_path, lang_code)
            mkdir_safe(file_path)
            
            # Write translations ordered by id for this user
            file_name = '%s/%s/%s.tgt.txt' % (out_path,
                                              lang_code,
                                              row.user_id)
            with codecs.open(file_name, 'a', encoding='utf-8') as out_file:
                out_file.write(row.txt.strip() + os.linesep)
            
            # Write translation metadata
            meta_name = '%s/%s/%s.tgt.meta.txt' % (out_path,
                                                   lang_code,
                                                   row.user_id)
            with codecs.open(meta_name, 'a', encoding='utf-8') as meta_file:
                src_len = src_lens[row.src_id]
                tgt_len = str(len(row.txt.split()))
                if len(user_src_ids[row.user_id].keys()) == 0:
                    # Write the column headers
                    meta_file.write('%s\t%s\t%s\t%s\t%s\t%s%s' % (TgtInputRow._fields[3],
                                                          TgtInputRow._fields[4],
                                                          TgtInputRow._fields[6],
                                                          TgtInputRow._fields[9],
                                                                  'src_len',
                                                                  'tgt_len',
                                                          os.linesep))
                meta_file.write('%s\t%s\t%s\t%s\t%s\t%s%s' % (row.is_machine,
                                                              row.date,
                                                              row.ui_id,
                                                              row.is_valid,
                                                              src_len,
                                                              tgt_len,
                                                              os.linesep))
            
            # Write action log for this translation
            log_name = '%s/%s/%s.tgt.actionlog.txt' % (out_path,
                                                       lang_code,
                                                       row.user_id)
            with codecs.open(log_name, 'a', encoding='utf-8') as log_file:
                log_file.write(row.action_log.strip() + os.linesep)
                
            # Finished processing this src_id
            user_src_ids[row.user_id][row.src_id] = 1

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
    src_lens = process_src(src_file, lang_dict, out_path)
    process_tgt(tgt_file, lang_dict, src_lens, out_path)

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
