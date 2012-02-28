from django.conf.urls.defaults import patterns, include, url

urlpatterns = patterns('tm.views',
    # PTM translation manager (TM) app
    url(r'^$', 'index'),
    url(r'^(?P<src_id>\d+)/$', 'history'),
    url(r'^training/$', 'training'),
    url(r'^module_train/(?P<ui_id>\d+)/$','module_train'),
    url(r'^tr/$', 'tr'),
)
