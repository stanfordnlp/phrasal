import os, sys
#sys.path.append('/usr/local/django/')
app_path='/usr/local/django/tmapp'
if app_path not in sys.path:
   sys.path.append(app_path)

os.environ['DJANGO_SETTINGS_MODULE'] = 'tmapp.settings'

import django.core.handlers.wsgi

application = django.core.handlers.wsgi.WSGIHandler()