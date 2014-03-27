import sys
import codecs
import csv
import os
from os.path import basename,split,join
from csv_unicode import UnicodeReader,UnicodeWriter
from collections import Counter,namedtuple

DumpRow = namedtuple('DumpRow', 'username birth_country resident_of mt_opinion src_proficiency tgt_proficiency src_doc tgt_lang interface order create_time start_time end_time complete training valid text log')

# Convenience methods
str2bool = lambda x:True if x == '1' else False
genre_from_url = lambda x:basename(split(x)[0])

def url2doc(raw_url):
    """
    Extract a URL from a pathname.
    """
    filename = basename(raw_url).replace('.json','').replace('src','tgt')
    genre = genre_from_url(raw_url)
    return join(genre,filename)

def load_middleware_dump(filename):
    """
    Load a middleware dump.

    Args:
    Returns: A list of DumpRow namedtuple objects.
    Raises:
    """
    row_list = []
    with open(filename) as in_file:
        r = UnicodeReader(in_file, delimiter='|', quoting = csv.QUOTE_NONE)
        for row in map(DumpRow._make, r):
            complete = str2bool(row.complete)
            valid = str2bool(row.valid)
            training = str2bool(row.training)
            if row.username == 'rayder441':
                # Debug/test username
                continue
            elif not valid:
                sys.stderr.write('WARNING: Skipping invalid row: %s %s%s' %(row.username, row.src_doc, os.linesep))
                continue
            elif not complete:
                sys.stderr.write('Skipping incomplete row: %s %s%s' %(row.username, row.src_doc, os.linesep))
                continue
            elif training:
                continue
            row_list.append(row)
    return row_list

def write_rows_to_csv(row_list, filename):
    """
    Write a list of namedtuple objects to a (unicode) CSV file.
    
    Args:
     row_list A list of namedtuple objects
    Returns:
    Raises:
    """
    with open(filename,'w') as out_file:
        csv_file = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)
        write_header = True
        for row in row_list:
            if write_header:
                write_header = False
                csv_file.writerow(list(row._fields))
            csv_file.writerow([x for x in row._asdict().itervalues()])

def load_references(filename_list):
    """
    Load references from a list of filenames.

    Args:
    Returns:
    Raises:
    """
    filename_to_lines = {}
    for filename in filename_list:
        doc_id = url2doc(filename)
        with codecs.open(filename, encoding='utf-8') as infile:
            filename_to_lines[doc_id] = [x.strip() for x in infile.readlines()]
    return filename_to_lines
            
def segment_times_from_log(log):
    """
    Accumulates the time spent focused on each segment
    in a user log.

    Args:
    Returns: a dictionary of segment_id -> seconds
    Raises:
    """
    segment_to_time = Counter()
    focus_segment = -1
    focus_time = 0.0
    for entry in log:
        if 'keyValues' in entry and 'focusSegment' in entry['keyValues']:
            new_focus_segment = entry['keyValues']['focusSegment']
            if new_focus_segment:
                new_focus_segment = int(entry['keyValues']['focusSegment'])
                new_focus_time = float(entry['time'])
                if focus_segment >= 0:
                    segment_to_time[focus_segment] += (new_focus_time - focus_time)
                focus_segment = new_focus_segment
                focus_time = new_focus_time
    end_time = float(log[-1]['time'])
    segment_to_time[focus_segment] += (end_time - focus_time)
    return segment_to_time

def initial_translations_from_pe_log(log):
    """
    Extract the initial MT suggestions from a pe log.

    Args:
    Returns: a segment_id -> translation dictionary
    Raises:
    """
    segment_to_translation = {}
    for entry in log:
        if 'keyValues' in entry and 'userText' in entry['keyValues']:
            segment_id = int(entry['subElement'])
            if not segment_id in segment_to_translation:
                segment_to_translation[segment_id] = entry['keyValues']['userText'].strip()
    return segment_to_translation

def initial_translations_from_imt_log(log):
    """
    Extract the initial MT suggestions from an imt log

    Args:
    Returns: a segment_id -> translation dictionary
    Raises:
    """
    segment_to_translation = {}
    for entry in log:
        if 'keyValues' in entry and 'bestTranslation' in entry['keyValues']:
            segment_id = int(entry['subElement'])
            if not segment_id in segment_to_translation:
                segment_to_translation[segment_id] = ' '.join(entry['keyValues']['bestTranslation'])
    return segment_to_translation

def source_segments_from_log(log):
    """

    Args:
    Returns:
    Raises:
    """
    segment_to_text = {}
    for entry in log:
        if 'keyValues' in entry and 'docId' in entry['keyValues'] and entry['keyValues']['docId']:
            for line_id,src_item in entry['keyValues']['segments'].iteritems():
                line_id = int(line_id)
                segment_to_text[line_id] = ' '.join(src_item['tokens'])
            break
    return segment_to_text

def final_translations_from_dict(segment_to_txt):
    """
    Extract the final set of translations from a DumpRow.

    Args:
    Returns:
    Raises:
    """
    id_to_segment = {}
    for line_id in sorted([int(x) for x in segment_to_txt.keys()]):
        id_to_segment[line_id] = segment_to_txt[str(line_id)].strip()
    return id_to_segment

