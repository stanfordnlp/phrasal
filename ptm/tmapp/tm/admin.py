from tm.models import SourceTxt,TargetTxt,UserConf,TranslationStats,ExperimentModule,ExperimentSample,UISpec,SurveyResponse
from django.contrib import admin

# View these tables in the admin interface
admin.site.register(SourceTxt)
admin.site.register(TargetTxt)
admin.site.register(UserConf)
admin.site.register(TranslationStats)
admin.site.register(ExperimentModule)
admin.site.register(ExperimentSample)
admin.site.register(UISpec)
admin.site.register(SurveyResponse)
