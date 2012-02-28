import logging
from django.core.urlresolvers import reverse
from django.http import HttpResponse, Http404, HttpResponseRedirect
from django.template import RequestContext
from django.shortcuts import render_to_response, get_object_or_404
from django.contrib.auth.decorators import login_required
from tm.models import SourceTxt,TargetTxt,LanguageSpec,TranslationStats,UISpec,Country
import tm_workqueue
import tm_view_utils
import tm_user_utils
import tm_train_module

logger = logging.getLogger(__name__)

@login_required
def index(request):
    """ Shows the work queue for each user.
    Args:
    Returns:
    Raises:
    """
    user_took_training = tm_train_module.done_training(request.user)
    if user_took_training == None:
        # TODO(spenceg): Do something fancier here.
        # User lookup failed
        raise Http404
    module = 'train'
    last_module = None
    if user_took_training:
        # Module is None if the user has completed all modules
        (module,last_module) = tm_workqueue.select_module(request.user)
        if module == None:
            module = 'none'
    name = request.user.first_name
    if not name:
        name = request.user.username
    return render_to_response('tm/index.html',
                              {'module_name':module,
                               'name' : name,
                               'last_module_name' : last_module},
                              context_instance=RequestContext(request))

@login_required
def module_train(request,ui_id):
    """ Shows this users enabled interfaces in order

    Args:
    Returns:
    Raises:
    """
    src = tm_train_module.get_src(request.user, int(ui_id))
    if src:
        template = tm_view_utils.get_template_for_ui(src.ui.name)
        if template:
            tgt_lang = tm_workqueue.select_tgt_language(request.user, src.id)
            src_toks = src.txt.split()
            return render_to_response(template,
                                      {'src':src, 'src_toks':src_toks,
                                       'tgt_lang':tgt_lang},
                                      context_instance=RequestContext(request))
    logger.error('Could not find a training instance for: (user: %s) (ui: %d)' % (str(request.user),ui_id))
    raise Http404
    
@login_required
def training(request):
    """ Shows the training page to the user.

    Args:
    Returns:
    Raises:
    """
    (src_lang,tgt_lang) = tm_user_utils.get_user_langs(request.user)
    src_name = None
    tgt_name = None
    if src_lang and tgt_lang:
        src_name = src_lang.name
        tgt_name = tgt_lang.name
    else:
        raise Http404

    if request.method == 'GET':
        country_list = Country.objects.all()
        return render_to_response('tm/train_exp1.html',
                                  {'src_lang':src_name,
                                   'country_list':country_list,
                                   'tgt_lang':tgt_name},
                                  context_instance=RequestContext(request))
    
    elif request.method == 'POST':
        native_country = request.POST['form-birth-country'].strip()
        home_country = request.POST['form-resident-country'].strip()
        num_hours = int(request.POST['form-num-hours'].strip())
        form_data = {'native_country':native_country,
                     'home_country':home_country,
                     'hours':num_hours}
        
        tm_train_module.done_training(request.user,
                                      set_done=True,
                                      form_data=form_data)
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
            template = tm_view_utils.get_template_for_ui(src.ui.name)
            if template:
                tgt_lang = tm_workqueue.select_tgt_language(request.user, src.id)
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
        # Get the metadata out of the form POST
        tgt_lang_id = int(request.POST['form-tgt-lang'].strip())
        tgt_txt = request.POST['form-tgt-txt'].strip()
        action_log = request.POST['form-action-log'].strip()
        src_id = int(request.POST['form-src-id'].strip())
        is_complete = bool(int(request.POST['form-complete'].strip()))
        
        tm_view_utils.save_tgt(request.user, src_id, tgt_lang_id,
                               tgt_txt, action_log, is_complete)

        # Send the user to the next translation
        return HttpResponseRedirect('/tm/tr/')
    
@login_required
def history(request, src_id):
    """
    Args:
    Returns:
    Raises:
    """
    src = tm_view_utils.get_src(src_id)
    if not src:
        raise Http404

    tgt_list = TargetTxt.objects.select_related().filter(src=src.id).order_by('-date')
    return render_to_response('tm/history.html',
                              {'src':src, 'tgt_list':tgt_list},
                              context_instance=RequestContext(request))
