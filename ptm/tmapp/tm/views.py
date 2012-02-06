from datetime import datetime
from django.core.urlresolvers import reverse
from django.http import HttpResponse, Http404, HttpResponseRedirect
from django.template import RequestContext
from django.shortcuts import render_to_response, get_object_or_404
from django.contrib.auth.decorators import login_required
from tm.models import SourceTxt,TargetTxt,LanguageSpec

# Default page (index) is the work queue
@login_required
def index(request):
    # Load up each source sentence with the appropriate css orientation
    src_list = SourceTxt.objects.select_related().all()
    return render_to_response('tm/index.html',
                              {'src_list' : src_list,'first_name' : request.user.first_name, 'last_name' : request.user.last_name},
                              context_instance=RequestContext(request))

@login_required
def tr(request, src_id):
    try:
        src = SourceTxt.objects.select_related().get(pk=src_id)
    except SourceTxt.DoesNotExist:
        raise Http404

    src_toks = src.txt.split()
    lang_list = LanguageSpec.objects.all()
    
    return render_to_response('tm/translate.html',
                              {'src':src, 'src_toks':src_toks, 'lang_list':lang_list},
                              context_instance=RequestContext(request))

@login_required
def trdone(request, src_id):
    src = get_object_or_404(SourceTxt,pk=src_id)
    tgt_user = request.user
    tgt_date = datetime.now()
    tgt_txt = request.POST['form-tgt-txt'].strip()
    tgt_lang = LanguageSpec.objects.get(pk=request.POST['form-tgt-lang'])

    tgt = TargetTxt.objects.create(src=src,user=tgt_user,date=tgt_date,txt=tgt_txt,lang=tgt_lang)
    tgt.save()

    # Save translation session stats here
    
    
    return HttpResponseRedirect(reverse('tm.views.history',args=(src_id,)))
    
@login_required
def history(request, src_id):
    try:
        src = SourceTxt.objects.select_related().get(pk=src_id)
    except SourceTxt.DoesNotExist:
        raise Http404
    # Now get the translations
    tgt_list = TargetTxt.objects.select_related().filter(src=src_id).order_by('-date')
    return render_to_response('tm/history.html',
                              {'src':src, 'tgt_list':tgt_list},
                              context_instance=RequestContext(request))
