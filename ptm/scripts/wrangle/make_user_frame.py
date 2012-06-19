#!/usr/bin/env python
#
# Creates the user data frame from various sources.
#
#
#
import sys
import re
import codecs
import os
import csv
from bs4 import BeautifulSoup
from os.path import splitext
from collections import namedtuple
from csv_unicode import UnicodeReader,UnicodeWriter
from argparse import ArgumentParser

# User data collected from the survey
UserDataRow = namedtuple('UserDataRow', 'user_id birth_country home_country hours_per_week is_pro')

# Output data frame definition
Row = namedtuple('Row', 'user_id user_name en_level hourly_rate en_spell en_vocab en_skills en_usage en_ar_trans fr_spell fr_vocab fr_usage en_fr_trans de_spell de_vocab en_de_trans birth_country home_country hours_per_week is_pro')

# Dictionary from raw text to structured fields in Row object
# Format is text flag --> [text offset, Row field]
TEXT_TO_ROW = {'English Skills':[1,'en_level'],
               'Permalink':[1,'hourly_rate'],
               'English Spelling Test':[2,'en_spell'],
               'English To Arabic Translation':[2,'en_ar_trans'],
               'English Vocabulary Test':[2,'en_vocab'],
               'U.S. Word Usage Test':[2,'en_usage'],
               'U.S. English Basic Skills':[2,'en_skills'],
               'English To German Translation Skills':[2,'en_de_trans'],
               'German Vocabulary Skills Test':[2,'de_vocab'],
               'German Spelling Test':[2,'de_spell'],
               'French Spelling Skills Test':[2,'fr_spell'],
               'French Vocabulary Skills Test':[2,'fr_vocab'],
               'English To French Translation Skills Test':[2,'en_fr_trans'],
               'French Word Usage Test':[2,'fr_usage']}


def make_id_dict(id_list_file, user_data_file):
    """ Creates a dictionary of the form:

       username -> UserDataRow

    Args:
    Returns:
    Raises:
    """
    # Read in userid -> username mapping
    id_dict = {}
    with open(id_list_file) as in_file:
        csv_file = csv.reader(in_file)
        for row in csv_file:
            assert len(row) == 2
            id_dict[row[0]] = row[1]

    # Now create a dictionary of the form username -> UserDataRow
    username_dict = {}
    with open(user_data_file) as in_file:
        csv_file = csv.reader(in_file)
        csv_file.next() # Skip header
        for row in map(UserDataRow._make, csv_file):
            username = id_dict[row.user_id]
            username_dict[username] = row

    return username_dict


def get_user_row(root_dir, file_list, id_dict):
    """ Extract user data from the HTML files

    Args:
    Returns:
     a Row object
    Raises:
    """
    global TEXT_TO_ROW
    
    for filename in file_list:
        basefile = splitext(filename)[0]
        if id_dict.has_key(basefile):
            soup = BeautifulSoup(open(root_dir+'/'+filename))
            lines = [x.strip() for x in soup.get_text().split(os.linesep) if len(x.strip()) > 0]
            # Pull relevant fields from the file
            text_flags = TEXT_TO_ROW.keys()
            row_fields = {}
            line_to_extract = -1
            line_field = None
            for i,line in enumerate(lines):
                if i == line_to_extract:
                    line = re.sub('%|\$|/hr','',line)
                    row_fields[line_field] = line.strip()

                active_flags = [x for x in text_flags if line.startswith(x)]
                if len(active_flags) > 0:
                    field_data = TEXT_TO_ROW[active_flags[0]]
                    line_to_extract = i + field_data[0]
                    line_field = field_data[1]

            # Now turn row fields into a Row
            print 'Extracting user data from:',root_dir
            
            user_data = id_dict[basefile]
            user_row = Row(user_id=user_data.user_id,
                           user_name=basefile,
                           en_level=row_fields['en_level'],
                           hourly_rate=row_fields['hourly_rate'],
                           en_spell=row_fields['en_spell'] if row_fields.has_key('en_spell') else '',
                           en_vocab=row_fields['en_vocab'] if row_fields.has_key('en_vocab') else '',
                           en_skills=row_fields['en_skills'] if row_fields.has_key('en_skills') else '',
                           en_usage=row_fields['en_usage'] if row_fields.has_key('en_usage') else '',
                           en_ar_trans=row_fields['en_ar_trans'] if row_fields.has_key('en_ar_trans') else '',
                           fr_spell=row_fields['fr_spell'] if row_fields.has_key('fr_spell') else '',
                           fr_vocab=row_fields['fr_vocab'] if row_fields.has_key('fr_vocab') else '',
                           fr_usage=row_fields['fr_usage'] if row_fields.has_key('fr_usage') else '',
                           en_fr_trans=row_fields['en_fr_trans'] if row_fields.has_key('en_fr_trans') else '',
                           de_spell=row_fields['de_spell'] if row_fields.has_key('de_spell') else '',
                           de_vocab=row_fields['de_vocab'] if row_fields.has_key('de_vocab') else '',
                           en_de_trans=row_fields['en_de_trans'] if row_fields.has_key('en_de_trans') else '',
                           birth_country=user_data.birth_country,
                           home_country=user_data.home_country,
                           hours_per_week=user_data.hours_per_week,
                           is_pro=user_data.is_pro)
            return user_row

    # Couldn't extract any user data? That shouldn't happen....
    raise Exception('No relevent files at path: ' + root_dir)        
            

def make_frame(profile_dir, id_list_file, user_data_file, outfile_name):
    """ Converts HTML in profile_dir, CSV in id_list_file, and
    CSV in user_data_file to an R data frame.

    Args:
    Returns:
    Raises:
    """
    id_dict = make_id_dict(id_list_file, user_data_file)

    rows = []
    for root,dirs,files in os.walk(profile_dir):
        if len(files) > 0:
            has_html = reduce(lambda x,y:x or y, map(lambda x:x.find('html')>0, files))
            if has_html and root.find('svn')<0:
                row = get_user_row(root, files, id_dict)
                rows.append(row)

    # Output the data frame
    with open(outfile_name,'w') as out_file:
        csv_file = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)
        write_header = True
        for row in rows:
            if write_header:
                write_header = False
                csv_file.writerow(list(row._fields))
            csv_file.writerow([x for x in row._asdict().itervalues()])
                          

def main():
    desc='Makes user data frame from various data sources'
    parser=ArgumentParser(description=desc)
    parser.add_argument('profile_dir',
                        metavar='directory',
                        help='User profile directory.')
    parser.add_argument('id_list',
                        help='Mapping from user names to ids.')
    parser.add_argument('user_data',
                        help='SQL dump of tm_userconf.')
    parser.add_argument('output_file',
                        help='Name of R data frame output file.')

    args = parser.parse_args()

    make_frame(args.profile_dir,
               args.id_list,
               args.user_data,
               args.output_file)

if __name__ == '__main__':
    main()
    
