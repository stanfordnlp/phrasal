from django.db import models
from django.contrib.auth.models import User

def truncate(txt, max_len=10):
    """ Truncates a whitespace-tokenized string after max_len
        tokens.

    Args:
    Returns:
    Raises:
    """
    toks = txt.split()
    if len(toks) > max_len:
        return ' '.join(toks[:10]) + ' ...'
    else:
        return txt

class Country(models.Model):
    """ What it says.
    Args:
    Returns:
    Raises:
    """
    code = models.CharField(max_length=2)
    name = models.CharField(max_length=100)

    # Should just return the display name, since this object
    # will be used in forms
    def __unicode__(self):
        return self.name
    
class LanguageSpec(models.Model):
    """ Specification of a (human) language. Mostly contains
    details for rendering the language in the browser.

    Args:
    Returns:
    Raises:
    """
    # 2 character ISO-659-1 code
    code = models.CharField(max_length=2)

    # Display name
    name = models.CharField(max_length=30)

    # rtl or ltr
    css_direction = models.CharField(max_length=3)

    # This object will be used in forms, so just return the id
    def __unicode__(self):
        return str(self.id)

class UISpec(models.Model):
    """ A textname and integer id for each 

    ui1 := (meedan) Show 1-best MT and no other assistance
    ui2 := (trados) Show suggestions in context
    ui3 := (sjc) Our best interface.
    
    Args:
    Raises:
    Returns:
    """
    name = models.CharField(max_length=10)

    def __unicode__(self):
        return self.name

class ExperimentModule(models.Model):
    """ Encodes a treatment to be applied to an experimental unit. This
    table should contain all factor levels for an experiment.

    Args:
    Raises:
    Returns:
    """
    ui = models.ForeignKey(UISpec)

    name = models.CharField(max_length=100)
    
    # CSV list of documents in this module
    docs = models.TextField()

    # Freeform metadata about this experiment
    description = models.TextField()

    def __unicode__(self):
        return '%s: ui %s' % (self.name,self.ui.name)
    
class SourceTxt(models.Model):
    """ The source input to be translated.

    doc -- document identifier
    seg -- segment identifier

    Args:
    Returns:
    Raises:
    """
    lang = models.ForeignKey(LanguageSpec)

    # Source text
    txt = models.TextField()

    # Document name
    doc = models.CharField(max_length=300)

    # Segment id (sentence) in the document
    seg = models.IntegerField()
    
    def __unicode__(self):
        return '%s-%d: %s' % (self.doc,self.seg,truncate(self.txt))

class ExperimentSample(models.Model):
    """ Basically a through table linking Users with samples that
    are specified by ExperimentModule. This table needs to populated
    with samples for each user when they start an ExperimentModule.

    Args:
    Returns:
    Raises:
    """
    user = models.ForeignKey(User)
    src = models.ForeignKey(SourceTxt,related_name='+')
    order = models.IntegerField()
    module = models.ForeignKey(ExperimentModule)

class UserConf(models.Model):
    """ Contains details of an experimental subject.

    Args:
    Returns:
    Raises:
    """
    # Each user has only one configuration
    user = models.OneToOneField(User)

    # ExperimentModules that will be applied to this user /
    # subject. Each ExperimentModule defines a treatment
    active_modules = models.ManyToManyField(ExperimentModule,
                                            related_name='+',
                                            blank=True,
                                            null=True)

    # Native language: assumes a person has only one native language
    lang_native = models.ForeignKey(LanguageSpec,related_name='+')

    # TODO(spenceg): Assume all users are bilingual. This isn't true
    # but simplifies the work queue logic for now
    lang_other = models.ForeignKey(LanguageSpec,related_name='+')

    # Has the user passed the training module?
    has_trained = models.BooleanField(default=False)
    
    # Demographic information entered by user during
    # training
    birth_country = models.ForeignKey(Country,
                                      related_name='+',
                                      blank=True,
                                      null=True)
    home_country = models.ForeignKey(Country,
                                     related_name='+',
                                     blank=True,
                                     null=True)
    # Number of hours that this user spends on translation
    # work each week (user reported)
    hours_per_week = models.IntegerField(blank=True, null=True)
    
    def __unicode__(self):
        return '%s: native:%s trained:%s' % (self.user.username,
                                             self.lang_native.name,
                                             str(self.has_trained))
                        
class TargetTxt(models.Model):
    """A unique translation for a given source input.
    Note that the MT system (Google translate) is entered as a user.

    Args:
    Returns:
    Raises:
    """
    # Each SourceTxt object can have multiple translations
    src = models.ForeignKey(SourceTxt)

    
    lang = models.ForeignKey(LanguageSpec,related_name='+')

    # User who created this translation
    # May be empty if this is an automatic translation
    # Also, create the reverse relation so that we can get all
    # translations for a given user
    user = models.ForeignKey(User,
                             blank=True,
                             null=True)

    # True if this is a machine generated translation
    is_machine = models.BooleanField(default=False)

    # The actual text
    txt = models.TextField()

    # Date when the translation was generated
    date = models.DateTimeField()

    def __unicode__(self):
        return '%s: %s' % (str(self.date),
                           truncate(self.txt))

class TranslationStats(models.Model):
    """ Metadata associated with each translation submitted by
    a user. These fields usually are not created for machine-generated
    translations, i.e., the "is_machine" field in TargetTxt is True.

    Args:
    Returns:
    Raises:
    """
    # Each translation was generated in a single session
    tgt = models.OneToOneField(TargetTxt)
    
    ui = models.ForeignKey(UISpec,related_name='+')

    user = models.ForeignKey(User,related_name='+')

    # Action log created by the interface
    # Usually, we use the translog2.js widget
    # This field cannot be empty
    action_log = models.TextField()

    # Did this translation session result in a valid
    # translation according to the rules of the experiment?
    is_valid = models.BooleanField(default=True)
    
    def __unicode__(self):
        return '%s (%d): ui:%s valid:%s' % (self.user.username,
                                            self.tgt.id,
                                            self.ui.name,
                                            str(self.is_valid))
    
