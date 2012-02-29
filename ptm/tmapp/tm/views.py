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
import tm_forms

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
        # User lookup failed --> No UserConf for this user
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
def tutorial(request, ui_id):
    """ Shows this users enabled interfaces in order

    Args:
    Returns:
    Raises:
    """
    ui_id = int(ui_id)
    if request.method == 'POST':
        ui_id = tm_train_module.next_training_ui_id(ui_id)
        
    if ui_id:
        src = tm_train_module.get_src(request.user, ui_id)
        if src:
            template = tm_view_utils.get_template_for_ui(src.ui.name)
            if template:
                tgt_lang = tm_workqueue.select_tgt_language(request.user, src.id)
                initial={'src_id':src.id,
                         'tgt_lang':tgt_lang,
                         'action_log':'ERROR'}
                form = tm_forms.TranslationInputForm(initial=initial)
                src_toks = src.txt.split()
                action = '/tm/tutorial/%d/' % (ui_id)
                return render_to_response(template,
                                          {'tgt_lang_name':tgt_lang.name,
                                           'src_css_dir':src.lang.css_direction,
                                           'src_toks':src_toks,
                                           'form_action':action,
                                           'form':form },
                                          context_instance=RequestContext(request))
            
    else:
        return HttpResponseRedirect('/tm/')
    
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
    if not (src_lang and tgt_lang):
        raise Http404

    if request.method == 'GET':
        # Blank form
        survey_form = tm_forms.UserTrainingForm()
        return render_to_response('tm/train_exp1.html',
                                  {'src_lang':src_lang.name,
                                   'survey_form':survey_form,
                                   'tgt_lang':tgt_lang.name},
                                  context_instance=RequestContext(request))
    
    elif request.method == 'POST':
        # User has submitted the form. Validate it.
        form = tm_forms.UserTrainingForm(request.POST)
        if form.is_valid():
            tm_train_module.done_training(request.user,
                                          set_done=True,
                                          form=form)
            return HttpResponseRedirect('/tm/tutorial/1')
        else:
            # Form was invalid
            logger.debug('Form validation error')
            return render_to_response('tm/train_exp1.html',
                                      {'src_lang':src_lang.name,
                                       'survey_form':form,
                                       'tgt_lang':tgt_lang.name},
                                      context_instance=RequestContext(request))
        
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
        # Select a new source sentence for translation
        src = tm_workqueue.select_src(request.user)
        if src:
            template = tm_view_utils.get_template_for_ui(src.ui.name)
            if template:
                tgt_lang = tm_workqueue.select_tgt_language(request.user, src.id)
                initial={'src_id':src.id,
                         'tgt_lang':tgt_lang,
                         'action_log':'ERROR'}
                form = tm_forms.TranslationInputForm(initial=initial)
                src_toks = src.txt.split()
                return render_to_response(template,
                                          {'tgt_lang_name':tgt_lang.name,
                                           'src_css_dir':src.lang.css_direction,
                                           'src_toks':src_toks,
                                           'form_action':'/tm/tr/',
                                           'form':form },
                                          context_instance=RequestContext(request))
            else:
                logger.error('Could not select a template for src: '
                             + repr(src))
                raise Http404
        else:
            # User has completed this block. Go back to the index.
            return HttpResponseRedirect('/tm/')

    elif request.method == 'POST':
        form = tm_forms.TranslationInputForm(request.POST)
        if form.is_valid():
            tm_view_utils.save_tgt(request.user, form)
            # Send the user to the next translation
            return HttpResponseRedirect('/tm/tr/')
        else:
            logger.warn('User %s entered an empty translation' % (request.user.username))
            # The workqueue algorithm is deterministic
            # at least once the active_document field is set,
            # so we should get the same source sentence back here.
            src = tm_workqueue.select_src(request.user)
            if not src:
                logger.error('Could not re-retrieve source sentence for user %s' % (request.user.username))
                raise Http404
            tgt_lang = tm_workqueue.select_tgt_language(request.user, src.id)
            template = tm_view_utils.get_template_for_ui(src.ui.name)
            src_toks = src.txt.split()
            return render_to_response(template,
                                          {'tgt_lang_name':tgt_lang.name,
                                           'src_css_dir':src.lang.css_direction,
                                           'src_toks':src_toks,
                                           'form_action':'/tm/tr/',
                                           'form':form },
                                          context_instance=RequestContext(request))
    
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
