import logging
from django import forms
from django.forms.util import ErrorList
from django.utils.safestring import mark_safe
from models import Language,DemographicData,ExitSurveyData,TranslationSession
from django.forms import ModelForm

import choices

logger = logging.getLogger(__name__)

class DivErrorList(ErrorList):
    """
    Styling of the error indicators for validation failures.
    
    """
    def __unicode__(self):
        return self.as_divs()
    def as_divs(self):
        if not self: return u''
        return u'<div class="errorlist">%s</div>' % ''.join([u'<div class="error">%s</div>' % e for e in self])

class ExitSurveyForm(ModelForm):
    """
    Final questionnaire after the experiment completes.
    """
    class Meta:
        model=ExitSurveyData
        exclude=['user']
        widgets = {
            'stanford_mt_opinion' : forms.RadioSelect,
            'imt_improvement' : forms.RadioSelect,
            'imt_would_use' : forms.RadioSelect,
        }
        labels = {
            'stanford_mt_opinion' : 'To what degree do you agree with the following statement: The MT suggestions were generally useful.',
            'imt_improvement' : 'To what degree do you agree with the following statement: I got better at using the interactive MT interface with practice/experience',
            'imt_would_use' : 'To what degree do you agree with the following statement: I would use an interactive translation tool like this instead of my existing CAT tool.',
            'preferred_ui': 'In which interface condition did you translate most efficiently?',
            'hardest_pos': 'What was the hardest source part of speech to translate?',
            'easiest_pos': 'What was the easiest source part of speech to translate?',
            'hardest_src': 'Please describe the hardest source segments to translate, citing examples if you remember them.',
            'hardest_tgt': 'Please describe the hardest target segments to generate, citing examples if you remember them.',
            'gaze_location': 'When translating, what part of the UI do you generally focus on?',
            'imt_most_useful': 'Which interactive aid did you find most useful?',
            'imt_least_useful' : 'Which interactive aid did you find least useful?',
            'imt_aid_suggestions' : 'Was there an aid not present in the current interface that would have been helpful?',
            'imt_gaze_location' : 'Did you focus on a different part of the UI when using the interactive MT interface?',
            'cat_response' : 'Please describe major strengths and weaknesses of your current CAT tool.',
            'other_response' : 'Any other feedback on the interfaces or experiment?',
        }
        error_messages = {
            'stanford_mt_opinion': {
                'required': ("Required field."),
                },
            'imt_improvement': {
                'required': ("Required field."),
                },
            'imt_would_use': {
                'required': ("Required field."),
                },
            'preferred_ui': {
                'required': ("Required field."),
                },
            'hardest_pos': {
                'required': ("Required field."),
                },
            'easiest_pos': {
                'required': ("Required field."),
                },
            'hardest_src': {
                'required': ("Required field."),
                },
            'hardest_tgt': {
                'required': ("Required field."),
                },
            'gaze_location': {
                'required': ("Required field."),
                },
            'imt_most_useful': {
                'required': ("Required field."),
                },
            'imt_least_useful': {
                'required': ("Required field."),
                },
            'imt_aid_suggestions': {
                'required': ("Required field."),
                },
            'imt_gaze_location': {
                'required': ("Required field."),
                },
            'cat_response': {
                'required': ("Required field."),
                },
        }
    
class DemographicForm(ModelForm):
    """
    Demographic information about the user.
    """
    class Meta:
        model=DemographicData
        exclude=['user']
        widgets = {
            'cat_tool_opinion' : forms.RadioSelect,
            'mt_opinion' :  forms.RadioSelect,
            'is_pro_translator' : forms.RadioSelect,
        }
        labels = {
            'language_native': ('What is your native language?'),
            'birth_country': ('What is the country of your birth?'),
            'resident_of': 'In which country do you currently live?',
            'is_pro_translator': 'Is translation your primary job?',
            'hours_per_week': ('On average, how many hours per week do you work as a translator?'),
            'src_proficiency': mark_safe('Please rate your proficiency in the source language (according to the <a href="http://en.wikipedia.org/wiki/ILR_scale" target="_blank">ILR Scale</a>)'),
            'tgt_proficiency': mark_safe('Please rate your proficiency in the target language (according to the <a href="http://en.wikipedia.org/wiki/ILR_scale" target="_blank">ILR Scale</a>)'),
            'mt_opinion' : 'To what degree do you agree with the following statement: Machine translation output is generally useful in my translation work.',
            'cat_tool': ('What is your primary CAT tool? (Select NONE if you do not use a CAT tool)'),
            'cat_tool_opinion' : 'To what degree do you agree with the following statement: I am satisfied with my current CAT tool.',
        }
        error_messages = {
            'is_pro_translator': {
                'required': ("Required field."),
                },
            'language_native': {
                'required': ("Required field."),
                },
            'birth_country': {
                'required': ("Required field."),
                },
            'resident_of': {
                'required': ("Required field."),
                },
            'hours_per_week': {
                'required': ("Required field."),
                },
            'src_proficiency': {
                'required': ("Required field."),
                },
            'tgt_proficiency': {
                'required': ("Required field."),
                },
            'cat_tool': {
                'required': ("Required field."),
                },
            'cat_tool_opinion': {
                'required': ("Required field."),
                },
            'mt_opinion': {
                'required': ("Required field."),
                },
        }

class TranslationInputForm(ModelForm):
    """
    Result of a translation session
    """
    class Meta:
        model = TranslationSession
        exclude=['user','timestamp']
        widgets = {
            'src_document' : forms.HiddenInput,
            'tgt_language' : forms.HiddenInput,
            'interface' : forms.HiddenInput,
            'order' : forms.HiddenInput,
            'training' : forms.HiddenInput,
            'text' : forms.HiddenInput,
            'log' : forms.HiddenInput
        }

