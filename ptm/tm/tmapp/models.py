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

class Experiment(models.Model):
    """
    Created when a full experiment configuration is loaded.
    """
    name = models.CharField(max_length=100)
    json_spec = models.TextField()

    def __unicode__(self):
        return self.name
    
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
        return '%s %s ui: %s tgt_lang: %s done: %s training: %s order: %d' % (self.user.username,
                                                                    self.src_document.url,
                                                                    self.interface,
                                                                    self.tgt_language.code,
                                                                    str(self.complete),
                                                                    str(self.training),
                                                                              self.order)
    
class DemographicData(models.Model):
    """
    Demographic data about a user. Entered when the user consents to participate
    in the experiment.
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
	Exit survey entered by the user after the experiment ends.
	"""
	user = models.OneToOneField(User)

        # Sanity check questions
        exit_data_comparison = models.BooleanField(choices=choices.BOOLEAN_CHOICES, default = None)
        exit_fatigue_comparison = models.BooleanField(choices=choices.BOOLEAN_CHOICES, default = None)
        exit_technical_comparison = models.TextField()

        # Linguistic difficulty questions
        exit_hardest_pos = models.CharField( max_length = 3, choices = choices.POS_CHOICES, default = None )
	exit_easiest_pos = models.CharField( max_length = 3, choices = choices.POS_CHOICES, default = None )
	exit_hardest_source = models.TextField()
	exit_hardest_target = models.TextField()

        # UI Comparison questions
        exit_focus_in_postedit = models.CharField( max_length = 5, choices = choices.POSTEDIT_GAZE, default = None )
	exit_focus_in_imt = models.CharField( max_length = 5, choices = choices.ITM_GAZE, default = None )
	exit_like_better = models.CharField( max_length = 3, choices = choices.INTERFACES, default = None )
	exit_more_efficient = models.CharField( max_length = 3, choices = choices.INTERFACES, default = None )

        # Interactive MT questions
	exit_itm_most_useful = models.CharField( max_length = 5, choices = choices.IMT_AID_CHOICES, default = None )
	exit_itm_least_useful = models.CharField( max_length = 5, choices = choices.IMT_AID_CHOICES, default = None )
	exit_useful_src_lookup = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_inlined = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_suggestions = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_completion = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_chunking = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_useful_tgt_anywhere = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )

        # Verdict on interactive MT questions
	exit_cat_strength_weakness = models.TextField()
	exit_itm_strength_weakness = models.TextField()
	exit_itm_missing_aid = models.TextField()
	exit_prefer_itm = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )
	exit_got_better_at_itm = models.PositiveSmallIntegerField( choices = choices.LIKERT_CHOICES, default = None )

        # Any other comments?
	exit_comments = models.TextField()
