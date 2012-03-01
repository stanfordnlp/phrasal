import logging
import random
from tm.models import SourceTxt,UISpec,UserConf,LanguageSpec,ExperimentModule,ExperimentSample
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

def purge_samples(user):
    """ Purges all stored ExperimentSamples for a user. This can be done
    as a sanity check before a new module is loaded.

    Args:
    Returns:
      cnt -- The number of samples that were purged.
    Raises:
    """

    sample_list = ExperimentSample.objects.filter(user=user)
    n_samples = sample_list.count()
    sample_list.delete()
    return n_samples

def select_src(user):
    """ Selects a source sentence for the user to translate.

    Args:
      user -- a django.contrib.auth.models.User object
    Returns:
      (src, module) -- A tuple
        src -- A SourceTxt object
        module -- An ExperimentModule object
    Raises:
    """
    user_conf = get_user_conf(user)
    if not user_conf:
        logger.error('Could not retrieve UserConf for user: ' + user.username)
        return (None, None)
    
    sample_list = ExperimentSample.objects.filter(user=user).order_by('order')
    if len(sample_list) > 0:
        sample = sample_list[0]
        src = sample.src
        module = sample.module
        # User can only see this sample once
        sample.delete()
        return (src, module)
    return (None, None)

def select_new_module(user):
    """ Selects a new ExperimentModule for a user, and specifies the order
    of the samples that the user will see.

    Args:
    Returns:
      next_module -- ExperimentModule.name (string)
      None -- user has completed all modules
    Raises:
    """
    user_conf = get_user_conf(user)
    if not user_conf:
        logger.error('UserConf does not exist for user: ' + request.user.username)
        return None

    modules = user_conf.active_modules.all()
    n_active_modules = len(modules)
    next_module = None
    if n_active_modules > 0:
        # Ordering of the active modules is randomized.
        n_idx = random.randint(0,n_active_modules-1)
        next_module = modules[n_idx]
        user_conf.active_modules.remove(next_module)

    user_conf.save()
    
    # Now create the samples for this ExperimentModule
    if next_module:
        purged = purge_samples(user)
        if purged > 0:
            logger.warn('Purged %d samples for user %s before starting module %s' % (purged,user.username,next_module.name))
                        
        docs = next_module.docs
        docs = docs.split(',')
        random.shuffle(docs)
        i_sample = 0
        for doc in docs:
            src_list = SourceTxt.objects.filter(doc=doc).order_by('seg')
            for src in src_list:
                sample = ExperimentSample(user=user,
                                          src=src,
                                          order=i_sample,
                                          module=next_module)
                sample.save()
                i_sample += 1
        return next_module.name

    return None
        
