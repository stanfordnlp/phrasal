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

Event = namedtuple('Event', 'sourceid userid time event_name event_class device target src_tok x y key keytype button src_len time_norm ui_id')

# Maps control keycodes to human readable names
control_keycode_to_str = None

# Maps js events to a type classification
event_to_class = {'start':'start',
                  'end':'end',
                  # Focus events can be controlled by mouse or tabbing
                  'blur': 'focus',
                  'focus':'focus',
                  'focusin':'focus',
                  'focusout':'focus',
                  # Browser events based on modification of the viewport
                  'load':'browser',
                  'resize':'browser',
                  'scroll':'browser',
                  'unload':'browser',
                  'click':'mouse',
                  'dblclick':'mouse',
                  'keydown':'keyboard',
                  'mousedown':'mouse',
                  'mouseup':'mouse',
                  'mouseover':'mouse',
                  'mouseout':'mouse',
                  'mouseenter':'mouse',
                  'mouseleave':'mouse',
                  'change':'mouse',
                  'select':'mouse',
                  'submit':'mouse',
                  'keypress':'keyboard',
                  'error':'browser'}

# Convert CSS ids from the UI to human readable names
css_id_to_str = {'id_txt':'target_textbox',
                 'src-display':'source_textbox',
                 'form-tgt-submit':'form_submit'}

# The whitespace tokenized source document
# src_doc[0][1] -> second token for the first sentence in the document
src_doc = None

def read_srcdoc(filename):
    """ Read and tokenize the source document.

    Args:
    Returns:
    Raises:
    """
    src_doc = []
    with codecs.open(filename,encoding='utf-8') as in_file:
        src_doc = [x.strip().split() for x in in_file.readlines()]
    return src_doc
        
def map_css_id(css_id, src_id):
    """ Map a CSS ui id to a source token.

    Args:
    Returns:
    Raises:
    """
    if css_id.startswith('src-tok'):
        src_tok_id = re.search('(\d+)', css_id)
        src_tok_id = int(src_tok_id.group(1))
        return ('token',src_doc[src_id][src_tok_id])
    elif css_id_to_str.has_key(css_id):
        return (css_id_to_str[css_id],'')
    else:
        return (css_id,'')

def get_device_for_event_class(event_class):
    """
    Args:
    Returns:
    Raises:
    """
    if event_class.startswith('mouse'):
        return 'mouse'
    elif event_class.startswith('keyboard'):
        return 'keyboard'
    else:
        return ''
    
