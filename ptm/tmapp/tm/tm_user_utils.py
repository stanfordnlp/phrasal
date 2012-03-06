import logging
from django.db import IntegrityError
from tm.models import UserConf,LanguageSpec,UISpec,SurveyResponse

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

def save_survey_form(user,form):
    """ Parses a valided tm_forms.UserStudySurveyForm.

    Args:
      user -- a django.contrib.auth.models.User object
      form -- a *validated* tm_forms.UserStudySurveyForm
    Returns:
    Raises:
      RuntimeError -- if the form contents could not be mapped
                      into the data model.
    """
    ui_name = form.cleaned_data['ui_select']
    uispec = UISpec.objects.filter(name=ui_name)
    if len(uispec) > 0:
        uispec = uispec[0]
    else:
        logger.error('UISpec does not exist: ' + ui_name)
        raise RuntimeError

    pos_select = form.cleaned_data['pos_select']
    pos_str = ','.join(pos_select)
    txt_response = form.cleaned_data['txt']
    try:
        response = SurveyResponse.objects.create(user=user,
                                                 pos=pos_str,
                                                 best_ui=uispec,
                                                 txt_response=txt_response)
        response.save()
    except IntegrityError:
        logger.error('Could not save response form: user:%s form:%s' % (user.username,repr(form)))
        raise RuntimeError
