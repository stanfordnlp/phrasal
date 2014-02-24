from django.conf.urls import patterns, include, url
from django.contrib import admin
from django.views.generic.base import TemplateView, RedirectView

import tmapp.controller

class TextPlainView(TemplateView):
    """
    See: http://www.netboy.pl/2011/10/add-favicon-ico-robots-txt-to-a-django-project/
    """
    def render_to_response(self, context, **kwargs):
        return super(TextPlainView, self).render_to_response(
            context, content_type='text/plain', **kwargs)

admin.autodiscover()

urlpatterns = patterns('',
    url(r'^$', RedirectView.as_view(url='/tm/'), name='site-root'),
    url(r'^tm/', include('tmapp.urls'), name='app-root'),
    url(r'^x', tmapp.controller.service_redirect),
    url(r'^login/$', 'django.contrib.auth.views.login', {'template_name': 'login.html'}),
    url(r'^bye/$', 'django.contrib.auth.views.logout', {'next_page': '/tm'}),
    url(r'^admin/', include(admin.site.urls)),

    url(r'^robots\.txt$', TextPlainView.as_view(template_name='robots.txt')),
    url(r'^favicon\.ico$', RedirectView.as_view(url=settings.STATIC_URL+'favicon.ico')),
)