def map_keycode(keycode):
    """ Maps a js input keycode to either a character
    or a string representing a control character.

    Args:
    Returns:
    Raises:
    """
    keycode = int(keycode)
    if control_keycode_to_str.has_key(keycode):
        keystr = control_keycode_to_str[keycode]
        return (keystr, 'control')
    return (unichr(keycode), 'input')

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
    """ Converts a raw event payload to a dictionary.
    Maps some of the payload elements to interpretable values.
    Payload keys are invariant.

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
                    # k is indicates a mouse button
                    payload_dict[item_key] = (item_value, 'mouse')
            elif item_key == 'x' or item_key == 'y':
                payload_dict[item_key] = item_toks[1]
    return payload_dict

def filter_events(event_list, user_id, line_id, ui_id):
    """ Converts a list of events to a list named tuples. Filters out
    duplicate events (e.g., keypress/keydown).

    Args:
    Returns:
    Raises:
    """
    global src_doc
    
    # Convert to a dictionary and filter duplicate events
    # Event list is already sorted by time
    filtered_events = []
    end_time = None
    num_discarded = 0
    for i,e in enumerate(event_list):
        if e == 'ERROR':
            continue
        if i == 0:
            filtered_events.append(e)
            continue
        e_toks = e.split()
        assert len(e_toks) > 1
        e_type = e_toks[0]
        e_time = int(e_toks[1])

        if e_type == 'end':
            end_time = e_time
        
        e_prev = event_list[i-1].split()
        e_prev_type = e_prev[0]
        e_prev_time = int(e_prev[1])

        # Sub 3ms response time associated with duplicate keyboard events
        if (e_time - e_prev_time) <= 1:
            num_discarded += 1
            filtered_events[-1] = e
        else:
            filtered_events.append(e)

    sys.stderr.write('Discard %d duplicate events.%s' % (num_discarded,os.linesep))
                     
    # Events --> Named tuples. Also apply field-specific mapping
    tuple_list = []
    for e in filtered_events:
        # We must have seen the end event
        assert end_time
        e_toks = e.split()
        e_type = e_toks[0]
        e_time = int(e_toks[1])
        # Process the payload
        payload = create_payload(e_toks[2:], line_id)
        (e_keycode,e_keytype) = payload.get('k', ('',''))
        (e_id,e_token) = payload.get('id', ('',''))
        e_x = payload.get('x','')
        e_y = payload.get('y','')
        eclass = event_to_class[e_type]
        device = get_device_for_event_class(eclass)
        src_len = len(src_doc[line_id])
        event = Event(sourceid=str(line_id),
                      ui_id=ui_id,
                      userid=str(user_id),
                      time=str(e_time),
                      src_len=str(src_len),
                      time_norm=str(float(e_time) / float(end_time)),
                      event_name=e_type,
                      event_class=eclass,
                      device=device,
                      target=e_id,
                      x=e_x,
                      y=e_y,
                      src_tok=e_token,
                      keytype=e_keytype,
                      key=e_keycode if device == 'keyboard' else '',
                      button=e_keycode if device == 'mouse' else '')
        tuple_list.append(event)

    return tuple_list

def get_ui_dict_from_meta(metafile):
    """ Associates sourceids with uis from metafiles.

    Args:
    Returns:
    Raises:
    """
    with open(metafile) as in_file:
        ui_dict = {}
        for i,line in enumerate(in_file):
            if i == 0:
                # Skip header
                continue
            line_toks = line.strip().split('\t')
            ui_dict[i-1] = line_toks[2]
    return ui_dict

def parse_actionlogs(logfile, metafile, output_dir):
    """ Convert actionlog to CSV format

    Args:
    Returns:
    Raises:
    """
    logfile_name = basename(logfile)
    user_id = int(re.search('^(\d+)\.', logfile_name).group(1))
    sys.stderr.write('User id: %d%s' % (user_id, os.linesep))
    ui_dict = get_ui_dict_from_meta(metafile)
    with open(logfile) as in_file:
        with open('%s/%s.csv' % (output_dir,logfile_name),'w') as out_file:
            out_csv = UnicodeWriter(out_file, quoting=csv.QUOTE_ALL)
            for i,line in enumerate(in_file):
                events = line.strip().split('|')
                ui_id = ui_dict[i]
                filtered_events = filter_events(events, user_id, i, ui_id)
                wrote_header = False
                for e in filtered_events:
                    if i == 0 and not wrote_header:
                        out_csv.writerow(list(e._fields))
                        wrote_header = True
                    out_csv.writerow([x for x in e._asdict().itervalues()])
    sys.stderr.write('Parsed %d event logs%s' % (i+1, os.linesep))

def main():
    desc='Convert and actionlog file to CSV for Tableau import'
    parser=ArgumentParser(description=desc)
    parser.add_argument('-l','--log_files',
                        dest='logfiles',
                        nargs='+',
                        required=True,
                        help='Action log file.')
    parser.add_argument('-m','--meta',
                        dest='metafiles',
                        nargs='+',
                        required=True,
                        help='Meta file for each target translation.')
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
    parser.add_argument('-o', '--output_dir',
                        dest='out_dir',
                        default=None,
                        type=str,
                        help='Output directory for files.')
    args = parser.parse_args()

    # Load global variables
    global control_keycode_to_str,src_doc
    if args.js_codefile:
        control_keycode_to_str = get_codes_dict(args.js_codefile)
    if args.src_docfile:
        src_doc = read_srcdoc(args.src_docfile)

    # Setup output directory
    output_dir = args.out_dir if args.out_dir else './'
        
    for logfile,metafile in zip(args.logfiles,args.metafiles):
        parse_actionlogs(logfile, metafile, output_dir)

if __name__ == '__main__':
    main()
