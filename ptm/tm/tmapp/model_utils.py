#
# Wrappers around common queries
#

from models import Language,DemographicData,ExitSurveyData,TrainingRecord

def get_language(code_str):
    """
    """
    try:
        language_object = Language.objects.get(code=code_str)
        return language_object
    except Language.MultipleObjectsReturned,Language.DoesNotExist:
        # Log error
        raise RuntimeError

def get_demographic_data(user):
    """
    """
    try:
        dd_object = DemographicData.objects.get(user=user)
        return dd_object
    except DemographicData.MultipleObjectsReturned:
        raise RuntimeError
    except DemographicData.DoesNotExist:
        # User has not submitted demographic data
        return None

def get_exit_data(user):
    """
    """
    try:
        exit_object = ExitSurveyData.objects.get(user=user)
        return exit_object
    except ExitSurveyData.MultipleObjectsReturned:
        raise RuntimeError
    except ExitSurveyData.DoesNotExist:
        # User has not submitted demographic data
        return None

def get_training_record(user):
    """
    """
    try:
        training_object = TrainingRecord.objects.get(user=user)
        return training_object
    except TrainingRecord.MultipleObjectsReturned:
        raise RuntimeError
    except TrainingRecord.DoesNotExist:
        return None
