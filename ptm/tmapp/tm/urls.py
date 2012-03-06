from django.conf.urls.defaults import patterns, include, url

urlpatterns = patterns('tm.views',
    # PTM translation manager (TM) app
    url(r'^$', 'index'),
    url(r'^(?P<src_id>\d+)/$', 'history'),
    url(r'^bye/$', 'bye'),
    url(r'^survey/$','survey'),
    url(r'^training/$', 'training'),
    url(r'^tutorial/(?P<module_id>\d+)/$','tutorial'),
    url(r'^tr/(?P<sample_id>\d+)/$', 'tr'),
)
