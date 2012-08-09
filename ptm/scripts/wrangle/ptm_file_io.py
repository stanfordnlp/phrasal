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

