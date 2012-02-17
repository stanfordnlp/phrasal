import logging
from django.core.urlresolvers import reverse
from django.http import HttpResponse, Http404, HttpResponseRedirect
from django.template import RequestContext
from django.shortcuts import render_to_response, get_object_or_404
from django.contrib.auth.decorators import login_required
from tm.models import SourceTxt,TargetTxt,LanguageSpec,TranslationStats,UISpec
import tm_workqueue
import tm_view_utils

logger = logging.getLogger(__name__)

@login_required
def index(request):
    """ Shows the work queue for each user.
    Args:
    Returns:
    Raises:
    """
    user_took_training = tm_workqueue.done_training(request.user)
    module = 'train'
    last_module = None
    if user_took_training:
        # Module is None if the user has completed all modules
        (module,last_module) = tm_workqueue.select_module(request.user)
        if module == None:
            module = 'none'

    return render_to_response('tm/index.html',
                              {'module_name':module,
                               'first_name' : request.user.first_name,
                               'last_name' : request.user.last_name,
                               'last_module_name' : last_module},
                              context_instance=RequestContext(request))

@login_required
def training(request):
    """ Shows the training page to the user.

    Args:
    Returns:
    Raises:
    """
    if request.method == 'GET':
        return render_to_response('tm/train_exp1.html',
                                  context_instance=RequestContext(request))
    elif request.method == 'POST':
        tm_workqueue.done_training(request.user,set_done=True)
        return HttpResponseRedirect('/tm/')

@login_required
def tr(request):
    """
     On GET: selects a translation interface and source sentence for this
     user.
     On POST: saves a completed translation
     
    Args:
    Returns:
    Raises:
      Http404 on server error.
    """
    if request.method == 'GET':
        # Return a new sentence for translation
        src = tm_workqueue.select_src(request.user)
        if src:
            tgt_lang = tm_workqueue.select_tgt_language(request.user, src.id)
            template = tm_view_utils.get_template_for_ui(src.ui.name)
            if template:
                src_toks = src.txt.split()
                return render_to_response(template,
                                          {'src':src, 'src_toks':src_toks,
                                           'tgt_lang':tgt_lang},
                                          context_instance=RequestContext(request))
            else:
                logger.error('Could not select a template for src: '
                             + repr(src))
                raise Http404
        else:
            # User has completed the module...
            # go back to the index
            return HttpResponseRedirect('/tm/')

    elif request.method == 'POST':
        tgt_lang_id = int(request.POST['form-tgt-lang'].strip())
        tgt_txt = request.POST['form-tgt-txt'].strip()
        action_log = request.POST['form-action-log'].strip()
        src_id = int(request.POST['form-src-id'].strip())

        tm_view_utils.save_tgt(request.user, src_id, tgt_lang_id,
                               tgt_txt, action_log)

        # Send the user to the next translation
        return HttpResponseRedirect('/tm/tr/')
    
@login_required
def history(request, src_id):
    """
    Args:
    Returns:
    Raises:
    """
    try:
        src = SourceTxt.objects.select_related().get(pk=src_id)
    except SourceTxt.DoesNotExist:
        raise Http404
    # Now get the translations
    tgt_list = TargetTxt.objects.select_related().filter(src=src_id).order_by('-date')
    return render_to_response('tm/history.html',
                              {'src':src, 'tgt_list':tgt_list},
                              context_instance=RequestContext(request))
