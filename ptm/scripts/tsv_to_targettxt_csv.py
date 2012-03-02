#!/usr/bin/env python
#
# Convert a tsv file to a csv file suitable for 
# the tm_targettxt table.
#
# Output table format matches tmapp.tm.models.TargetTxt:
#
#CREATE TABLE "tm_targettxt" (
#    "id" serial NOT NULL PRIMARY KEY,
#    "src_id" integer NOT NULL REFERENCES "tm_sourcetxt" ("id") DEFERRABLE INITIALLY DEFERRED,
#    "lang_id" integer NOT NULL REFERENCES "tm_languagespec" ("id") DEFERRABLE INITIALLY DEFERRED,
#    "is_machine" boolean NOT NULL,
#    "date" timestamp with time zone NOT NULL,
#    "txt" text NOT NULL
#)
#;
#
import sys
import codecs
import csv
from os.path import basename
from csv_unicode import UnicodeWriter
from argparse import ArgumentParser
from datetime import datetime

def generate_csv(pk,lpk,spk,tsv_file):
    """ Convert tsv to csv. Column order is:
        header_row = ['id','src_id','lang_id','is_machine','date','txt']

    Args:
    Returns:
    Raises:
    """
    in_file = codecs.open(tsv_file,encoding='utf-8')
    out_file_name = basename(tsv_file) + '.csv'
    out_file = open(out_file_name,'w')
    csv_out = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)
    n_lines = 0
    date_str = str(datetime.now())
    for line in in_file:
        n_lines += 1
        (doc_id, seg_id, txt) = line.strip().split('\t')
        row = [str(pk), str(spk), str(lpk), str(True), date_str, txt]
        csv_out.writerow(row)
        pk += 1
        spk += 1
    in_file.close()
    out_file.close()
    return n_lines

def main():
    desc='Convert a tsv file to csv file for SQL import'
    parser=ArgumentParser(description=desc)
    parser.add_argument('pk',
                        metavar='pk',
                        type=int,
                        help='First primary key for this dataset.')
    parser.add_argument('spk',
                        metavar='src_pk',
                        type=int,
                        help='First source pk for this doc')
    parser.add_argument('lpk',
                        metavar='lang_pk',
                        type=int,
                        help='Primary key of this language')
    parser.add_argument('tsv_file',
                        metavar='tsv_file',
                        type=str,
                        help='Tab-separated file to convert')
    args = parser.parse_args()

    n_lines = generate_csv(args.pk,args.lpk,args.spk,args.tsv_file)

    sys.stderr.write('Done! Read and wrote %d lines.\n' % (n_lines))

if __name__ == '__main__':
    main()

