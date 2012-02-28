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
    """
    if not user:
        return None
    try:
        user_conf = UserConf.objects.get(user=user)
    except UserConf.MultipleObjectsReturned,UserConf.DoesNotExist:
        logger.error('Could not retrieve credentials for ' + repr(user))
        return None

    return user_conf

def get_user_langs(user):
    """ Get the user's assigned source language
    
    Args:
    Returns:
      None -- if the user does not exist
      (src,tgt) -- A language spec tuple
    Raises:
    """
    conf = get_user_conf(user)
    if conf == None:
        return None

    return (conf.lang_native, conf.lang_other)
    

