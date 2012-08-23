#!/usr/bin/env python
#
# Creates the user data frame from the user survey (in CSV format)
# and the odesk profile pages (raw HTML).
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

import ptm_file_io

# Output data frame definition
Row = namedtuple('Row', 'user_id user_name tgt_lang en_level hourly_rate en_spell en_vocab en_skills en_usage en_ar_trans fr_spell fr_vocab fr_usage en_fr_trans de_spell de_vocab en_de_trans birth_country home_country hours_per_week is_pro')

# Dictionary from raw text to structured fields in Row object
# Format is text flag --> [text offset, Row field]
TEXT_TO_ROW = {'English Skills':[1,'en_level'],
               'Permalink':[1,'hourly_rate'],
               'English Spelling Test':[1,'en_spell'],
               'English To Arabic Translation':[1,'en_ar_trans'],
               'English Vocabulary':[1,'en_vocab'],
               'U.S. Word Usage':[1,'en_usage'],
               'U.S. English Basic Skills':[1,'en_skills'],
               'English To German Translation Skills':[1,'en_de_trans'],
               'German Vocabulary Skills':[1,'de_vocab'],
               'German Spelling':[1,'de_spell'],
               'French Spelling Skills':[1,'fr_spell'],
               'French Vocabulary Skills':[1,'fr_vocab'],
               'English To French Translation Skills':[1,'en_fr_trans'],
               'French Word Usage':[1,'fr_usage']}


TEXT_TO_ROW_V2 = {'English Skills':[0,'en_level'],
               'Contractor Profile':[1,'hourly_rate'],
               'English Spelling':[1,'en_spell'],
               'English To Arabic Translation':[1,'en_ar_trans'],
               'English Vocabulary':[1,'en_vocab'],
               'U.S. Word Usage':[1,'en_usage'],
               'U.S. English Basic Skills':[1,'en_skills'],
               'English To German Translation':[1,'en_de_trans'],
               'German Vocabulary':[1,'de_vocab'],
               'German Spelling':[1,'de_spell'],
               'French Spelling':[1,'fr_spell'],
               'French Vocabulary':[1,'fr_vocab'],
               'English To French Translation Skills':[1,'en_fr_trans'],
               'French Word Usage':[1,'fr_usage']}


def wc(file):
    with codecs.open(file,encoding='utf-8') as infile:
        return len(infile.readlines())

def read_odesk_version2(filename):
    """ Oh no. Lots of nasty rules.
    """
    global TEXT_TO_ROW_V2

    text_flags = TEXT_TO_ROW_V2.keys()
    line_offset = -1
    row_fields = {}
    field_key = None
    soup = BeautifulSoup(open(filename))
    lines = [x.strip() for x in soup.get_text().split('  ') if len(x.strip()) > 0]
    last_line = None
    for line in lines:
        active_flags = [x for x in text_flags if line.startswith(x)]
        if len(active_flags) > 0:
            field_data = TEXT_TO_ROW_V2[active_flags[0]]
            line_offset = field_data[0]
            field_key = field_data[1]
        if line_offset == 0:
            if field_key == 'en_level':
                line_toks = line.split()
                row_fields[field_key] = line_toks[2]
            elif field_key == 'hourly_rate':
                p = re.compile('\$(\d+\.\d+)\s*/\s*hr')
                m = p.search(line)
                rate = m.group(1)
                if len(rate) == 0:
                    raise RuntimeError(line)
                row_fields[field_key] = rate
            else:
                # Odesk tests
                score = None
                if line.split()[1] == 'min':
                    p = re.compile('(\d+\.\d+)')
                    m = p.search(last_line)
                    score = m.group(1)
                else:
                    line_toks = line.split()
                    score = line_toks[0]
                row_fields[field_key] = score
        line_offset -= 1
        last_line = line
    return row_fields


def read_odesk_version1(filename):
    global TEXT_TO_ROW
    # Else do original version
    soup = BeautifulSoup(open(filename))
    lines = [x.strip() for x in soup.get_text().split(os.linesep) if len(x.strip()) > 0]
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
    return row_fields

def langid_to_str(lang_id):
    if lang_id == 1:
        return 'en'
    elif lang_id == 2:
        return 'ar'
    elif lang_id == 3:
        return 'fr'
    elif lang_id == 4:
        return 'de'
    else:
        raise RuntimeError('Unknown language id:' + str(lang_id))

def get_user_row(root_dir, file_list, userdata):
    """ Extract user data from the HTML files

    Args:
    Returns:
     a Row object
    Raises:
    """
    for filename in file_list:
        basefile = splitext(filename)[0]
        # We only look at the master profile page
        if basefile in userdata:
            filename = '%s/%s' % (root_dir, filename)
            n_lines = wc(filename)
            row_fields = None
            if n_lines == 1:
                row_fields = read_odesk_version2(filename)
            else:
                row_fields = read_odesk_version1(filename)

            # Now turn row fields into a Row
            sys.stderr.write('Extracting user data from: %s%s' % (filename, os.linesep))
            
            user_data = userdata[basefile]
            tgt_lang = langid_to_str(int(user_data.lang_other_id))
            user_row = Row(user_id=user_data.user_id,
                           user_name=basefile,
                           tgt_lang=tgt_lang,
                           en_level=row_fields.get('en_level',''),
                           hourly_rate=row_fields.get('hourly_rate',''),
                           en_spell=row_fields.get('en_spell',''),
                           en_vocab=row_fields.get('en_vocab', ''),
                           en_skills=row_fields.get('en_skills',''),
                           en_usage=row_fields.get('en_usage',''),
                           en_ar_trans=row_fields.get('en_ar_trans',''),
                           fr_spell=row_fields.get('fr_spell',''),
                           fr_vocab=row_fields.get('fr_vocab',''),
                           fr_usage=row_fields.get('fr_usage',''),
                           en_fr_trans=row_fields.get('en_fr_trans',''),
                           de_spell=row_fields.get('de_spell',''),
                           de_vocab=row_fields.get('de_vocab',''),
                           en_de_trans=row_fields.get('en_de_trans',''),
                           birth_country=user_data.birth_country_id,
                           home_country=user_data.home_country_id,
                           hours_per_week=user_data.hours_per_week,
                           is_pro=user_data.is_pro_translator)
            return user_row

    # Couldn't extract any user data? That shouldn't happen....
    raise Exception('No relevent files at path: ' + root_dir)        


def make_frame(profile_dir, user_data_file):
    """ Converts HTML in profile_dir, CSV in id_list_file, and
    CSV in user_data_file to an R data frame.

    Args:
    Returns:
    Raises:
    """
    userdata = ptm_file_io.load_raw_user_data(user_data_file)

    rows = []
    for root,dirs,files in os.walk(profile_dir):
        if len(files) > 0:
            has_html = reduce(lambda x,y:x or y,
                              map(lambda x:x.find('html') > 0, files))
            if has_html and root.find('svn') < 0:
                row = get_user_row(root, files, userdata)
                rows.append(row)

    # Output the data frame
    csv_file = UnicodeWriter(sys.stdout, quoting=csv.QUOTE_ALL)
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
    parser.add_argument('user_data',
                        help='SQL dump of tm_userconf.')

    args = parser.parse_args()

    make_frame(args.profile_dir,
               args.user_data)

if __name__ == '__main__':
    main()
    
