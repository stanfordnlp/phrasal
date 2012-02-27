import os, sys
app_path='/usr/local/www/tmapp'
if app_path not in sys.path:
   sys.path.append(app_path)

os.environ['DJANGO_SETTINGS_MODULE'] = 'settings'

import django.core.handlers.wsgi
application = django.core.handlers.wsgi.WSGIHandler()

