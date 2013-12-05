import logging
from django.http import HttpResponse
from django.shortcuts import render_to_response,redirect
from django.contrib.auth.decorators import login_required
from django.template import RequestContext

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
    # TODO(spenceg): Should include a screencast demo, and a version of the UI with training documents
    # loaded.
    return HttpResponse("Training interface")

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
                              {'form':form },
                              context_instance=RequestContext(request))        


@login_required
def form_exit(request):
    form = None
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
                              {'form':form },
                              context_instance=RequestContext(request))
