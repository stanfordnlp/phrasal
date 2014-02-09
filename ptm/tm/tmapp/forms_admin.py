import logging
import json
from django.contrib.auth.models import User
from django.contrib.auth.forms import UserCreationForm
from django import forms

from tmapp.models import TranslationSession,UserConfiguration,SourceDocument,Language

logger = logging.getLogger(__name__)

class UserCreationForm2(UserCreationForm):
    """
    User authentication model based on a json specification.
    """
    json_spec = forms.CharField(label='Json experiment specification',widget=forms.Textarea)

    def __init__(self, *args, **kwargs):
        super(UserCreationForm2, self).__init__(*args, **kwargs)

        # Disable the other required fields from the User model
        for key in self.fields:
            self.fields[key].required = False
        self.fields['json_spec'].required = True
    
    class Meta(UserCreationForm.Meta):
        fields = ('json_spec',)

    def clean(self):
        cleaned_data = self.cleaned_data

        return cleaned_data

    def save(self, commit=True):
        """
        Create and configure all Users and TranslationSessions
        """
        cleaned_data = self.cleaned_data
        spec = json.loads(cleaned_data['json_spec'])
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
                ui = session_spec[1]
                try:
                    src_doc = SourceDocument.objects.get(url=doc_url)
                except SourceDocument.DoesNotExist:
                    src_doc = SourceDocument.objects.create(url=doc_url,language=src_lang)
                    src_doc.save()
                session = TranslationSession.objects.create(user=user,src_document=src_doc,
                                                            tgt_language=tgt_lang,interface=ui,
                                                            order=i)
                session.save()

            # Create and save the training sessions
            for i,session_spec in enumerate(spec['training']):
                doc_url = session_spec[0]
                ui = session_spec[1]
                try:
                    src_doc = SourceDocument.objects.get(url=doc_url)
                except SourceDocument.DoesNotExist:
                    src_doc = SourceDocument.objects.create(url=doc_url,language=src_lang)
                    src_doc.save()
                session = TranslationSession.objects.create(user=user,src_document=src_doc,
                                                            tgt_language=tgt_lang,interface=ui,
                                                            training=True,order=i)
                session.save()

        return user
