#!/usr/bin/env bash

# Web stack dependencies
sudo apt-get install nginx nginx-doc postresql sqlite3 postgresql-server-dev-9.1

# Python middleware dependencies
sudo apt-get install python-pip
sudo pip install Django==1.6
sudo pip install uwsgi
sudo pip install psycopg2
sudo pip install django-pipeline

# PostgreSQL setup
# TODO: Setup using user postgres permissions

# Middleware Code checkout
#git clone username@jacob.stanford.edu:/u/nlp/git/javanlp.git
# Edit the uwsgi and nginx parameters in tm
# python manage.py syncdb
# python manage.py collectstatic
# Edit the addresses of the MT services in controller.py
# Disable Django debug in settings.py

# nginx start/stop
# sudo ln -s ~/path/to/your/mysite/mysite_nginx.conf /etc/nginx/sites-enabled/
#sudo apt-get install nginx
#sudo /etc/init.d/nginx start    # start nginx

# Startup uwsgi as a persistent process
# (uwsgi --ini uwsgi.ini)

# Login to admin interface:
#  Add languages
#  Add users and user configurations
