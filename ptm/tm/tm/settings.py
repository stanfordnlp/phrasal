"""
Django settings for tm project.

For more information on this file, see
https://docs.djangoproject.com/en/1.6/topics/settings/

For the full list of settings and their values, see
https://docs.djangoproject.com/en/1.6/ref/settings/
"""

#
# TODO: Disable debug for deployment
#
DEBUG = True
TEMPLATE_DEBUG = True

#
# TODO: Point to server checkout of the UI repo
#
UI_DIR = '/home/rayder441/sandbox/translate/client_src'

# Build paths inside the project like this: os.path.join(BASE_DIR, ...)
import os
BASE_DIR = os.path.dirname(os.path.dirname(__file__))

# Quick-start development settings - unsuitable for production
# See https://docs.djangoproject.com/en/1.6/howto/deployment/checklist/

# SECURITY WARNING: keep the secret key used in production secret!
SECRET_KEY = '4d40c118!g2(q9yf#)e!yfg6uhf(+rxmgjz5%y0&e+ki=w21yo'

ALLOWED_HOSTS = []


# Application definition

INSTALLED_APPS = (
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'tmapp',
)

MIDDLEWARE_CLASSES = (
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
)

ROOT_URLCONF = 'tm.urls'

WSGI_APPLICATION = 'tm.wsgi.application'

# URLs for authentication
LOGIN_URL = '/login/'
LOGIN_REDIRECT_URL = '/'
LOGOUT_URL = '/bye/'

# Database
# https://docs.djangoproject.com/en/1.6/ref/settings/#databases

DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.sqlite3',
        'NAME': os.path.join(BASE_DIR, 'db.sqlite3'),
    }
}

# Internationalization
# https://docs.djangoproject.com/en/1.6/topics/i18n/

LANGUAGE_CODE = 'en-us'

TIME_ZONE = 'America/Los_Angeles'

USE_I18N = True

USE_L10N = True

USE_TZ = True


#
# Static files setup
#
STATIC_URL = '/static/'
STATIC_ROOT = os.path.join(BASE_DIR,'static_root')

STATICFILES_DIRS = (
    os.path.join(BASE_DIR,'static'),
    os.path.join(UI_DIR,'css'),
    os.path.join(UI_DIR,'js'),
    os.path.join(UI_DIR,'data'),
    os.path.join(UI_DIR,'static'),
)

STATICFILES_FINDERS = (
    'django.contrib.staticfiles.finders.FileSystemFinder',
    'django.contrib.staticfiles.finders.AppDirectoriesFinder',
)

#
# Templates setup
#
TEMPLATE_DIRS = (
    os.path.join(BASE_DIR,'templates'),
    os.path.join(BASE_DIR,'tmapp/templates'),
)

# List of callables that know how to import templates from various sources.
TEMPLATE_LOADERS = (
    'django.template.loaders.filesystem.Loader',
    'django.template.loaders.app_directories.Loader',
)

LOGGING = {
    'version': 1,
    'disable_existing_loggers': True,
    'formatters': {
        'verbose': {
            'format': '%(levelname)s %(asctime)s %(module)s %(process)d %(thread)d %(message)s'
        }
    },
    'handlers': {
        'null': {
            'level':'DEBUG',
            'class':'django.utils.log.NullHandler',
        },
        'default': {
            'level':'DEBUG',
            'class':'logging.FileHandler',
            'filename': os.path.join(BASE_DIR,'logs/server.log'),
            'formatter':'verbose',
        },  
        'request_handler': {
            'level':'DEBUG',
            'class':'logging.FileHandler',
            'filename': os.path.join(BASE_DIR,'logs/request.log'),
            'formatter':'verbose',
        },
    },
    'loggers': {
        '': { # Catch-all logger that handles all events
            'handlers': ['default'],
            'level': 'DEBUG',
            'propagate': True
        },
        'django.request': { # Logs all requests to the server
            'handlers': ['request_handler'],
            'level': 'DEBUG',
            'propagate': False
        },
        'django.db.backends': { # Stop SQL debug from logging to main logger
            'handlers': ['null'],
            'level': 'DEBUG',
            'propagate': False
        },
    }
}
