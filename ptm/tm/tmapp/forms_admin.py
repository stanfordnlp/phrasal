import logging
import json
from django.contrib.auth.models import User
from django.contrib.auth.forms import UserCreationForm
from django import forms

logger = logging.getLogger(__name__)

class UserCreationForm2(UserCreationForm):
    """
    User authentication model based on a json specification.
    """
    json_spec = forms.CharField(label='Json user specification',widget=forms.Textarea)

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

        # Parse the json spec
        json_str = cleaned_data['json_spec']
        d = json.loads(json_str)

        username = d['username']
        cleaned_data['username'] = username
        try:
            User._default_manager.get(username=username)
            raise forms.ValidationError(
                self.error_messages['duplicate_username'],
                code='duplicate_username',
                )
        except User.DoesNotExist:
            # Ok. Username is unique
            pass
    
        password1 = d['password']
        cleaned_data['password1'] = password1
        password2 = d['password']
        cleaned_data['password2'] = password2
        if password1 and password2 and password1 != password2:
            raise forms.ValidationError(
                self.error_messages['password_mismatch'],
                code='password_mismatch',
            )
        
        return cleaned_data

    def save(self, commit=True):
        user = super(UserCreationForm, self).save(commit=False)
        user.username = self.cleaned_data['username']
        user.set_password('password1')
        if commit:
            user.save()
            # TODO(spenceg): If we get this far, then we should create the
            # other user data, including translation sessions and training records

        return user
