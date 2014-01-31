from django.db import models
from django.contrib.auth.models import User

import choices

## TODO:
##  * Add related_to field to ForeignKey objects? Did
##    that in the original impl and can't figure out why
##  * Language column, SourceDocument must be populated in the database setup.

class Language(models.Model):
    """ Specification of a (human) language. Mostly contains details for rendering the language in the browser.

    Has an autoincrementing id field.

    """
    # 2 character ISO-659-1 code (e.g., en)
    code = models.CharField(max_length=2,
                            choices=choices.LANGUAGES)

    layout_direction = models.CharField(max_length=3,
                                        choices=choices.LAYOUT)

    # This object will be used in forms, so just return the id
    def __unicode__(self):
        return dict(choices.LANGUAGES)[self.code]

class SourceDocument(models.Model):
    """
    A source document. 
    """
    url = models.CharField(max_length=150)
    language = models.ForeignKey(Language,
                                 related_name='+')
    
    def __unicode__(self):
        return self.url
    
class UserConfiguration(models.Model):
    """
    Extends the User data model with application specific
    configuration settings.
    """
    user = models.OneToOneField(User)
    source_language = models.ForeignKey(Language,
                                        related_name='+')
    target_language = models.ForeignKey(Language,
                                        related_name='+')

    def __unicode__(self):
        return '%s %s->%s' % (self.user.username,
                              self.source_language.code,
                              self.target_language.code)
    
class TrainingRecord(models.Model):
    """
    Created when the user has completed training.
    
    """
    user = models.ForeignKey(User)
    timestamp = models.DateTimeField(auto_now=True,auto_now_add=True)

    def __unicode__(self):
        return self.user.username

class TranslationSession(models.Model):
    """
    A translation session bound to a unique
    user/document/interface tuple. Has an order field
    to indicate the order in which it should be presented
    to the user.
    """
    user = models.ForeignKey(User,related_name='+')
    src_document = models.ForeignKey(SourceDocument,
                                     related_name='+')
    tgt_language = models.ForeignKey(Language,
                                     related_name='+')

    interface = models.CharField(max_length=10, choices=choices.INTERFACES)
        
    # Order in which to present to the user
    order = models.PositiveIntegerField()

    create_time = models.DateTimeField(auto_now=True,
                                       auto_now_add=True,
                                       editable=False)

    start_time = models.DateTimeField(null=True,blank=True,editable=False)

    end_time = models.DateTimeField(null=True,blank=True,editable=False)
    
    complete = models.BooleanField(default=False,editable=False)

    training = models.BooleanField(default=False)

    valid = models.BooleanField(default=True)
    
    # Target text (separated by a delimiter)
    text = models.TextField(null=True,blank=True)
    
    # The session log
    log = models.TextField(null=True,blank=True)

    def __unicode__(self):
        return '%s %s %s' % (self.user.username,
                             self.src_document.url,
                             self.tgt_language.code)
    
class DemographicData(models.Model):
    """
    Demographic data about a user and his work environment.
    """
    user = models.OneToOneField(User)
    
    language_native = models.ForeignKey(Language,
                                        related_name='+')

    birth_country = models.CharField(max_length=2,
                                     choices=choices.COUNTRY_CHOICES)

    resident_of = models.CharField(max_length=2,
                                   choices=choices.COUNTRY_CHOICES)

    # Average number of hours that this user works as
    # a translator each week
    hours_per_week = models.PositiveIntegerField()

    is_pro_translator = models.BooleanField(choices=choices.BOOLEAN_CHOICES,
                                            default=False)

    src_proficiency = models.PositiveSmallIntegerField(choices=choices.ILR_CHOICES)

    tgt_proficiency = models.PositiveSmallIntegerField(choices=choices.ILR_CHOICES)

    cat_tool = models.CharField(max_length=20,
                                choices=choices.CAT_CHOICES,
                                default='none')

    cat_tool_opinion = models.PositiveSmallIntegerField(choices=choices.LIKERT_CHOICES,default=3)
    
    mt_opinion = models.PositiveSmallIntegerField(choices=choices.LIKERT_CHOICES,default=3)
    
    def __unicode__(self):
        return '%s: native:%s' % (self.user.username,
                                  self.language_native.code)

