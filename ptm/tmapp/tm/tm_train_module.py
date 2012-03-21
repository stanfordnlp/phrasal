import logging
import random
from tm.models import SourceTxt,Country
from tm_user_utils import get_user_conf, get_active_modules

logger = logging.getLogger(__name__)

def next_training_module(user,last_module):
    """ Returns the next training ui to present to the
        user based on the last ui.
    Args:
      last_id -- pk of the ui. Pass None to get the first training ui id
    Returns:
      id -- pk of the ui to display.
      None -- if the user has completed the training.
    Raises:
    """
    modules = get_active_modules(user)
    if not modules or len(modules) == 0:
        return None
    logger.debug(str(last_module))
    if last_module:
        modules = modules.filter(id__gt=last_module.id).order_by('id')

    if len(modules) > 0:
        logger.debug(str(modules[0]))
        return modules[0]

    return None

def done_training(user,set_done=False,form=None):
    """ Determines whether or not a user has completed training.

    Args:
      user -- a django.contrib.auth.User object
      set_done -- true if this user has finished training
      form -- an instance of tm_forms.UserTrainingForm
    Returns:
      True -- if the user has completed training.
      False -- if the user has not completed training.
      None -- if the user does not exist 
    Raises:
    """
    user_conf = get_user_conf(user)
    if set_done and form:
        user_conf.birth_country = form.cleaned_data['birth_country']
        user_conf.home_country = form.cleaned_data['home_country']
        user_conf.hours_per_week = form.cleaned_data['hours_per_week']
        user_conf.is_pro_translator = form.cleaned_data['is_pro_translator']
        user_conf.has_trained = True
        user_conf.save()
        return True
    else:
        return user_conf.has_trained

def get_src(src_lang):
    """ Retrieves a training source sentence for this UiSpec id.

    Args:
      src_lang -- a LanguageSpec object
    Returns:
      src -- SourceTxt object for this language.
      None -- otherwise.
    Raises:
    """
    srcs = SourceTxt.objects.filter(lang=src_lang,doc__startswith='train')
    n_srcs = len(srcs)
    if n_srcs > 0:
        r_idx = random.randint(0,n_srcs-1)
        return srcs[r_idx]
    
    logger.error('No training documents loaded for src language ' + src_lang.name)
    return None
    
