import csv
from collections import namedtuple,defaultdict

#
# The raw user data from sql/dump_db.sql.
#
# User data collected from the survey
UserDataRow = namedtuple('UserDataRow', 'user_id,lang_other_id,has_trained,birth_country_id,home_country_id,hours_per_week,is_pro_translator,username,first_name,last_name')
def load_raw_user_data(filename):
    """ Returns a username -> UserDataRow dictionary.
    """
    with open(filename) as infile:
        user_dict = {}
        r = csv.reader(infile)
        # Skip header
        r.next()
        for row in map(UserDataRow._make, r):
            user_dict[row.username] = row
        return user_dict
    
#
# Sentence data file produced by edu.stanford.nlp.ptm.SourceTextAnalysis.
#
SentenceData = namedtuple('SentenceData', 'src_id syn_complexity n_entity_tokens')
def load_sentence_data(filename):
    with open(filename) as infile:
        r = csv.reader(infile, delimiter='\t')
        return map(SentenceData._make, r)

#
# Token data file produced by edu.stanford.nlp.ptm.SourceTextAnalysis.
#
TokenData = namedtuple('TokenData', 'src_id token_id token pos')
def load_token_data(filename):
    """
    Returns a defaultdict(list) where the first key is src_id
    TODO(spenceg): Unicode support.
    """
    with open(filename) as infile:
        r = csv.reader(infile, delimiter='\t')
        rows = defaultdict(list)
        for row in map(TokenData._make, r):
            src_id = int(row.src_id)
            rows[src_id].append(row)
        return rows

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
    with open(filename) as infile:
        r = csv.reader(filename,delimiter='\t')
        # Skip header
        r.next()
        return map(MetaRow._make, r)

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
    with open(filename) as infile:
        r = csv.reader(infile)
        # Skip header
        r.next()
        return map(Event._make, r)

