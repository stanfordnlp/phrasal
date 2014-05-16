#
# Wrappers around common queries
#
import logging
from models import Language,DemographicData,ExitSurveyData,TrainingRecord,UserConfiguration,SourceDocument

logger = logging.getLogger(__name__)

def get_language(code_str):
    """
    """
    try:
        return Language.objects.get(code=code_str)
    except Language.MultipleObjectsReturned,Language.DoesNotExist:
        logger.error('Language code not found in database: ' + code_str)
        raise RuntimeError

def get_source_docs_for_lang(src_lang_code):
    """
    """
    src_language = get_language(src_lang_code)
    try:
        return SourceDocument.objects.filter(language=src_language)
    except:
        logger.error('No source documents for language: ' + src_lang_code)
        raise RuntimeError
    
def get_demographic_data(user):
    """

    Raises: RuntimeError
    """
    try:
        return DemographicData.objects.get(user=user)
    except DemographicData.MultipleObjectsReturned:
        raise RuntimeError
    except DemographicData.DoesNotExist:
        return None

def get_exit_data(user):
    """

    Raises: RuntimeError
    """
    try:
        return ExitSurveyData.objects.get(user=user)
    except ExitSurveyData.MultipleObjectsReturned:
        raise RuntimeError
    except ExitSurveyData.DoesNotExist:
        return None

def get_training_record(user):
    """

    Raises: RuntimeError
    """
    try:
        return TrainingRecord.objects.get(user=user)
    except TrainingRecord.MultipleObjectsReturned:
        raise RuntimeError
    except TrainingRecord.DoesNotExist:
        return None

def get_configuration(user):
    """
    Return the configuration for the user

    Raises: RuntimeError
    """
    try:
        return UserConfiguration.objects.get(user=user)
    except UserConfiguration.MultipleObjectsReturned,UserConfiguration.DoesNotExist:
        logger.error('User not configured properly: ' + str(user))
        raise RuntimeError
