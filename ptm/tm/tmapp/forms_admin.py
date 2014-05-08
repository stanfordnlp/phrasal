import logging
import json
from django.contrib import admin
from django.contrib.auth.models import User
from django.contrib.auth.forms import UserCreationForm
from django import forms

from tmapp.models import TranslationSession,UserConfiguration,SourceDocument,Language

logger = logging.getLogger(__name__)

class ExperimentAdminForm(admin.ModelAdmin):
    """
    User authentication model based on a json specification.
    """
    def save_model(self, request, obj, form, change):
        """
        Create and configure all Users and TranslationSessions
        """
        spec = json.loads(obj.json_spec)
        user = None
        for username,spec in spec.iteritems():
            logger.debug(username)
            # Create the user object
            #user = super(UserCreationForm, self).save(commit=False)
            user = User.objects.create(username=username)
            user.username = username
            user.set_password(spec['password'].strip())
            user.save()

            # Create the user configuration object
            src = spec['src_lang']
            tgt = spec['tgt_lang']
            src_lang = Language.objects.get(code=src)
            tgt_lang = Language.objects.get(code=tgt)
            user_configuration = UserConfiguration.objects.create(user=user,source_language=src_lang,
                                                                  target_language=tgt_lang)
            user_configuration.save()
            
            # Create and save the translation sessions
            for i,session_spec in enumerate(spec['sessions']):
                doc_url = session_spec[0]
                doc_domain = session_spec[1]
                ui = session_spec[2]
                try:
                    src_doc = SourceDocument.objects.get(url=doc_url)
                except SourceDocument.DoesNotExist:
                    src_doc = SourceDocument.objects.create(url=doc_url,
                                                            domain=doc_domain,
                                                            language=src_lang)
                    src_doc.save()
                session = TranslationSession.objects.create(user=user,src_document=src_doc,
                                                            tgt_language=tgt_lang,interface=ui,
                                                            order=i)
                session.save()

            # Create and save the training sessions
            for i,session_spec in enumerate(spec['training']):
                doc_url = session_spec[0]
                doc_domain = session_spec[1]
                ui = session_spec[2]
                try:
                    src_doc = SourceDocument.objects.get(url=doc_url)
                except SourceDocument.DoesNotExist:
                    src_doc = SourceDocument.objects.create(url=doc_url,
                                                            domain=doc_domain,
                                                            language=src_lang)
                    src_doc.save()
                session = TranslationSession.objects.create(user=user,src_document=src_doc,
                                                            tgt_language=tgt_lang,interface=ui,
                                                            training=True,order=i)
                session.save()

        # Save the experiment if everything above completes
        obj.save()
