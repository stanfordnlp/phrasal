import logging
from datetime import datetime
from tm.models import SourceTxt,TargetTxt,LanguageSpec,TranslationStats,UISpec
from django.db import IntegrityError

logger = logging.getLogger(__name__)

def get_uispec(ui_id):
    """ Gets a UISpec object for a ui_id.

    Args:
    Returns:
    Raises:
    """
    try:
        uispec = UISpec.objects.get(id=ui_id)
    except UISpec.DoesNotExist:
        logger.error('Could not retrieve UISpec for id: ' + str(ui_id))
        return None
    return uispec

def get_template_for_ui(ui_id):
    """ Returns a Django template for a given UISpec

    Args:
      ui_id -- pk for a UISpec object
      None -- If a template could not be found
    Returns:
      template_str -- A string containing the template name for this UI
      None -- if a template could not be found.
    Raises:
    """
    uispec = get_uispec(ui_id)
    
    ui_name = uispec.name
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
    
    return src

def save_tgt(user, form):
    """ Save a user translation along with translation stats.
    
    Args:
      user -- A django.contrib.auth.User object
      form -- A validated tm_forms.TranslationInputForm object.
    Raises:
     RuntimeError -- If the src or tgt can't be retrieved.
    Returns:
    """
    src_id = form.cleaned_data['src_id']
    src = get_src(src_id)
    ui_id = form.cleaned_data['ui_id']
    uispec = get_uispec(ui_id)
 
    # Save the actual translation and the translation session stats
    try:
        tgt_date = datetime.now()
        tgt_lang = form.cleaned_data['tgt_lang']
        tgt_txt = form.cleaned_data['txt'].strip()
        action_log = form.cleaned_data['action_log'].strip()
        is_valid = form.cleaned_data['is_valid']
        
        tgt = TargetTxt.objects.create(src=src, user=user,
                                       date=tgt_date, txt=tgt_txt,
                                       lang=tgt_lang)
        tgt_stats = TranslationStats.objects.create(tgt=tgt,
                                                    ui=uispec,
                                                    user=user,
                                                    action_log=action_log,
                                                    is_valid=is_valid)
        tgt.save()
        tgt_stats.save()
        
    except IntegrityError:
        logger.error('Could not save translation for src (%d) trans: %s' % (src_id,tgt_txt))
        raise RuntimeError

