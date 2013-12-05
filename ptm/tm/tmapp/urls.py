from django.conf.urls import patterns, url

urlpatterns = patterns('tmapp.views',
    url(r'^$', 'index'),
    url(r'^translate/$', 'translate'),
    url(r'^demographic/$', 'form_demographic'),
    url(r'^exit/$', 'form_exit'),
)
