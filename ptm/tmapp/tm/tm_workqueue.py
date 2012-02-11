import logging
from tm.models import SourceTxt,UISpec,UserConf,LanguageSpec

logger = logging.getLogger(__name__)

def select_tgt_lang(src_lang,user):
    """ Selects a translation target for this source sentence
    given this user's language proficiencies

    Args:
    Returns:
    Raises:
      RuntimeError -- If a language cannot be selected
    """
    user_confs = UserConf.objects.filter(user=user)
    if len(user_confs) > 0:
        u_native_lang = user_confs[0].lang_native
        u_other_lang = user_confs[0].lang_other.split(',')
        if src_lang == u_native_lang:
            # TODO(spenceg) Only supports one translation target language now
            # (i.e., bilingual speakers)
            tgt_lang_code = u_other_lang[0]
            tgt_lang = LanguageSpec.objects.filter(code=tgt_lang_code)
            if len(tgt_lang) > 0:
                return tgt_lang[0]
        else:
            return u_native_lang
        
    logger.error('User does not have requisite proficiencies to translate src_id %d ' % (src_id))
    raise RuntimeError

def get_work_list(user):
    """ Implements the per-user work queue policy.

    Args:
    Returns:
    Raises:
    """

    # TODO(spenceg): Return everything. But the work queue
    # should be ordered such that they haven't translated each
    # sentence, and that the set of targets reflects their language
    # proficiencies
    return SourceTxt.objects.select_related().all()

def select_ui_for_user(user):
    """ Implements the per-user ui selection policy.

    Args:
    Returns:
      A tuple (name,id), where name is a string, and id is an int
    Raises:
      RunetimeError -- If a UI cannot be selected
    """
    
    user_confs = UserConf.objects.filter(user=user)
    if len(user_confs) > 0:
        allowed_uis = user_confs[0].uis_enabled
        if len(allowed_uis) != 0:
            allowed_uis = allowed_uis.split(',')
            # TODO(spenceg): Implement interface selection policy
            # Maybe we should randomize which interface
            # they see, or choose it based on which interface they have
            # seen the fewest times

            # For now, just pick the first UI.
            ui_name = allowed_uis[0]
            ui_spec = UISpec.objects.filter(name=ui_name)
            if len(ui_spec) == 1:
                selected_ui = ui_spec[0]
                return (selected_ui.name, selected_ui.id)

    logger.error('No UIs for user ' + str(user))
    raise RuntimeError

