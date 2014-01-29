from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from django.contrib.auth.forms import UserChangeForm, AdminPasswordChangeForm
from django.contrib.auth.models import User, Group

from forms_admin import UserCreationForm2
import models

# Standard models (for debugging)
admin.site.register(models.Language)
admin.site.register(models.DemographicData)
admin.site.register(models.TranslationSession)
admin.site.register(models.SourceDocument)
admin.site.register(models.TrainingRecord)
admin.site.register(models.UserConfiguration)
admin.site.register(models.ExitSurveyData)

#
# User admin information
#
class UserAdmin2(UserAdmin):
    add_form = UserCreationForm2
    add_fieldsets = (
        (None, {
            'classes': ('wide',),
            'fields': ('json_spec',)}
        ),
    )

admin.site.unregister(User)
admin.site.register(User, UserAdmin2)
