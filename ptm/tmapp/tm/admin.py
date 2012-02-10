from tm.models import SourceTxt,LanguageSpec,TargetTxt,UserConf,UISpec,TranslationStats
from django.contrib import admin

# View these tables in the admin interface
admin.site.register(SourceTxt)
admin.site.register(LanguageSpec)
admin.site.register(TargetTxt)
admin.site.register(UserConf)
admin.site.register(UISpec)
admin.site.register(TranslationStats)
