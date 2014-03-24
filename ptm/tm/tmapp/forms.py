import logging
import re
from django import forms
from django.forms.util import ErrorList
from django.utils.safestring import mark_safe
from models import Language,DemographicData,ExitSurveyData,TranslationSession
from django.forms import ModelForm

logger = logging.getLogger(__name__)

# Strip whitespace in freeform text fields.
normalize_whitespace = lambda x : re.sub(r'\s+', ' ', x.strip(), re.U)

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
    def clean_exit_technical_comparison(self):
        data = self.cleaned_data['exit_technical_comparison']
        logger.debug(data)
        return normalize_whitespace(self.cleaned_data['exit_technical_comparison'])
    def clean_exit_hardest_source(self):
        return normalize_whitespace(self.cleaned_data['exit_hardest_source'])
    def clean_exit_hardest_target(self):
        return normalize_whitespace(self.cleaned_data['exit_hardest_target'])
    def clean_exit_cat_strength_weakness(self):
        return normalize_whitespace(self.cleaned_data['exit_cat_strength_weakness'])
    def clean_exit_itm_strength_weakness(self):
        return normalize_whitespace(self.cleaned_data['exit_itm_strength_weakness'])
    def clean_exit_itm_missing_aid(self):
        return normalize_whitespace(self.cleaned_data['exit_itm_missing_aid'])
    def clean_exit_comments(self):
        return normalize_whitespace(self.cleaned_data['exit_comments'])
    
    class Meta:
        model=ExitSurveyData
        exclude=['user']
        widgets = {
            'exit_data_comparison' : forms.RadioSelect,
            'exit_fatigue_comparison' : forms.RadioSelect,
            'exit_hardest_pos' : forms.RadioSelect,
            'exit_easiest_pos' : forms.RadioSelect,
            'exit_focus_in_postedit' : forms.RadioSelect,
            'exit_focus_in_imt' : forms.RadioSelect,
            'exit_like_better' : forms.RadioSelect,
            'exit_more_efficient' : forms.RadioSelect,
            'exit_itm_most_useful' : forms.RadioSelect,
            'exit_itm_least_useful' : forms.RadioSelect,
            'exit_useful_src_lookup' : forms.RadioSelect,
            'exit_useful_tgt_inlined' : forms.RadioSelect,
            'exit_useful_tgt_suggestions' : forms.RadioSelect,
            'exit_useful_tgt_completion' : forms.RadioSelect,
            'exit_useful_tgt_chunking' : forms.RadioSelect,
            'exit_useful_tgt_anywhere' : forms.RadioSelect,
            'exit_prefer_itm' : forms.RadioSelect,
            'exit_got_better_at_itm' : forms.RadioSelect
        }
        labels = {
            'exit_data_comparison' : 'Were the documents in the two modules equally difficult to translate?',
            'exit_fatigue_comparison' : 'Did you notice that fatigue affected your ability to translate in the second translation module relative to the first translation module?',
            'exit_technical_comparison' : 'Did you experience any technical issues with the software during the experiment? If yes, please describe:',
            'exit_hardest_pos' : 'What was the hardest source part of speech to translate?',
            'exit_easiest_pos' : 'What was the easiest source part of speech to translate?',
            'exit_hardest_source' : 'Please describe the hardest source segments to translate, citing examples if you remember them.',
            'exit_hardest_target' : 'Please describe the hardest target segments to generate, citing examples if you remember them.',
            'exit_focus_in_postedit' : 'When translating with the post-edit interface, what part of the UI do you generally focus on?',
            'exit_focus_in_imt' : 'When translating with the interactive interface, what part of the UI do you generally focus on?',
            'exit_like_better' : 'In general, which interface did you prefer?',
            'exit_more_efficient' : 'In which interface did you feel most productive?',
            'exit_itm_most_useful' : 'In the interactive interface: Which interactive aid did you find most useful?',
            'exit_itm_least_useful' : 'In the interactive interface: Which interactive aid did you find least useful?',
            'exit_useful_src_lookup' : 'In the interactive interface: Source text lookup with the mouse was generally useful.',
            'exit_useful_tgt_inlined' : 'In the interactive interface: Inlined target translations (gray text in the typing area) were generally useful.',
            'exit_useful_tgt_suggestions' : 'In the interactive interface: Drop-down target suggestions were generally useful.',
            'exit_useful_tgt_completion' : 'In the interactive interface: Highlighting of the source coverage during translation was generally useful.',
            'exit_useful_tgt_chunking' : 'In the interactive interface: Suggestions of phrases (groups of words longer than two) were generally useful.',
            'exit_useful_tgt_anywhere' : 'In the interactive interface: Bringing any gray text into the drop-down suggestion box by typing its prefix was generally useful.',
            'exit_cat_strength_weakness' : 'Please describe major strengths and weaknesses of the post-edit mode in general.',
            'exit_itm_strength_weakness' : 'Please describe major strengths and weaknesses of the interactive translation mode in general.',
            'exit_itm_missing_aid' : 'Was there an aid not present in the current interactive interface that would have been helpful?',
            'exit_prefer_itm' : 'I would use interactive translation features if they were integrated into a CAT product that included other standard features such as translation workflow, translation memories, formatting, etc.',
            'exit_got_better_at_itm' : 'I got better at using the interactive interface with practice/experience.',
            'exit_comments' : 'Any other feedback on the interfaces or experiment?'
        }
        error_messages = {
            'exit_data_comparison' : { 'required': ("Required field.") },
            'exit_fatigue_comparison' : { 'required': ("Required field.") },
            'exit_technical_comparison' : { 'required': ("Required field.") },
            'exit_hardest_pos' : { 'required': ("Required field.") },
            'exit_easiest_pos' : { 'required': ("Required field.") },
            'exit_hardest_source' : { 'required': ("Required field.") },
            'exit_hardest_target' : { 'required': ("Required field.") },
            'exit_focus_in_postedit' : { 'required': ("Required field.") },
            'exit_focus_in_imt' : { 'required': ("Required field.") },
            'exit_like_better' : { 'required': ("Required field.") },
            'exit_more_efficient' : { 'required': ("Required field.") },
            'exit_itm_most_useful' : { 'required': ("Required field.") },
            'exit_itm_least_useful' : { 'required': ("Required field.") },
            'exit_useful_src_lookup' : { 'required': ("Required field.") },
            'exit_useful_tgt_inlined' : { 'required': ("Required field.") },
            'exit_useful_tgt_suggestions' : { 'required': ("Required field.") },
            'exit_useful_tgt_completion' : { 'required': ("Required field.") },
            'exit_useful_tgt_chunking' : { 'required': ("Required field.") },
            'exit_useful_tgt_anywhere' : { 'required': ("Required field.") },
            'exit_cat_strength_weakness' : { 'required': ("Required field.") },
            'exit_itm_strength_weakness' : { 'required': ("Required field.") },
            'exit_itm_missing_aid' : { 'required': ("Required field.") },
            'exit_prefer_itm' : { 'required': ("Required field.") },
            'exit_got_better_at_itm' : { 'required': ("Required field.") },
            'exit_comments' : { 'required': ("Required field.") }
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
            'is_pro_translator': { 'required': ("Required field.") },
            'language_native': { 'required': ("Required field.") },
            'birth_country': { 'required': ("Required field.") },
            'resident_of': { 'required': ("Required field.") },
            'hours_per_week': { 'required': ("Required field.") },
            'src_proficiency': { 'required': ("Required field.") },
            'tgt_proficiency': { 'required': ("Required field.") },
            'cat_tool': { 'required': ("Required field.") },
            'cat_tool_opinion': { 'required': ("Required field.") },
            'mt_opinion': { 'required': ("Required field.") },
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
            'log' : forms.HiddenInput,
            'valid' : forms.HiddenInput
        }

