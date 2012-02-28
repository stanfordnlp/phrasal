import logging
from django import forms
from tm.models import Country

logger = logging.getLogger(__name__)

class UserTrainingForm(forms.Form):
    """ Self-submitted information by the user.

    Args:
    Returns:
    Raises:
    """
    birth_label = 'Where were you born?'
    birth_country = forms.ModelChoiceField(queryset=Country.objects.all(),
                                           required=True,
                                           label=birth_label)
    home_label = 'Where do you currently live?'
    home_country = forms.ModelChoiceField(queryset=Country.objects.all(),
                                          required=True,
                                          label=home_label)
    hours_label = 'On average, how many hours do you work as a translator each week?'
    hours_per_week = forms.IntegerField(required=True, label=hours_label)
