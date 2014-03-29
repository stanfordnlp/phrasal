#!/usr/bin/env python
#
# Extract translation information from a database dump.
#
import sys
import codecs
import csv
import json
import os
import re
import shutil
from argparse import ArgumentParser
from os.path import basename,split,join,exists
from datetime import datetime
from collections import namedtuple,defaultdict
from csv_unicode import UnicodeReader
import imt_utils
from imt_utils import segment_times_from_log,url2doc,initial_translations_from_imt_log,initial_translations_from_pe_log,load_references

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

stderr = lambda x:sys.stderr.write(str(x) + os.linesep)

SYSTEM_DIR='translations'

def console_dump(doc_to_ref, doc_to_user_txt, doc_to_user_time):
    """
    """
    for doc_name in sorted(doc_to_ref.keys()):
        for i,ref in enumerate(doc_to_ref[doc_name]):
            doc_id = '%s:%d' % (doc_name, i)
            print doc_id
            print '%s\t%s\t%.3f\t%s' % ('ref', 'ref', 0.0, ref)
            for user_id in sorted(doc_to_user_txt[doc_id].keys()):
                username,ui = user_id.split(':')
                tgt_txt = doc_to_user_txt[doc_id][user_id]
                doc_time = doc_to_user_time[doc_id][user_id]
                tgt_txt = re.sub('\s+', ' ', tgt_txt)
                print '%s\t%s\t%s\t%s' % (username, ui, doc_time, tgt_txt)
            print

def get_fd(fd_dict, key, filepath):
    """
    """
    fd = fd_dict[key] if key in fd_dict else codecs.open(filepath,'w',encoding='utf-8')
    fd_dict[key] = fd
    return fd
            
def output_system_files(doc_to_ref, doc_to_user_txt, username_set):
    """
    """
    if exists(SYSTEM_DIR):
        shutil.rmtree(SYSTEM_DIR)
    os.mkdir(SYSTEM_DIR)
    ref_name = 'ref0'
    refpath = join(SYSTEM_DIR, ref_name)
    docid_file = codecs.open(join(SYSTEM_DIR,ref_name+'.ids'),'w',encoding='utf-8')
    fd_dict = {}
    for ref_doc in sorted(doc_to_ref.keys()):
        seen_username_set = set()
        num_lines = 0
        for i,segment in enumerate(doc_to_ref[ref_doc]):
            num_lines = i+1
            fd = get_fd(fd_dict, ref_name, refpath)
            fd.write(segment.strip() + os.linesep)
            doc_id = '%s:%d' % (ref_doc, i)
            docid_file.write(doc_id + os.linesep)
            for user_id in sorted(doc_to_user_txt[doc_id].keys()):
                username,interface = user_id.split(':')
                seen_username_set.add(username)
                filepath = join(SYSTEM_DIR, username+'.trans')
                fd = get_fd(fd_dict, username, filepath)
                tgt_txt = doc_to_user_txt[doc_id][user_id]
                fd.write(tgt_txt.strip() + os.linesep)
                props_path = join(SYSTEM_DIR, username+'.props')
                fd = get_fd(fd_dict, username+'_prop', props_path)
                fd.write('Domain=%s%s' % (interface,os.linesep))
        missing_users = username_set - seen_username_set
        if len(missing_users) > 0:
            stderr('%s missing %s' % (ref_doc, str(missing_users)))
        for username in missing_users:
            filepath = join(SYSTEM_DIR, username+'.trans')
            fd = get_fd(fd_dict, username, filepath)
            props_path = join(SYSTEM_DIR, username+'.props')
            fd_props = get_fd(fd_dict, username+'_prop', props_path)
            for i in xrange(num_lines):
                fd.write(os.linesep)
                fd_props.write('Domain=unk' + os.linesep)
    docid_file.close()
    for key,fd in fd_dict.iteritems():
        fd.close()

def extract_translations(dump_file,
                         target_lang,
                         ref_file_list,
                         output_to_console):
    """
    """
    stderr('Loading references...')
    doc_to_ref = load_references(ref_file_list)
    doc_to_timing = defaultdict(dict)
    session_id = 0

    stderr('Loading database dump...')
    doc_to_user_txt = defaultdict(dict)
    doc_to_user_time = defaultdict(dict)
    dump_row_list = imt_utils.load_middleware_dump(dump_file, target_lang)
    username_set = set()
    for row in dump_row_list:
        username_set.add(row.username)
        text_dict = json.loads(row.text)
        segment_to_tgt_txt = imt_utils.final_translations_from_dict(text_dict)
        doc_name = url2doc(row.src_doc)
        log = json.loads(row.log)
        segment_to_time = segment_times_from_log(log)
        segment_to_mt = initial_translations_from_imt_log(log) if row.interface == 'imt' else initial_translations_from_pe_log(log)
        for line_id in sorted(segment_to_tgt_txt.keys()):
            doc_id = '%s:%d' % (doc_name, line_id)
            user_id = row.username + ':' + row.interface
            mt_id = 'MT:mt'
            doc_to_user_txt[doc_id][user_id] = segment_to_tgt_txt[line_id]
            doc_to_user_time[doc_id][user_id] = segment_to_time[line_id]
            doc_to_user_txt[doc_id][mt_id] = segment_to_mt[line_id]
            doc_to_user_time[doc_id][mt_id] = 0.0

    # Output the results
    output_system_files(doc_to_ref, doc_to_user_txt, username_set)
    if output_to_console:
        console_dump(doc_to_ref, doc_to_user_txt, doc_to_user_time)

def main():
    """
    """
    desc='Extract translations from a TM middleware dump'
    parser=ArgumentParser(description=desc)
    parser.add_argument('dump_file',
                        help='The TM middleware dump file.')
    parser.add_argument('target_lang',
                        help='The target language.')
    parser.add_argument('ref_file_list',
                        metavar='ref_file',
                        nargs='+',
                        help='The translation reference files.')
    parser.add_argument('-c','--console_output',
                        dest='output_to_console',
                        action='store_true',
                        help='Directory containing counts files for computing lexical frequency (generated with giga_vocab.sh).')
    args = parser.parse_args()

    extract_translations(args.dump_file,
                         args.target_lang,
                         args.ref_file_list,
                         args.output_to_console)
        
if __name__ == '__main__':
    main()
