import logging
from tm.models import SourceTxt,Country
from tm_user_utils import get_user_conf

logger = logging.getLogger(__name__)

# TODO(spenceg): Hard-coded for the first experiment
def next_training_ui_id(last_id):
    """ Returns the next training ui to present to the
        user based on the last ui.
    Args:
      last_id -- pk of the ui. Pass None to get the first training ui id
    Returns:
      id -- pk of the ui to display.
      None -- if the user has completed the training.
    Raises:
    """
    if not last_id:
        return 1
    if last_id == 1:
        return 2
    return None

def done_training(user,set_done=False,form_data=None):
    """ Determines whether or not a user has completed training.

    Args:
      user -- a django.contrib.auth.User object
      set_done -- true if this user has finished training
      form_data -- data that the user submitted during training
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
        try:
            native_name = form_data['native_country']
            native_country = Country.objects.get(code=native_name)
            home_name = form_data['home_country']
            home_country = Country.objects.get(code=home_name)
        except Country.DoesNotExist:
            logger.error('Could not lookup user country selection: %s' % (str(form_data)))
            return None
        
        user_conf.birth_country = native_country
        user_conf.home_country = home_country
        user_conf.hours_per_week = form_data['hours']
        user_conf.has_trained = True
        user_conf.save()

        return True
    else:
        return user_conf.has_trained

def get_src(user, ui_id):
    """ Shows the user a sequence of training UIs based
    on user_conf.uis_allowed and last_ui_id. After the last
    unseen ui is shown, returns None.

    Args:
    Raises:
    Returns:
      src -- SourceTxt object for this ui_id.
      None -- otherwise.
    """
    srcs = SourceTxt.objects.filter(ui=ui_id,doc__startswith='train')
    if len(srcs) > 0:
        return srcs[0]

    return None
    
