from django.db import models
from django.contrib.auth.models import User

# Helper functions
def truncate(txt):
    toks = txt.split()
    if len(toks) > 10:
        return ' '.join(toks[:10]) + ' ...'
    else:
        return txt

class LanguageSpec(models.Model):
    """ Specification of a (human) language. Mostly contains
    details for rendering the language in the browser.

    Args:
    Returns:
    Raises:
    """
    code = models.CharField(max_length=2)
    name = models.CharField(max_length=30)
    css_direction = models.CharField(max_length=3)

    def __unicode__(self):
        return '%s: %s %s' % (self.name,self.code,self.css_direction)

class UserConf(models.Model):
    """ Counts user utilization of the three interfaces

    uis_enabled is a comma-separated list of UISpec
    names that this user can use.

    total_time is the total time (in seconds) spent translating
    on the site. Note that only completed translations are counted.
    
    Args:
    Returns:
    Raises:
    """
    user = models.ForeignKey(User)
    # Comma-separated list of ui names (see UISpec.name)
    uis_enabled = models.CharField(max_length=500,blank=True)
    total_time = models.IntegerField()
    is_machine = models.BooleanField()
    # Assumes user has only one native language
    lang_native = models.ForeignKey(LanguageSpec)
    # Comma-separated list of ISO-659-1 language codes
    lang_other = models.CharField(max_length=500)
    
    def __unicode__(self):
        return '%s: time:%ds uis:%s' % (self.user.username, self.total_time,self.uis_enabled)
                        
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
    txt = models.TextField()
    seg = models.CharField(max_length=100)
    doc = models.CharField(max_length=200)
    
    def __unicode__(self):
        return '%s-%s: %s' % (self.doc,self.seg,truncate(self.txt))

class TargetTxt(models.Model):
    """A unique translation for a given source input.
    Note that the MT system (Google translate) is entered as a user.

    Args:
    Returns:
    Raises:
    """
    src = models.ForeignKey(SourceTxt)
    txt = models.TextField()
    lang = models.ForeignKey(LanguageSpec)
    user = models.ForeignKey(User)
    date = models.DateTimeField()

    def __unicode__(self):
        return '%s (%s): %s' % (self.user.username,self.date,truncate(self.txt))

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
    ui = models.ForeignKey(UISpec)
    user = models.ForeignKey(User)
    action_log = models.TextField()
    
    def __unicode__(self):
        return '%s (%d): ui:%s ' % (self.user.username,self.tgt.id,self.ui.name)

class TranslationRule(models.Model):
    """ A new translation rule. This is entered by the MT system / JDBC

    Args:
    Returns:
    Raises:
    """
    instances = models.ManyToManyField(TargetTxt)
    src_txt = models.CharField(max_length=1000)
    tgt_txt = models.CharField(max_length=1000)
    s_to_t = models.CharField(max_length=200)
    n_selected = models.IntegerField()
    n_pruned = models.IntegerField()
    is_automatic = models.BooleanField()

    def __unicode__(self):
        return '%s ||| %s ||| (%s)' % (self.src_txt,self.tgt_txt,self.s_to_t)
    
