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
      RuntimeError -- if the query fails
    """
    try:
        uispec = UISpec.objects.get(id=ui_id)
    except UISpec.DoesNotExist:
        logger.error('Could not retrieve UISpec for id: ' + str(ui_id))
        raise RuntimeError
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
      src -- a SourceTxt object
    Raises:
      RuntimeError -- if the query fails
    """
    try:
        src = SourceTxt.objects.select_related().get(pk=src_id)
    except SourceTxt.DoesNotExist:
        logger.error('No SourceTxt object for id: ' + str(src_id))
        raise RuntimeError
    
    return src

def get_machine_hypothesis(src, tgt_lang):
    """ Gets a machine-generated hypothesis for this input.

    Args:
      src -- a SourceTxt object
      tgt_lang -- a LanguageSpec object
    Returns:
      tgt -- a TargetTxt object
      None -- if no machine hypothesis can be found
    Raises:
      RuntimeError -- if the query fails
    """
    try:
        tgt_list = TargetTxt.objects.filter(src=src,
                                            lang=tgt_lang,
                                            is_machine=True)
        n_results = len(tgt_list)
        if n_results > 0:
            if n_results > 1:
                logger.warn('More than 1 machine hypothesis for src: %d' % (src.id))
            return tgt_list[0]

    except TargetTxt.DoesNotExist:
        logger.error('Could not lookup hypothesis for src: %d' % (src.id))
        raise RuntimeError

    return None
    
def get_suggestion(src, tgt_lang, ui_id):
    """ Gets a machine-generated suggestion for this src_id. Note that
    the type of suggestion is dependent on the ui

    Args:
    Returns:
      suggest -- (string) representation of the suggestion. Possibly empty
                 if there is no machine suggestion.
    Raises:
    """
    uispec = get_uispec(ui_id)

    # Show a machine-generated complete suggestion 
    if uispec.best_suggestion:
        tgt = get_machine_hypothesis(src, tgt_lang)
        if tgt:
            return tgt.txt
        else:
            logger.error('No machine hypothesis for src: ' + str(src_id))

    # This interface does not show a 1-best hypothesis
    return ''

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
        
        tgt = TargetTxt.objects.create(src=src,
                                       date=tgt_date,
                                       txt=tgt_txt,
                                       lang=tgt_lang,
                                       is_machine=False)
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

