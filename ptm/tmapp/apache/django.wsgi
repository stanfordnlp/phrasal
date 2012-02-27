import sys
import os

proj_path='/usr/local/www'
if proj_path not in sys.path:
   sys.path.append(proj_path)

app_path='/usr/local/www/tmapp'
if app_path not in sys.path:
   sys.path.append(app_path)

os.environ['DJANGO_SETTINGS_MODULE'] = 'tmapp.settings'

import django.core.handlers.wsgi
application = django.core.handlers.wsgi.WSGIHandler()

