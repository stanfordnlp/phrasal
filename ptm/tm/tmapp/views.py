import logging
from django.http import HttpResponse
from django.shortcuts import render_to_response,redirect
from django.contrib.auth.decorators import login_required
from django.template import RequestContext
from django.utils.translation import ugettext as _

from tmapp.forms import TranslationInputForm

import controller

logger = logging.getLogger(__name__)

## TODO
##  * Make the Http404 page more helpful
##
##

##
## Notes:
##  * RuntimeError raises Http500
##  * Raise Http404 for invalid requests

@login_required
def index(request):
    """
    Return the main index template.
    """
    status = controller.get_user_status(request.user)
    return render_to_response('index.html',
                              {'page_title' : 'Overview',
                               'status' : status},
                              context_instance=RequestContext(request))

@login_required
def training(request):
    page_title = _('CAT Training')
    page_name = _('Training')
    if request.method == 'GET':
        return render_to_response('training.html',
                              {'page_title' : page_title,
                               'page_name' : page_name,
                               'form_action' : '/tm/training/'},
                              context_instance=RequestContext(request))
    elif request.method == 'POST':
        # TODO Save the user's training status
        return redirect('/tm/')
    raise Http404

@login_required
def translate(request):
    """
    Return the translation UI and static content.
    """
    # TODO(spenceg): Need to change the form action per Jason's client
    #                -side manipulation of the URL?
    if request.method == 'GET':
        conf,form = controller.get_translate_configuration_for_user(request.user)
        if conf:
            return render_to_response('translate.html',
                                      {'conf' : conf,
                                       'form_action' : '/tm/translate/',
                                       'form' : form},
                                      context_instance=RequestContext(request))
        else:
            # No more translation sessions
            return redirect('/tm/')
    elif request.method == 'POST':
        # Note: raises runtime error if the form doesn't validate
        # Then what do we do?
        controller.save_translation_session(request.user, request.POST)
        # Go to next document
        return redirect('/tm/translate')

@login_required
def form_demographic(request):
    form = None
    form_instructions = _('Please complete this demographic survey. This information will remain confidential and will not be linked in any way with your real identity.')
    form_title = _('Demographic Survey')
    page_name = _('Demographic Survey')
    
    if request.method == 'GET':
        form = controller.get_demographic_form(request.user)

    elif request.method == 'POST':
        form = controller.get_demographic_form(request.user, request.POST)
        if form.is_valid():
            controller.save_modelform(request.user, form)
            return redirect('/tm/')
    else:
        # TODO log message
        raise Http404

    return render_to_response('survey.html',
                              {'form':form,
                               'form_instructions' : form_instructions,
                               'form_title' : form_title,
                               'page_name' : page_name},
                              context_instance=RequestContext(request))        


@login_required
def form_exit(request):
    form = None
    form_instructions = _('Please fill out this survey about your experience with the different CAT interfaces and modes of assistance.')
    form_title = _('Exit Questionnaire')
    page_name = _('Exit Questionnaire')
    
    if request.method == 'GET':
        form = controller.get_exit_form(request.user)

    elif request.method == 'POST':
        form = controller.get_exit_form(request.user,request.POST)
        if form.is_valid():
            controller.save_modelform(request.user, form)
            return redirect('/tm/')
    else:
        # TODO log message
        raise Http404

    return render_to_response('survey.html',
                              {'form':form,
                               'form_instructions' : form_instructions,
                               'form_title' : form_title,
                               'page_name' : page_name},
                              context_instance=RequestContext(request))   
