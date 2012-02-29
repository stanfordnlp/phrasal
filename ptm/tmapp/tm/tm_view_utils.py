import logging
from datetime import datetime
from django.core.exceptions import ObjectDoesNotExist
from tm.models import SourceTxt,TargetTxt,LanguageSpec,TranslationStats
from django.db import IntegrityError
from tm_user_utils import get_user_conf

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

def get_src(src_id):
    """ Gets a SourceTxt object from a src_id

    Args:
    Returns:
    Raises:
    """
    try:
        src = SourceTxt.objects.select_related().get(pk=src_id)
    except SourceTxt.DoesNotExist:
        logger.error('No SourceTxt object for id: ' + str(src_id))
        return None
    
    return src.id

def save_tgt(user, form):
    """ Save a user translation along with translation stats.
    
    Args:
      user -- A django.contrib.auth.User object
      form -- A validated tm_forms.TranslationInputForm object.
    Raises:
     RuntimeError -- If the src or tgt can't be retrieved.
    Returns:
    """
    # Save the actual target translation
    try:
        src_id = form.cleaned_data['src_id']
        src = SourceTxt.objects.get(id=form.src_id)
    except SourceTxt.DoesNotExist:
        logger.error('Could not lookup src (%d)' % (form.src_id))
        raise RuntimeError

    # Save translation session stats
    try:
        tgt_date = datetime.now()
        tgt_lang = form.cleaned_data['tgt_lang']
        tgt_txt = form.cleaned_data['txt']
        action_log = form.cleaned_data['action_log']
        is_valid = form.cleaned_data['is_valid']
        
        tgt = TargetTxt.objects.create(src=src, user=user,
                                   date=tgt_date, txt=tgt_txt,
                                   lang=tgt_lang)
        tgt_stats = TranslationStats.objects.create(tgt=tgt,
                                                    ui=src.ui,
                                                    user=user,
                                                    action_log=action_log,
                                                    is_valid=is_valid)
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
