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
    # Check to see if this user has finished training
    user_took_training = tm_train_module.done_training(request.user)
    if user_took_training == None:
        logger.error('Missing conf file for user: ' + request.user.username)
        raise Http404

    # Select the user's current module
    module_name = 'train'
    tr_url = ''
    if user_took_training:
        (module_name, sample_id) = tm_workqueue.has_samples(request.user)
        if module_name:
            logger.info('Module %s still active for %s' % (module_name,str(request.user)))
            tr_url = '/tm/tr/%d/' % (sample_id)
        else:
            # Select a new module
            module_name = tm_workqueue.select_new_module(request.user)
            if module_name == None:
                logger.info('%s has finished all modules' % (str(request.user)))
                module_name = 'none'
            else:
                (module_name, sample_id) = tm_workqueue.has_samples(request.user)
                logger.info('Selected module %s for user %s' % (module_name,str(request.user.username)))
                tr_url = '/tm/tr/%d/' % (sample_id)
    else:
        # Sanity check -- purge the experimental samples
        n_samples = tm_workqueue.purge_samples(request.user)
        if n_samples:
            logger.warn('User %s has not completed traning, but purged %d samples' % (request.user.username, n_samples))

    display_name = request.user.first_name
    if not display_name:
        display_name = request.user.username

    return render_to_response('tm/index.html',
                              {'module_name':module_name,
                               'name' : display_name,
                               'tr_url' : tr_url},
                              context_instance=RequestContext(request))

@login_required
def tutorial(request, module_id):
    """ Guides the user through a tutorial

    Args:
    Returns:
    Raises:
    """
    last_module = None
    if request.method == 'POST':
        # Select the next ExperimentModule
        last_module = tm_workqueue.get_experiment_module(int(module_id))

    module = tm_train_module.next_training_module(request.user,last_module)
        
    if not module:
        # User has completed training. Redirect to the workqueue
        return HttpResponseRedirect('/tm/')
    else:
        ui_id = module.ui.id
        (src_lang,tgt_lang) = tm_user_utils.get_user_langs(request.user)
        src = tm_train_module.get_src(src_lang)
        if src:
            template = tm_view_utils.get_template_for_ui(ui_id)
            if not template:
                logger.error('Could not find a template for ui id: ' + str(ui_id))
                raise Http404
            else:
                header_txt = 'Example of %s (Translate to %s)' % (module.ui.display_name,tgt_lang.name)
                txt_suggest = tm_view_utils.get_suggestion(src,
                                                           tgt_lang,
                                                           ui_id)
                initial={'src_id':src.id,
                         'txt':txt_suggest,
                         'ui_id':ui_id,
                         'tgt_lang':tgt_lang,
                         'action_log':'ERROR',
                         'css_direction':tgt_lang.css_direction}
                form = tm_forms.TranslationInputForm(initial=initial)
                src_toks = src.txt.split()
                action = '/tm/tutorial/%d/' % (ui_id)
                return render_to_response(template,
                                          {'header_txt':header_txt,
                                           'src_css_dir':src.lang.css_direction,
                                           'src_toks':src_toks,
                                           'form_action':action,
                                           'form':form },
                                          context_instance=RequestContext(request))
            
    
@login_required
def training(request):
    """ Shows the training page to the user.

    Args:
    Returns:
    Raises:
    """
    (src_lang,tgt_lang) = tm_user_utils.get_user_langs(request.user)

    if request.method == 'GET':
        # Create an unbound survey form.
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
            module = tm_train_module.next_training_module(request.user,None)
            if module:
                return HttpResponseRedirect('/tm/tutorial/%d/' % (module.id))
            else:
                logger.error('No active modules for user ' + request.user.username)
                raise Http404
        else:
            # Form was invalid. Return the bound form for correction.
            return render_to_response('tm/train_exp1.html',
                                      {'src_lang':src_lang.name,
                                       'survey_form':form,
                                       'tgt_lang':tgt_lang.name},
                                      context_instance=RequestContext(request))
        
@login_required
def tr(request,sample_id):
    """
     On GET: selects a translation interface and source sentence for this
     user.
     On POST: saves a completed translation
     
    Args:
    Returns:
    Raises:
      Http404 on server error.
    """
    (src_lang,tgt_lang) = tm_user_utils.get_user_langs(request.user)
    sample_id = int(sample_id)
    
    if request.method == 'GET':
        # Select a new source sentence for translation
        sample = tm_workqueue.get_sample(request.user, sample_id)
        if not sample:
            logger.error('Invalid user sample request: %s %d' % (str(request.user), sample_id))
            raise Http404
        else:
            src = sample.src
            ui_id = sample.module.ui.id
            template = tm_view_utils.get_template_for_ui(ui_id)
            if template:
                txt_suggest = tm_view_utils.get_suggestion(src,
                                                           tgt_lang,
                                                           ui_id)
                logger.debug('%d suggestion: %s' % (src.id,txt_suggest))
                header_txt = 'Translate to ' + tgt_lang.name
                initial={'src_id':src.id,
                         'txt':txt_suggest,
                         'ui_id':ui_id,
                         'tgt_lang':tgt_lang,
                         'action_log':'ERROR',
                         'css_direction':tgt_lang.css_direction}
                form = tm_forms.TranslationInputForm(initial=initial)
                src_toks = src.txt.split()
                action = '/tm/tr/%d/' % (sample_id)
                return render_to_response(template,
                                          {'header_txt':header_txt,
                                           'src_css_dir':src.lang.css_direction,
                                           
                                           'src_toks':src_toks,
                                           'form_action':action,
                                           'form':form },
                                          context_instance=RequestContext(request))
            else:
                logger.error('Could not select a template for src: '
                             + repr(src))
                raise Http404

    elif request.method == 'POST':
        form = tm_forms.TranslationInputForm(request.POST)
        if form.is_valid():
            tm_view_utils.save_tgt(request.user, form)
            tm_workqueue.delete_sample(sample_id)
            sample_id = tm_workqueue.poll_next_sample_id(request.user)
            if sample_id:
                return HttpResponseRedirect('/tm/tr/%d/' % (sample_id))
            else:
                # User has completed this module. Go back to the index.
                return HttpResponseRedirect('/tm/')
        else:
            logger.warn('User %s entered an empty translation' % (request.user.username))
            sample = tm_workqueue.get_sample(request.user, sample_id)
            ui_id = int(form['ui_id'].value().strip())
            template = tm_view_utils.get_template_for_ui(ui_id)
            header_txt = 'Translate to ' + tgt_lang.name
            src_toks = sample.src.txt.split()
            css_dir = sample.src.lang.css_direction
            action = '/tm/tr/%d/' % (sample_id)
            return render_to_response(template,
                                      {'header_txt':header_txt,
                                       'src_css_dir':css_dir,
                                       'src_toks':src_toks,
                                       'form_action':action,
                                       'form':form },
                                      context_instance=RequestContext(request))
    
@login_required
def history(request, src_id):
    """ TODO(spenceg): This needs to be re-written for the current state
    of the backend.
    
    Args:
    Returns:
    Raises:
    """
    src = tm_view_utils.get_src(src_id)
    if not src:
        raise Http404

    tgt_list = TargetTxt.objects.select_related().filter(src=src).order_by('-date')
    return render_to_response('tm/history.html',
                              {'src':src, 'tgt_list':tgt_list},
                              context_instance=RequestContext(request))
