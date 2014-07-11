#!/usr/bin/env python
#
# Create a frame of segment-level translation that includes
# timing and automatic quality information for each
# segment in each translation session.
#
# Author: Spence Green
#
import sys
import codecs
import json
import os
import csv
from os.path import basename
from collections import namedtuple,defaultdict,Counter

# Local libraries
import imt_utils
import edit_distance

stderr = lambda x:sys.stderr.write(str(x) + os.linesep)

OutputRow = namedtuple('OutputRow', 'time last_time username user_resident_of user_mt_opinion user_gender interface doc_id segment_id src_len order session_order interface_order genre mt_sbleu subject_sbleu subject_hbleu is_valid edit_distance')

args = sys.argv[1:]
if len(args) < 5:
    stderr('Usage: python %s dump_file tgt_lang gender_csv out_file [bleu_directory] [prefix]' % (basename(sys.argv[0])))
    stderr('bleu_directory : output of the extract translations script')
    sys.exit(-1)

# Parse the command line
dump_file = args[0]
target_lang = args[1]
gender_file = args[2]
out_file_name = args[3]
bleu_directory = args[4] if len(args) > 4 else None
quality_prefix = args[5] if len(args) > 5 else 'sbleu'

user_to_gender = imt_utils.load_gender_csv(gender_file)
user_doc_to_sbleu = imt_utils.load_sbleu_files(bleu_directory, quality_prefix+'_ref') if bleu_directory else None
user_doc_to_hbleu = imt_utils.load_sbleu_files(bleu_directory, quality_prefix+'_mt') if bleu_directory else None
dump_row_list = imt_utils.load_middleware_dump(dump_file, target_lang)
output_row_list = []
total_translation_time = defaultdict(Counter)
user_order_to_time = defaultdict(list)

# Load and process the database dump
session_order = 0
condition_order = 0
last_user = None
last_condition = None
for i,row in enumerate(dump_row_list):
    if i > 0 and i % 10 == 0:
        sys.stdout.write('.')
        if i % 800 == 0:
            print
    tgt_text_dict = json.loads(row.text)
    segment_to_tgt_txt = imt_utils.final_translations_from_dict(tgt_text_dict)
    doc_name = imt_utils.url2doc(row.src_doc)
    log = json.loads(row.log)
    segment_to_time = imt_utils.segment_times_from_log(log)
    segment_to_mt = imt_utils.initial_translations_from_imt_log(log) if row.interface == 'imt' else imt_utils.initial_translations_from_pe_log(log)
    segment_to_src_txt = imt_utils.source_segments_from_log(log)
    doc_name = imt_utils.url2doc(row.src_doc)
    doc_genre = imt_utils.genre_from_url(row.src_doc)

    for line_id in sorted(segment_to_tgt_txt.keys()):
        # TODO: hack for a user with bad logs
        edist = 0
        if line_id in segment_to_mt:
            mt_tgt_txt = segment_to_mt[line_id]
            user_tgt_txt = segment_to_tgt_txt[line_id]
            edist = edit_distance.dameraulevenshtein(mt_tgt_txt,
                                                     user_tgt_txt,
                                                     True)
        segment_id = '%s:%d' % (doc_name, line_id)
        time = segment_to_time[line_id]
        total_translation_time[row.username][row.interface] += time
        total_translation_time[row.username][row.interface+'_nseg'] += 1
        order = int(row.order)
        if not (last_user or last_condition):
            last_user = row.username
            last_condition = row.interface
        if row.username != last_user:
            session_order = 0
            condition_order = 0
        elif last_condition != row.interface:
            condition_order = 0
        time_key = '%s:%d' % (row.username,order)
        user_order_to_time[time_key].append(time)
        output_row = OutputRow(time=str(time),
                               last_time=str(0.01),
                               username=row.username,
                               user_resident_of=row.resident_of,
                               user_mt_opinion=row.mt_opinion,
                               user_gender=user_to_gender[row.username],
                               interface=row.interface,
                               doc_id=doc_name,
                               segment_id=segment_id,
                               src_len=str(len(segment_to_src_txt[line_id].split())),
                               order=str(order),
                               session_order=str(session_order),
                               interface_order=str(condition_order),
                               genre=doc_genre,
                               mt_sbleu=user_doc_to_sbleu['MT'][segment_id],
                               subject_sbleu=user_doc_to_sbleu[row.username][segment_id],
                               subject_hbleu=user_doc_to_hbleu[row.username][segment_id],
                               is_valid='T' if int(row.valid) == 1 else 'F',
                               edit_distance=str(edist))
        output_row_list.append(output_row)
        session_order += 1
        condition_order += 1
        last_condition = row.interface
        last_user = row.username

# Fill in the previous time column
for i in xrange(len(output_row_list)):
    row = output_row_list[i]
    order = int(row.order)
    time_key = '%s:%d' % (row.username,order)
    doc_name,line_id = row.segment_id.split(':')
    prev_line_id = int(line_id) - 1
    if prev_line_id >= 0:
        last_time = user_order_to_time[time_key][prev_line_id] + 0.0001
        output_row_list[i] = row._replace(last_time=str(last_time))
    else:
        # Look back to previous document
        time_key = '%s:%d' % (row.username,order-1)
        if time_key in user_order_to_time:
            last_time = user_order_to_time[time_key][-1] + 0.0001
            output_row_list[i] = row._replace(last_time=str(last_time))
        else:
            # This is the first segment
            pass
    
print
print 'Writing to:',out_file_name
imt_utils.write_rows_to_csv(output_row_list, out_file_name)

print
print 'Total translation time'
for username in sorted(total_translation_time.keys()):
    print '%s\tpe: %.3f (%d)\timt: %.3f (%d)' % (username,
                                 total_translation_time[username]['pe'],
                                 total_translation_time[username]['pe_nseg'],
                                 total_translation_time[username]['imt'],
                                 total_translation_time[username]['imt_nseg'])