class ExitSurveyData(models.Model):
	"""
	Exit survey entered by the user
	"""
	user = models.OneToOneField(User)

	exit_hardest_pos = models.CharField( max_length = 3, choices = choices.POS_CHOICES, default = None )
	exit_easiest_pos = models.CharField( max_length = 3, choices = choices.POS_CHOICES, default = None )
	exit_hardest_source = models.TextField()
	exit_hardest_target = models.TextField()
	exit_focus_in_postedit = models.CharField( max_length = 5, choices = choices.POSTEDIT_GAZE, default = None )
	exit_focus_in_imt = models.CharField( max_length = 5, choices = choices.ITM_GAZE, default = None )
	exit_like_better = models.CharField( max_length = 3, choices = choices.INTERFACES, default = None )
	exit_more_efficient = models.CharField( max_length = 3, choices = choices.INTERFACES, default = None )
	exit_itm_most_useful = models.CharField( max_length = 5, choices = choices.ITM_UI_ELEMENTS, default = None )
	exit_itm_least_useful = models.CharField( max_length = 5, choices = choices.ITM_UI_ELEMENTS, default = None )
	exit_useful_src_lookup = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_inlined = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_suggestions = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_completion = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_chunking = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_anywhere = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_cat_strength_weakness = models.TextField()
	exit_itm_strength_weakness = models.TextField()
	exit_itm_missing_aid = models.TextField()
	exit_prefer_itm = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_got_better_at_itm = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_comments = models.TextField()


    # Experiment/condition questions
    # Which interface was most efficient?
#    preferred_ui = models.CharField(max_length=3,
#                                    choices=choices.INTERFACES)
    
    # Which POS categories was the hardest to translate?
#    hardest_pos = models.CharField(max_length=3,
#                                   choices=choices.POS_CHOICES)

#    easiest_pos = models.CharField(max_length=3,
#                                   choices=choices.POS_CHOICES)
    
    # Which source hardest to translate?
#    hardest_src = models.TextField()
    
    # Which target hardest to generate?
#    hardest_tgt = models.TextField()
    
    # Likert: the MT suggestions were useful
#    stanford_mt_opinion = models.PositiveSmallIntegerField(choices=choices.LIKERT_CHOICES,default=3)
    
    # When translating, what part of the UI do you usually focus on?
#    gaze_location = models.CharField(max_length=5,
#                                     choices=choices.GAZE_CHOICES)
    
    # UI-imt questions
    # Which aid did you find most useful?
#    imt_most_useful = models.CharField(max_length=20,
#                                       choices=choices.IMT_AID_CHOICES)
    
    # Which aid was least effective?
#    imt_least_useful = models.CharField(max_length=20,
#                                        choices=choices.IMT_AID_CHOICES)
    
    # Likert: I became more efficient with practice
#    imt_improvement = models.PositiveSmallIntegerField(choices=choices.LIKERT_CHOICES,default=3)
     
    # Text: Was there an aid not present in the current interface that would have been helpful?
#    imt_aid_suggestions = models.TextField()
    
    # Did you focus on a different part of the UI when using IMT?
    # When translating, what part of the UI do you usually focus on?
#    imt_gaze_location = models.CharField(max_length=5,
#                                         choices=choices.GAZE_CHOICES)
    
    # UI-imt relative to other CAT tools
    # Likert: I would use an tool like this instead of my existing CAT tool
#    imt_would_use = models.PositiveSmallIntegerField(choices=choices.LIKERT_CHOICES,default=3)
    
    # Text: Please describe major strengths and weaknesses of your current CAT tool.
#    cat_response = models.TextField()
    
    # Text: Anything else
#    other_response = models.TextField(blank=True,null=True)
