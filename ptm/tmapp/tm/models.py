from django.db import models
from django.contrib.auth.models import User

# Helper functions
def truncate(txt):
    toks = txt.split()
    if len(toks) > 10:
        return ' '.join(toks[:10]) + ' ...'
    else:
        return txt

# Language metadata, mostly for rendering in the browser
class LanguageSpec(models.Model):
    code = models.CharField(max_length=2)
    name = models.CharField(max_length=30)
    css_direction = models.CharField(max_length=3)

    def __unicode__(self):
        return '%s: %s %s' % (self.name,self.code,self.css_direction)

# segid -- key into a source corpus (for research purposes only), not necessarily unique
class SourceTxt(models.Model):
    lang = models.ForeignKey(LanguageSpec)
    txt = models.CharField(max_length=5000)
    segid = models.CharField(max_length=100)

    def __unicode__(self):
        return '%s: %s' % (self.segid,truncate(self.txt))

# A single translation submitted by a user
class TargetTxt(models.Model):
    src = models.ForeignKey(SourceTxt)
    txt = models.CharField(max_length=5000)
    lang = models.ForeignKey(LanguageSpec)
    user = models.ForeignKey(User)
    date = models.DateTimeField()

    def __unicode__(self):
        return '%s (%s): %s' % (self.user.username,self.date,truncate(self.txt))

# Metadata about a translation submitted by a user
# We should use this metadata to quantify translator productivity
# improvements
class TranslationStats(models.Model):
    tgt = models.ForeignKey(TargetTxt)
    elapsedtime = models.IntegerField()
    ptm_enabled = models.BooleanField()
    # TODO: Not sure if this will be keystrokes or mouse actions or what
    n_actions = models.IntegerField()

    def __unicode__(self):
        return '%s:%s' % (self.tgt,self.elapsedtime)

# Other metadata should be associated with each target translation, such as:
class TranslationRule(models.Model):
    instances = models.ManyToManyField(TargetTxt)
    src_txt = models.CharField(max_length=1000)
    tgt_txt = models.CharField(max_length=1000)
    s_to_t = models.CharField(max_length=200)
    n_selected = models.IntegerField()
    n_pruned = models.IntegerField()
    is_automatic = models.BooleanField()

    def __unicode__(self):
        return '%s ||| %s ||| (%s)' % (self.src_txt,self.tgt_txt,self.s_to_t)
    
