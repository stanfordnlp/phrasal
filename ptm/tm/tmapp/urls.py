from django.conf.urls import patterns, url

urlpatterns = patterns('tmapp.views',
    url(r'^$', 'index'),
    url(r'^training/$', 'training'),
    url(r'^training/(?P<step_id>\d+)/$', 'training'),
    url(r'^training/ui/$', 'training_ui'),
    url(r'^translate/$', 'translate'),
    url(r'^demographic/$', 'form_demographic'),
    url(r'^exit/$', 'form_exit'),
)
