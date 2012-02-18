#!/usr/bin/env python
#
# Convert a tsv file to a csv file suitable for 
# database table.
#
# Output table format matches tmapp.tm.models.SourceTxt:
#
# CREATE TABLE "tm_sourcetxt" (
# "id" serial NOT NULL PRIMARY KEY,
# "lang_id" integer NOT NULL REFERENCES "tm_languagespec" ("id") DEFERRABLE INITIALLY DEFERRED,
# "ui_id" integer NOT NULL REFERENCES "tm_uispec" ("id") DEFERRABLE INITIALLY DEFERRED,
# "txt" text NOT NULL,
# "seg" varchar(100) NOT NULL,
# "doc" varchar(200) NOT NULL);
#
import sys
import codecs
import csv
from os.path import basename
from csv_unicode import UnicodeWriter
from argparse import ArgumentParser

def generate_sql(pk,lpk,upk,tsv_file):
    in_file = codecs.open(tsv_file,encoding='utf-8')
    out_file_name = basename(tsv_file) + '.csv'
    out_file = open(out_file_name,'w')
    csv_out = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)

    # Write the table header to match this table
    header_row = ['id','lang_id','ui_id','txt','seg','doc']
    csv_out.writerow(header_row)
    n_lines = 0
    for line in in_file:
        n_lines += 1
        (doc_id, seg_id, txt) = line.strip().split('\t')
        row = [str(pk), str(lpk), str(upk), txt, seg_id, doc_id]
        csv_out.writerow(row)
        pk += 1
    in_file.close()
    out_file.close()
    return n_lines

def main():
    desc='Convert a tsv file to csv file for SQL import'
    parser=ArgumentParser(description=desc)
    parser.add_argument('first_id',
                        metavar='pk',
                        type=int,
                        help='First primary key for this dataset.')
    parser.add_argument('lpk',
                        metavar='lang_pk',
                        type=int,
                        help='Primary key of this language')
    parser.add_argument('upk',
                        metavar='ui_pk',
                        type=int,
                        help='Primary key of this UI')
    parser.add_argument('tsv_file',
                        metavar='tsv_file',
                        type=str,
                        help='Tab-separated file to convert')
    args = parser.parse_args()

    n_lines = generate_sql(args.first_id,args.lpk,args.upk,args.tsv_file)

    sys.stderr.write('Done! Read and wrote %d lines.\n' % (n_lines))

if __name__ == '__main__':
    main()

