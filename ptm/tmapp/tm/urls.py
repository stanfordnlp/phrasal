from django.conf.urls.defaults import patterns, include, url

urlpatterns = patterns('tm.views',
    # PTM translation manager (TM) app
    url(r'^$', 'index'),
    url(r'^(?P<src_id>\d+)/$', 'history'),

    # Legacy view from Summer 2011
    url(r'^(?P<src_id>\d+)/tr/$', 'tr'),

    # Prototype views for 6 Feb.
    url(r'^(?P<src_id>\d+)/tr2/$', 'tr2'),
    url(r'^(?P<src_id>\d+)/tr3/$', 'tr3'),

    url(r'^(?P<src_id>\d+)/trdone/$', 'trdone'), 
)
