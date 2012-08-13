from collections import namedtuple
import csv


#
# Meta file format
#
MetaRow = namedtuple('MetaRow','is_machine date ui_id is_valid src_len tgt_len')

def load_meta_file(filename):
    """
    Returns a list of MetaRow objects

    Args:
    Returns:
    Raises:
    """
    rows = []
    r = csv.reader(open(filename),delimiter='\t')
    seen_header = False
    for row in map(MetaRow._make, r):
        if seen_header:
            rows.append(row)
        seen_header = True
    return rows

#
# Action log event format
#
Event = namedtuple('Event', 'sourceid userid time event_name event_class device target src_tok x y key keytype button src_len time_norm ui_id')

def load_actionlog_events(filename):
    """
    Returns a list of action log events

    Args:
    Returns:
    Raises:
    """
    rows = []
    with open(filename) as infile:
        seen_header = False
        for row in map(Event._make, csv.reader(infile)):
            if seen_header:
                rows.append(row)
            seen_header = True
    return rows

