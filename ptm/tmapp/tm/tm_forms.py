import logging
from django import forms
from tm.models import Country,LanguageSpec

logger = logging.getLogger(__name__)

class UserTrainingForm(forms.Form):
    """ Self-submitted information by the user.

    Args:
    Returns:
    Raises:
    """
    birth_country = forms.ModelChoiceField(queryset=Country.objects.all(),
                                           label='Where were you born?',
                                           error_messages={'required': 'Required field'})

    home_country = forms.ModelChoiceField(queryset=Country.objects.all(),
                                          label='Where do you currently live?',
                                          error_messages={'required': 'Required field'})

    hours_per_week = forms.IntegerField(label='On average, how many hours per week do you work as a translator?',
                                        error_messages={'required': 'Required field'})

class TranslationInputForm(forms.Form):
    """ Validates a translation submitted by a user. Includes hidden
    metadata used by tmapp to store the proposed translation.

    Args:
    Returns:
    Raises:
    """
    # Store an integer (pk) instead of using the Django infrastructure
    # We don't want to tie this form to a queryset.
    src_id = forms.IntegerField(widget=forms.HiddenInput())

    # a pk for UISpec. Mainly for re-rendering the form in the event that
    # it does not validate
    ui_id = forms.IntegerField(widget=forms.HiddenInput())
    
    tgt_lang = forms.ModelChoiceField(queryset=LanguageSpec.objects.all(),
                                      widget=forms.HiddenInput())

    css_direction = forms.CharField(widget=forms.HiddenInput())
    
    action_log = forms.CharField(widget=forms.HiddenInput())

    # If we want to accept "False" as a valid input, then
    # we must set required=False
    # See: http://docs.djangoproject.com/en/dev/ref/forms/fields/#booleanfield
    is_valid = forms.BooleanField(widget=forms.HiddenInput(),
                                  initial=True,
                                  required=False)

    # This is the only field that is visible to the user
    txt = forms.CharField(widget=forms.Textarea,
                          min_length=1,
                          label='',
                          error_messages={'required': 'You must enter at least one word!'})
