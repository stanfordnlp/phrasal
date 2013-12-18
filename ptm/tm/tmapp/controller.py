import logging
import model_utils
from datetime import datetime
from collections import defaultdict
import json
import urllib
import urllib2
from django.http import HttpResponse, Http404
from django.contrib.auth.decorators import login_required

import choices
from models import UserConfiguration,TrainingRecord,TranslationSession,DemographicData
from forms import DemographicForm,ExitSurveyForm,DivErrorList,TranslationInputForm

logger = logging.getLogger(__name__)

def get_user_app_status(user):
    """
    Returns the status of the app for the current user.
    The status determines which functions are active.
    
    Args:
    Returns:
    Raises:
    """
    user_status = {}
    
    # Filled out demographic info
    user_status['demographic_form'] = True if model_utils.get_demographic_data(user) else False

    # Completed training
    user_status['training'] = True if model_utils.get_training_record(user) else False

    # Translation sessions remaining
    sessions = TranslationSession.objects.filter(user=user).exclude(text__isnull=False).order_by('order')
    user_status['translate'] = True if len(sessions) == 0 else False

    # Filled out exit survey
    user_status['exit_form'] = True if model_utils.get_exit_data(user) else False
        
    return user_status

def user_training_status(user, complete=False):
    """
    Query and optionally update the user's training status.

    Args:
    Returns:
    Raises: RuntimeError
    """
    training_record = model_utils.get_training_record(user)
    if training_record:
        return True
    elif complete:
        training_record = TrainingRecord.objects.create(user=user)
        training_record.save()
    else:
        return False

def get_translate_configuration_for_user(user,training=False):
    """
    Configures the translation session for the user.
    
    """
    try:
        session = TranslationSession.objects.filter(user=user,training=training).exclude(complete=True).order_by('order')[0]
        
    except IndexError:
        # TODO Logging
        return None,None

    # Set the time of this query
    session.start_time = datetime.now()
    session.save()

    # Create the form to display to the user
    form = TranslationInputForm(instance=session)
    
    source_doc = session.src_document
    
    session_object = {}
    session_object['src_document_url'] = source_doc.url
    session_object['src_language'] = source_doc.language.code
    session_object['tgt_language'] = session.tgt_language.code
    session_object['interface'] = session.interface

    return (session_object,form)

def get_user_translation_direction(user):
    """
    Get a tuple identifying the translation direction for the user.

    Returns: (src_lang,tgt_lang)
    Raises: RuntimeError
    """
    user_conf = model_utils.get_configuration(user)
    code_to_language = dict(choices.LANGUAGES)
    try:
        src_lang = code_to_language[user_conf.source_language.code]
        tgt_lang = code_to_language[user_conf.target_language.code]
        return src_lang,tgt_lang
    except KeyError:
        logger.error('Unknown languages for user: ' + str(user))
        raise RuntimeError

def save_translation_session(user, post_data, training=False):
    """
    Save the result of a translation session
    """
    try:
        session = TranslationSession.objects.filter(user=user,training=training).exclude(complete=True).order_by('order')[0]
    except IndexError:
        logger.error('Final translation already submitted: %s || %s' % (user.username, str(post_data)))
        raise RuntimeError

    # TODO(spenceg): Kind of a bootleg integrity check
    # Make this more secure.
    if int(post_data['order']) != session.order:
        logger.error('Submission does not match current session: %s || %s ||%s' % (user.username, str(post_data), str(session)))
        raise RuntimeError

    logger.debug(post_data)

    form = TranslationInputForm(post_data, instance=session)
    
    if form.is_valid():
        form_model = form.save(commit=False)
        form_model.user = user
        form_model.end_time = datetime.now()
        form_model.complete = True
        form_model.save()
    else:
        logger.error('Form validation failed: %s || %s ||%s' % (user.username, str(post_data), str(session)))
        raise RuntimeError
    
def get_demographic_form(user, post_data=None):
    """
    Return a ModelForm backed by DemographicData
    """
    instance = model_utils.get_demographic_data(user)
    if instance and post_data:
        return DemographicForm(post_data,instance=instance,error_class=DivErrorList)
    elif instance:
        return DemographicForm(instance=instance,error_class=DivErrorList)       
    elif post_data:
        return DemographicForm(post_data,error_class=DivErrorList)
    else:
        return DemographicForm(error_class=DivErrorList)

def get_exit_form(user=None, post_data=None):
    """
    Return a ModelForm backed by models.ExitSurveyData.
    """
    instance = model_utils.get_exit_data(user)
    if instance and post_data:
        return ExitSurveyForm(post_data,instance=instance,error_class=DivErrorList)
    elif instance:
        return ExitSurveyForm(instance=instance,error_class=DivErrorList)
    elif post_data:
        return ExitSurveyForm(post_data,error_class=DivErrorList)
    else:
        return ExitSurveyForm(error_class=DivErrorList)

def save_modelform(user, model_form):
    """
    Save a ModelForm that takes a User as a ForeignKey.

    Args:
    Returns:
    Raises:
    """
    model = model_form.save(commit=False)
    model.user = user
    model.save()

#
# Service stuff--This is hard-coded for speed. We don't want
# to do a database query for every request
# to lookup the redirect URL.
# TODO(spenceg) Maybe there's a more MVC-like way to do this.
#
SERVICE_URLS = defaultdict(dict)
SERVICE_URLS['en']['fr'] = 'http://127.0.0.1:8017/x'

# Request types
TRANSLATION_REQUEST = 'tReq'
RULE_QUERY_REQUEST = 'rqReq'

@login_required
def service_redirect(request):
    """
    Redirect Http request to the MT service to avoid
    CORS complexity.

    This can be called directly by a Django urls.py
    file. It's effectively a view. But it can also be
    called by other views as a redirect function, so
    it's located here in the controller.

    Args: an HTTPRequest object
    Returns: an HTTPResponse object
    Raises: Http404
    """
    # Parse the request
    req = None
    query_type = None
    if TRANSLATION_REQUEST in request.GET:
        req = request.GET[TRANSLATION_REQUEST]
        query_type = TRANSLATION_REQUEST
    elif RULE_QUERY_REQUEST in request.GET:
        req = request.GET[RULE_QUERY_REQUEST]
        query_type = RULE_QUERY_REQUEST
    else:
        logger.error('Unknown request: ' + str(request.GET))
        raise Http404

    try:
        req_dict = json.loads(req)
        src_lang = req_dict['src']
        tgt_lang = req_dict['tgt']
    except Exception as e:
        logger.error(str(e) + req)
        raise Http404

    try:
        service_url = SERVICE_URLS[src_lang.lower()][tgt_lang.lower()]
    except KeyError:
        logger.error('No service URL: ' + req)
        raise Http404
    
    # Construct the query
    url = '%s?%s=%s' % (service_url, query_type, urllib.quote(req))
    logger.debug(url)
        
    #Execute the query
    request = urllib2.urlopen( url )
    content = request.read()

    # Create the response
    response_data = json.loads( content, encoding = 'ISO-8859-1' )
    return HttpResponse(json.dumps(response_data, encoding='utf-8'), content_type="application/json")
