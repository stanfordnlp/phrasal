from django.conf.urls.defaults import patterns, include, url
from django.contrib.staticfiles.urls import staticfiles_urlpatterns
from django.views.generic.simple import redirect_to

# Enable the admin interface
from django.contrib import admin
admin.autodiscover()

urlpatterns = patterns('',
    # Redirect requests to the root to the TM app
    url(r'^$', redirect_to, {'url': '/tm/'}),

    # PTM translation manager (TM) app
    url(r'^tm/', include('tm.urls')),

    # User authentication for the whole site
    url(r'^login/$', 'django.contrib.auth.views.login', {'template_name': 'login.html'}),
                       
    # Uncomment the admin/doc line below to enable admin documentation:
    url(r'^admin/doc/', include('django.contrib.admindocs.urls')),
    # Uncomment the next line to enable the admin:
    url(r'^admin/', include(admin.site.urls)),
)

# WARNING: Disable this for production!!
urlpatterns += staticfiles_urlpatterns()
