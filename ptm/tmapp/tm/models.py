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

    def __unicode__(self):
        return '%s: %s' % (self.code, self.name)
    
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

    def __unicode__(self):
        return '%s: %s %s' % (self.name, self.code, self.css_direction)

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
    
class SourceTxt(models.Model):
    """ The source input to be translated.

    doc -- document identifier
    seg -- segment identifier

    Args:
    Returns:
    Raises:
    """
    lang = models.ForeignKey(LanguageSpec)
    ui = models.ForeignKey(UISpec)

    txt = models.TextField()
    seg = models.CharField(max_length=100)
    doc = models.CharField(max_length=200)
    
    def __unicode__(self):
        return '%s-%s: %s' % (self.doc,self.seg,truncate(self.txt))

class UserConf(models.Model):
    """ Extended user properties for this app.

    Args:
    Returns:
    Raises:
    """
    # Each user has only one configuration
    user = models.ForeignKey(User)

    # UIs that this user can see
    uis_enabled = models.ManyToManyField(UISpec,related_name='+')

    # Currently active UI
    active_module = models.ForeignKey(UISpec,blank=True,null=True)

    # Native language: assumes a person has only one native language
    lang_native = models.ForeignKey(LanguageSpec,related_name='+')

    # TODO(spenceg): Assume all users are bilingual. This isn't true
    # but simplifies the work queue logic for now
    lang_other = models.ForeignKey(LanguageSpec,related_name='+')

    # Research: Users are *only* permitted to see sentences once
    srcs = models.ManyToManyField(SourceTxt,blank=True,related_name='+')

    # Has the user passed the training module?
    has_trained = models.BooleanField(default=False)

    # User has completed all translation tasks
    done_with_tasks = models.BooleanField(default=False)
    
    # Is the user a machine?
    is_machine = models.BooleanField(default=False)

    # Demographic information entered by user
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
    #hours_per_week = models.IntegerField()
    
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
    src = models.ForeignKey(SourceTxt)
    lang = models.ForeignKey(LanguageSpec,related_name='+')
    user = models.ForeignKey(User,related_name='+')

    txt = models.TextField()
    date = models.DateTimeField()

    def __unicode__(self):
        return '%s (%s): %s' % (self.user.username,
                                self.date,
                                truncate(self.txt))

# Metadata about a translation submitted by a user
# We should use this metadata to quantify translator productivity
# improvements
class TranslationStats(models.Model):
    """ Statistics about a user translation that resulted in a new
    translation for a source text.

    Args:
    Returns:
    Raises:
    """
    tgt = models.ForeignKey(TargetTxt)
    ui = models.ForeignKey(UISpec,related_name='+')
    user = models.ForeignKey(User,related_name='+')

    action_log = models.TextField()

    # Did the user finish this translation?
    complete = models.BooleanField(default=True)
    
    def __unicode__(self):
        return '%s (%d): ui:%s complete:%s' % (self.user.username,
                                               self.tgt.id,
                                               self.ui.name,
                                               str(self.complete))

#class TranslationRule(models.Model):
#    """ A new translation rule. This is entered by the MT system / JDBC
#
#    Args:
#    Returns:
#    Raises:
#    """
#    instances = models.ManyToManyField(TargetTxt)
#    src_txt = models.CharField(max_length=1000)
#    tgt_txt = models.CharField(max_length=1000)
#    s_to_t = models.CharField(max_length=200)
#    n_selected = models.IntegerField()
#    n_pruned = models.IntegerField()
#    is_automatic = models.BooleanField()

#    def __unicode__(self):
#        return '%s ||| %s ||| (%s)' % (self.src_txt,self.tgt_txt,self.s_to_t)
    
