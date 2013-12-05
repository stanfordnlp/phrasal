from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from django.contrib.auth.forms import (UserChangeForm, AdminPasswordChangeForm)
from forms_admin import UserCreationForm2
from django.contrib.auth.models import User, Group

from models import Language,DemographicData,TranslationSession,SourceDocument

# Standard models (for debugging)
admin.site.register(Language)
admin.site.register(DemographicData)
admin.site.register(TranslationSession)
admin.site.register(SourceDocument)

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
