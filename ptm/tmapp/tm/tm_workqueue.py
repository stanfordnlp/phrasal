from tm.models import SourceTxt,UISpec,UserConf
import logging

logger = logging.getLogger(__name__)

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

