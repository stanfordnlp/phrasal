package edu.stanford.nlp.mt.tm;

import java.io.IOException;

import edu.stanford.nlp.mt.tm.DynamicTranslationModel.FeatureTemplate;
import edu.stanford.nlp.mt.util.IString;

/**
 * Load a phrase table from a filename
 * 
 * @author Daniel Cer
 * @author Spence Green
 */
public class TranslationModelFactory {

  public static final String FEATURE_PREFIX_OPTION = "featpref";
  public static final String DYNAMIC_INDEX = "dyn-index";
  public static final String DYNAMIC_SAMPLE_SIZE = "dyn-sample";
  public static final String DYNAMIC_FEATURE_TEMPLATE = "dyn-feat";
  public static final String SEPARATOR = ":";
  
  public static final String DYNAMIC_TAG = "dyn:";
  public static final String DTU_TAG = "dtu:";
  
  /**
   * Factory method for translation model loading.
   * 
   * @param options
   * @return
   * @throws IOException
   */
  @SuppressWarnings("rawtypes")
  static public <FV> TranslationModel<IString,FV> factory(String filename, 
      String...options) throws IOException {
    
    // Parse options
    String featurePrefix = null;
    boolean setSystemIndex = true;
    int dynamicSampleSize = DynamicTranslationModel.DEFAULT_SAMPLE_SIZE;
    FeatureTemplate dynamicTemplate = FeatureTemplate.DENSE_EXT;
    for (String option : options) {
      String[] fields = option.split(SEPARATOR);
      String key = fields[0];
      String value = fields[1];
      if (key.equals(FEATURE_PREFIX_OPTION)) {
        featurePrefix = value;
      } else if (key.equals(DYNAMIC_INDEX)) {
        setSystemIndex = Boolean.valueOf(value);
      } else if (key.equals(DYNAMIC_SAMPLE_SIZE)) {
        dynamicSampleSize = Integer.valueOf(value);
      } else if (key.equals(DYNAMIC_FEATURE_TEMPLATE)) {
        dynamicTemplate = FeatureTemplate.valueOf(value);
      }
    }
    
    TranslationModel<IString,FV> translationModel;
    if (filename.startsWith(DTU_TAG)) {
      String file = filename.substring(DTU_TAG.length());
      translationModel = new DTUTable<FV>(file);
    
    } else if (filename.startsWith(DYNAMIC_TAG)) {
      String file = filename.substring(DYNAMIC_TAG.length());
      translationModel = DynamicTranslationModel.load(file, setSystemIndex, DynamicTranslationModel.DEFAULT_NAME);
      ((DynamicTranslationModel) translationModel).setSampleSize(dynamicSampleSize);
      ((DynamicTranslationModel) translationModel).createQueryCache(dynamicTemplate);
      
    } else {
      translationModel = featurePrefix == null ? new CompiledPhraseTable<FV>(filename) :
        new CompiledPhraseTable<FV>(featurePrefix, filename);
    }
    return translationModel;
  }
}
