import logging
import random
from tm.models import SourceTxt,UISpec,UserConf,LanguageSpec,ExperimentModule
from tm_user_utils import get_user_conf

logger = logging.getLogger(__name__)

def get_experiment_module(module_id):
    """ Retrieves and ExperimentModule based on an id.

    Args:
      module_id -- a pk for ExperimentModule
    Returns:
      module -- an ExperimentModule
      None -- if there is no ExperimentModule for module_id
    Raises:
    """
    try:
        module = ExperimentModule.objects.get(id=module_id)
    except ExperimentModule.DoesNotExist:
        logger.error('No ExperimentModule for id ' + str(module_id))
        return None

    return module

# TODO(spenceg): active_doc needs to be properly initialized by select_new_module, which is a precondition for this method.
def select_src(user):
    """ Selects a source sentence for the user to translate.

    Args:
      user -- a django.contrib.auth.models.User object
    Returns:
      src -- A SourceTxt object
      None -- if no SourceTxt exists for user
    Raises:
    """
    user_conf = get_user_conf(user)
    if not user_conf.active_module:
        logger.info('Selecting source for inactive user profile: ' + repr(user))
        return None

    # TODO(spenceg): Iterate through documents instead of randomly
    # through sentences
    # TODO(spenceg): Random ordering evidently kills MySQL and SQLite, but
    # we are using Postgres currently, so try it until something breaks
    src_list = SourceTxt.objects.filter(ui=user_conf.active_module).exclude(id__in=user_conf.srcs.all).order_by('?')
    
    if src_list:
        return src_list[0]
    else:
        logger.info(user.username + ' has completed module ' + user_conf.active_module.name)
        return None

def select_tgt_language(user, src_id):
    """ Selects a translation target for this user and source text.

    Args:
    Returns:
     A LanguageSpec object on success, None on failure.
    Raises:
     RuntimeError -- if src_id does not exist
    """
    user_conf = get_user_conf(user)
    if not user_conf:
        logger.error('UserConf does not exist for user: ' + request.user.username)
        return None
     
    try:
        src = SourceTxt.objects.get(id=src_id)
    except SourceTxt.DoesNotExist:
        logger.error('Source id %d does not exist!' % (src_id))
        raise RuntimeError

    # TODO(spenceg): Data model only supports bilingual proficiency
    # Add support for multiple translation targets
    if user_conf.lang_native == src.lang:
        return user_conf.lang_other
    else:
        return user_conf.lang_native
    
def select_new_module(user):
    """ Randomizes the order of the ExperimentModule objects that
    the user sees.

    Args:
    Returns:
      next_module -- an ExperimentModule QuerySet
      None -- user has completed all modules
    Raises:
    """
    user_conf = get_user_conf(user)
    if not user_conf:
        logger.error('UserConf does not exist for user: ' + request.user.username)
        return None
    
    user_conf.active_doc = None
    modules = user_conf.active_modules.all()
    n_active_modules = len(modules)
    next_module = None
    if n_active_modules > 0:
        # Ordering of the active modules is randomized.
        n_idx = random.randint(0,n_active_modules-1)
        next_module = modules[n_idx]
        user_conf.active_modules.remove(next_module)

    user_conf.save()
    return next_module
        
