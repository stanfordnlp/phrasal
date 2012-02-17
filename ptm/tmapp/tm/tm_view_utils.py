import logging
from datetime import datetime
from django.core.exceptions import ObjectDoesNotExist
from tm.models import SourceTxt,TargetTxt,LanguageSpec,TranslationStats
from django.db import IntegrityError
from tm_workqueue import get_user_conf

logger = logging.getLogger(__name__)

def get_template_for_ui(ui_name):
    """ Returns a Django template for a given UISpec

    Args:
     ui_name -- String corresponding to UISpec.name field
     
    Raises:
    Returns:
     A string containing the template 
    """
    if ui_name == 'tr':
        return 'tm/tr.html'
    elif ui_name == 'meedan':
        return 'tm/tr_meedan.html'
    elif ui_name == 'trados':
        return 'tm/tr_trados.html'
    elif ui_name == 'sjc':
        return 'tm/tr_sjc.html'
    else:
        return None

def save_tgt(user,src_id,tgt_lang_id,tgt_txt,action_log,is_complete):
    """ Save a user translation along with translation stats.
    
    Args:
    Raises:
     RuntimeError -- If the src or tgt can't be retrieved.
    Returns:
    """
    # Save the actual target translation
    try:
        src = SourceTxt.objects.get(id=src_id)
        tgt_lang = LanguageSpec.objects.get(id=tgt_lang_id)
    except ObjectDoesNotExist:
        logger.error('Could not lookup src (%d) or tgt_lang (%d)'
                     % (src_id,tgt_lang_id))
        raise RuntimeError

    # Save translation session stats
    try:
        tgt_date = datetime.now()
        tgt = TargetTxt.objects.create(src=src, user=user,
                                   date=tgt_date, txt=tgt_txt,
                                   lang=tgt_lang)
        tgt_stats = TranslationStats.objects.create(tgt=tgt,
                                                    ui=src.ui,
                                                    user=user,
                                                    action_log=action_log,
                                                    complete=is_complete)
        tgt.save()
        tgt_stats.save()
    except IntegrityError:
        logger.error('Could not save new translation to database src:' +
                     str(src_id) + 'tgt_txt:' + tgt_txt) 
        raise RuntimeError

    # Now indicate that the user has translated this sentence
    conf = get_user_conf(user)
    conf.srcs.add(src)
    conf.save()
