import logging
from tm.models import SourceTxt,UISpec,UserConf,LanguageSpec

logger = logging.getLogger(__name__)


def get_user_conf(user):
    """ Returns the configuration for a user
    
    Args:
      user -- django.contrib.auth.models.User object
    Returns:
      UserConf object if the user exists in the database.
      None otherwise.
    Raises:
    """
    try:
        user_conf = UserConf.objects.get(user=user)
    except UserConf.MultipleObjectsReturned, UserConf.DoesNotExist:
        logger.error('Could not retrieve credentials for ' + repr(user))
        return None

    return user_conf

def done_training(user,set_done=False):
    """ Determines whether or not a user has completed training.

    Args:
    Returns:
      True -- if the user has completed training.
      False -- if the user has not completed training.
      None -- if the user does not exist 
    Raises:
    """
    user_conf = get_user_conf(user)
    if not user_conf:
        return None
    elif set_done:
        user_conf.has_trained = True
        user_conf.save()
        return True
    else:
        return user_conf.has_trained

def select_src(user):
    """ Selects a source sentence for the user to translate.

    Args:
    Returns:
     A SourceTxt object on success, None on failure
    Raises:
    """
    user_conf = get_user_conf(user)
    if not user_conf.active_module:
        logger.info('Selecting source for inactive user profile: ' + repr(user))
        return None

    # TODO(spenceg): Random ordering evidently kills MySQL and SQLite, but
    # we are using Postgres currently, so try it until something breaks
    src_list = SourceTxt.objects.filter(ui=user_conf.active_module).exclude(id__in=user_conf.srcs.all).order_by('?')
    
    if src_list:
        return src_list[0]
    else:
        logger.info(user.username + ' has completed module ' + user_conf.active_module.name)
        return None

def select_tgt_language(user,src_id):
    """ Selects a translation target for this user and source text.

    Args:
    Returns:
     A LanguageSpec object on success, None on failure.
    Raises:
     RuntimeError -- if src_id does not exist
    """
    user_conf = get_user_conf(user)
    try:
        src = SourceTxt.objects.get(id=src_id)
    except DoesNotExist:
        logger.error('Source id %d does not exist!' % (src_id))
        raise RuntimeError

    # TODO(spenceg): Data model only supports bilingual proficiency
    # Add support for multiple translation targets
    if user_conf.lang_native == src.lang:
        return user_conf.lang_other
    else:
        return user_conf.lang_native
    
def select_module(user):
    """ Implements the per-user work queue policy.

    Args:
    Returns:
       Tuple containing (new_module,last_module)
       new_module (string) -- Current active module
       last_module (string) -- Last active module
    Raises:
    """
    user_conf = get_user_conf(user)
    if user_conf.done_with_tasks:
        return (None, None)

    # Now select the new module (if any)
    enabled = None
    last_module_name = None
    if user_conf.active_module:
        last_module_name = user_conf.active_module.name
        src = select_src(user)
        if src:
            # The user still has work to do on this module
            return (user_conf.active_module.name, last_module_name)
        else:
            enabled = user_conf.uis_enabled.filter(id__gt=user_conf.active_module.id).order_by('id')
    else:
        enabled = user_conf.uis_enabled.all().order_by('id')

    current_module_name = None
    if enabled:
        current_module_name = enabled[0].name
        user_conf.active_module = enabled[0]
        user_conf.save()
    else:
        user_conf.active_module = None
        user_conf.done_with_tasks = True
        user_conf.save()

    return (current_module_name, last_module_name)
        
