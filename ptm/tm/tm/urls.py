from django.conf.urls import patterns, include, url
from django.contrib import admin
from django.views.generic.base import RedirectView

import tmapp.controller

admin.autodiscover()

urlpatterns = patterns('',
    url(r'^$', RedirectView.as_view(url='/tm/'), name='site-root'),
    url(r'^tm/', include('tmapp.urls'), name='app-root'),
    url(r'^x', tmapp.controller.service_redirect),
    url(r'^login/$', 'django.contrib.auth.views.login', {'template_name': 'login.html'}),
    url(r'^bye/$', 'django.contrib.auth.views.logout', {'next_page': '/tm'}),
    url(r'^admin/', include(admin.site.urls)),
)
