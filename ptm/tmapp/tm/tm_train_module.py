import logging
from tm.models import SourceTxt
from tm_workqueue import get_user_conf

logger = logging.getLogger(__name__)


def get_training_src(user, last_ui_id):
    """ Shows the user a sequence of training UIs based
    on user_conf.uis_allowed and last_ui_id. After the last
    unseen ui is shown, returns None.

    Args:
    Raises:
    Returns:
      SourceTxt object if any training is left, otherwise None.
    """
    conf = get_user_conf(user)
    uis = None
    if last_ui_id: 
        uis = [x for x in conf.uis_allowed if x.id > last_ui_id]
    else:
        uis = [x for x in conf.uis_allowed]
    
    if uis == None or len(uis) == 0:
        return None
    else:
        srcs = SourceTxt.objects.filter(ui__in=uis,doc__startswith='train')
        if len(srcs) > 0:
            return srcs[0]

    return None
    
