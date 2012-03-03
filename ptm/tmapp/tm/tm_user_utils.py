import logging
from tm.models import UserConf,LanguageSpec

logger = logging.getLogger(__name__)

def get_user_conf(user):
    """ Returns the configuration for a user
    
    Args:
      user -- django.contrib.auth.models.User object
    Returns:
      UserConf object if the user exists in the database.
      None otherwise.
    Raises:
      RuntimeError -- if the UserConf could not be found
    """
    if not user:
        return None
    try:
        user_conf = UserConf.objects.get(user=user)
    except UserConf.MultipleObjectsReturned,UserConf.DoesNotExist:
        logger.error('Could not retrieve credentials for ' + repr(user))
        raise RuntimeError

    return user_conf

def get_user_langs(user):
    """ Get the user's assigned source and target languages.
    TODO(spenceg): This code assumes that the user is bilingual (e.g.,
    cannot have trilingual proficiencies)
    
    Args:
    Returns:
      None -- if the user does not exist
      (src,tgt) -- A language spec tuple
    Raises:
    """
    conf = get_user_conf(user)
    return (conf.lang_native, conf.lang_other)
    
def get_active_modules(user):
    """ Gets the users active experiment modules. The active modules
    are sorted by primary key.

    Args:
      modules -- A QuerySet of ExperimentModules
      None -- The user no longer has any active modules.
    Returns:
    Raises:
    """
    conf = get_user_conf(user)
    return conf.active_modules.all().order_by('id')

