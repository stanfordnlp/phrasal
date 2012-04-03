#!/usr/bin/env python
#
# Converts js action logs to CSV format.
#
import sys
import re
import codecs
import os
import csv
from os.path import basename
from collections import defaultdict,namedtuple
from csv_unicode import UnicodeWriter
from argparse import ArgumentParser

Event = namedtuple('Event', 'sourceid userid time event_name event_class target x y key')

# Maps control keycodes to human readable names
control_keycode_to_str = None

# Maps js events to a type classification
event_to_class = {'blur': 'view',
                  'focus':'view',
                  'focusin':'view',
                  'focusout':'view',
                  'load':'browser',
                  'resize':'view',
                  'scroll':'view',
                  'unload':'browser',
                  'click':'mouse',
                  'dblclick':'mouse',
                  'keydown':'keyboard-control',
                  'mousedown':'mouse',
                  'mouseup':'mouse',
                  'mouseover':'mouse',
                  'mouseout':'mouse',
                  'mouseenter':'mouse',
                  'mouseleave':'mouse',
                  'change':'mouse',
                  'select':'mouse',
                  'submit':'mouse',
                  'keypress':'keyboard-input',
                  'error':'browser'}

# Convert CSS ids from the UI to human readable names
css_id_to_str = {'id_txt':'Target Textbox',
                 'src-display':'Source Textbox',
                 'form-tgt-submit':'Submit Button'}

# The whitespace tokenized source document
# src_doc[0][1] -> second token for the first sentence in the document
src_doc = None

def read_srcdoc(filename):
    """ Read and tokenize the source document.

    Args:
    Returns:
    Raises:
    """
    global src_doc
    src_doc = []
    with codecs.open(filename,encoding='utf-8') as in_file:
        for line in in_file:
            src_doc.append(line.strip().split())

def map_css_id(css_id, src_id):
    """ Map a CSS ui id to a source token.

    Args:
    Returns:
    Raises:
    """
    if css_id.startswith('src-tok'):
        src_tok_id = re.search('(\d+)', css_id)
        src_tok_id = int(src_tok_id.group(1))
        return 'tok-'+src_doc[src_id][src_tok_id]
    elif css_id_to_str.has_key(css_id):
        return css_id_to_str[css_id]
    else:
        return 'Layout'

def map_keycode(keycode):
    """ Maps a js input keycode to either a character
    or a string representing a control character.

    Args:
    Returns:
    Raises:
    """
    keycode = int(keycode)
    if control_keycode_to_str.has_key(keycode):
        return control_keycode_to_str[keycode]
    return unichr(keycode)

def get_codes_dict(filename):
    """ Convert a js keycode TSV file to a dictionary. The file
    has this format:

       <keycode>\t<str>
    Args:
    Returns:
    Raises:
    """
    keycode_dict = {}
    with open(filename) as in_file:
        for line in in_file:
            line = line.strip()
            if len(line) > 0:
                (keycode,name) = line.split('\t')
                keycode_dict[int(keycode)] = name
    return keycode_dict

def create_payload(payload_list, src_line_id):
    """ Converts a payload list to a dictionary and maps payload elements

    Args:
    Returns:
    Raises:
    """
    payload_dict = {}
    for item in payload_list:
        item_toks = item.split(':')
        if len(item_toks) == 2:
            item_key = item_toks[0]
            item_value = item_toks[1]
            if item_key == 'id':
                payload_dict[item_key] = map_css_id(item_value, src_line_id)
            elif item_key == 'k':
                # ASCII < 7 are control sequences not caught by Javascript
                if int(item_value) > 7:
                    payload_dict[item_key] = map_keycode(item_value)
                else:
                    payload_dict[item_key] = item_value
            elif item_key == 'x' or item_key == 'y':
                payload_dict[item_key] = item_toks[1]
    return payload_dict

def filter_events(event_list, user_id, line_id):
    """ Converts a list of events to a list named tuples 

    Args:
    Returns:
    Raises:
    """
    # Convert to a dictionary and filter duplicate events
    # Event list is already sorted by time
    filtered_events = []
    for i,e in enumerate(event_list):
        if i == 0:
            filtered_events.append(e)
            continue
        e_toks = e.split()
        e_type = e_toks[0]
        e_time = int(e_toks[1])

        e_prev = event_list[i-1].split()
        e_prev_type = e_prev[0]
        e_prev_time = int(e_prev[1])

        if e_prev_type == 'keydown' and e_type == 'keypress' and (e_time-e_prev_time) < 3:
            filtered_events[-1] = e
        else:
            filtered_events.append(e)

    # Events --> Named tuples. Also apply field-specific mapping
    tuple_list = []
    for e in filtered_events:
        e_toks = e.split()
        e_type = e_toks[0]
        e_time = int(e_toks[1])
        if e_type == 'start' or e_type == 'end':
            pass
        else:
            # Process the payload
            payload = create_payload(e_toks[2:], line_id)
            e_keycode = payload.get('k','')
            e_id = payload.get('id','')
            e_x = payload.get('x','')
            e_y = payload.get('y','')
            eclass= event_to_class[e_type]
            event = Event(sourceid=str(line_id),
                          userid=str(user_id),
                          time=str(e_time),
                          event_name=e_type,
                          event_class=eclass,
                          target=e_id,
                          x=e_x,
                          y=e_y,
                          key=e_keycode)
            tuple_list.append(event)

    return tuple_list
    
def parse_actionlogs(logfile):
    """ Convert actionlog to CSV format

    Args:
    Returns:
    Raises:
    """
    user_id = int(re.search('^(\d+)\.', basename(logfile)).group(1))
    sys.stderr.write('User id: %d%s' % (user_id, os.linesep))
    with open(logfile) as in_file:
        with open(basename(logfile)+'.csv','w') as out_file:
            out_csv = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)
            for i,line in enumerate(in_file):
                events = line.strip().split('|')
                filtered_events = filter_events(events, user_id, i)
                wrote_header = False
                for e in filtered_events:
                    if i == 0 and not wrote_header:
                        out_csv.writerow(list(e._fields))
                        wrote_header = True
                    out_csv.writerow([x for x in e._asdict().itervalues()])
    sys.stderr.write('Parsed %d event logs%s' % (i+1, os.linesep))

def main():
    global control_keycode_to_str
    
    desc='Convert and actionlog file to CSV for Tableau import'
    parser=ArgumentParser(description=desc)
    parser.add_argument('logfile',
                        metavar='action_log',
                        type=str,
                        help='Action log file.')
    parser.add_argument('-c', '--js_codes',
                        dest='js_codefile',
                        default=None,
                        type=str,
                        help='TSV file to map control key js keycodes to strings.')
    parser.add_argument('-s', '--src_doc',
                        dest='src_docfile',
                        default=None,
                        type=str,
                        help='Source document.')
    args = parser.parse_args()

    if args.js_codefile:
        control_keycode_to_str = get_codes_dict(args.js_codefile)
    if args.src_docfile:
        src_doc = read_srcdoc(args.src_docfile)
        
    parse_actionlogs(args.logfile)

if __name__ == '__main__':
    main()
