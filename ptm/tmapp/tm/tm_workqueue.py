import logging
import random
from tm.models import SourceTxt,UISpec,UserConf,LanguageSpec,ExperimentModule,ExperimentSample,SourceDocumentSpec
from tm_user_utils import get_user_conf
from tm_train_module import done_training

logger = logging.getLogger(__name__)

def get_doc_description(doc_name):
    """ Returns a human readable description of a document.

    Args:
      doc_name -- a SourceTxt.name field
    Returns:
      desc -- (string) a human readable description of a document.
      None -- if the document could not be retrieved
    Raises:
    """
    doc_list = SourceDocumentSpec.objects.filter(name=doc_name)
    if len(doc_list) > 0:
        return doc_list[0].desc
    return None

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

def get_sample(user, sample_id):
    """ Selects an experiment sample for a user. This method enforces the
    constraint that the user can only access the next ExperimentSample list
    ordered by the 'order_by' column.

    Args:
      user -- a django.contrib.auth.models.User object
    Returns:
      sample -- an ExperimentSample object
      None -- if the sample could not be selected
    Raises:
      RuntimeError -- if the user tries to access the sample out of order
    """
    sample_list = ExperimentSample.objects.filter(user=user).order_by('order')
    if len(sample_list) > 0:
        sample = sample_list[0]
        if sample.id == sample_id and sample.user == user:
            return sample
        else:
            logger.error('%s tried to access sample %d out of order!' % (user.username, sample_id))
            raise RuntimeError

    logger.error('Sample %d for user %s does not exist!' % (sample_id, user.username))
    return None

def delete_sample(sample_id):
    """ Deletes a sample.

    Args:
    Returns:
    Raises:
    """
    try:
        sample = ExperimentSample.objects.get(id=sample_id)
    except ExperimentSample.DoesNotExist:
        logger.warn('Sample %d does not exist!' % (sample_id))
        # Silently return
        return True

    sample.delete()
    return True

def has_samples(user):
    """ Returns the name of the module for which the user has samples.

    Args:
      user -- a django.contrib.auth.models.User object
    Returns:
      (name,id) tuple, where:
        name -- (str) ExperimentModule.name if it exists. Otherwise, None.
        id -- (int) ExperimentModule.id if it exists. Otherwise, None.
    Raises:
    """
    sample_list = ExperimentSample.objects.filter(user=user).order_by('order')
    if len(sample_list) > 0:
        sample = sample_list[0]
        return (sample.module.name, sample.id)
    
    return (None,None)

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
        
def get_next_module(user):
    """ Select the next module for this user based on the the data model.

    Args:
      user -- a django.contrib.auth.models.User object
    Returns:
      module_name -- short name for module in tm/index.html
      tr_url -- if this is a tr module, contains the POST action
    Raises:
    """
    user_took_training = done_training(user)
    # Return values. The goal of the gnarly conditionals below is to
    # set these two values.
    module_name = 'train'
    tr_url = ''
    
    if user_took_training:
        (module_name, sample_id) = has_samples(user)
        if module_name:
            logger.info('Module %s still active for %s' % (module_name, user.username))
            tr_url = '/tm/tr/%d/' % (sample_id)
        else:
            # Select a new module
            module_name = select_new_module(user)
            if module_name == None:
                logger.info('%s has finished all modules' % (user.username))
                module_name = 'none'
            else:
                (module_name, sample_id) = has_samples(user)
                logger.info('Selected module %s for user %s' % (module_name,user.username))
                tr_url = '/tm/tr/%d/' % (sample_id)
    else:
        # User has not completed training, so as a sanity check
        # purge any experiment samples
        n_samples = purge_samples(user)
        if n_samples:
            logger.warn('User %s has not completed training, but purged %d samples' % (user.username, n_samples))

    return (module_name,tr_url)
