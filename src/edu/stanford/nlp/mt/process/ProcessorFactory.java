package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.process.de.GermanPostprocessor;
import edu.stanford.nlp.mt.process.de.GermanPreprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPostprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPreprocessor;
import edu.stanford.nlp.mt.process.fr.FrenchPostprocessor;
import edu.stanford.nlp.mt.process.fr.FrenchPreprocessor;

/**
 * Factory for loading pre/post processors for supported languages.
 * 
 * TODO(spenceg): Integrate Language codes with core and 
 * services.Messages.
 * 
 * @author Spence Green
 *
 */
public final class ProcessorFactory {

  // Supported languages in iso-639-1 format
  // TODO(spenceg) Make this more robust, and perhaps reconcile
  // with the JavaNLP core Languages package.
  public static enum Language {UNK,AR,EN,DE,FR};
  
  private ProcessorFactory() {}
  
  /**
   * Load a preprocessor for a specific language with options.
   * 
   * @param language
   * @param options
   * @return
   */
  public static Preprocessor getPreprocessor(String language, String...options) throws IllegalArgumentException {
    language = language.toLowerCase();
    
    if (language.equals("en") || language.equals("english")) {
      return new EnglishPreprocessor();
    
    } else if (language.equals("de") || language.equals("german")) {
      return new GermanPreprocessor();
      
    } else if (language.equals("fr") || language.equals("french")) {
      return new FrenchPreprocessor();
    }
    
    throw new IllegalArgumentException("Invalid preprocessor language code: " + language);
  }

  /**
   * Load a postprocessor for a specific language with options.
   * 
   * @param language
   * @param options
   * @return
   */
  public static Postprocessor getPostprocessor(String language, String...options) {
    language = language.toLowerCase();
    
    if (language.equals("en") || language.equals("english")) {
      return new EnglishPostprocessor(options);
    
    } else if (language.equals("de") || language.equals("german")) {
      return new GermanPostprocessor(options);
      
    } else if (language.equals("fr") || language.equals("french")) {
      return new FrenchPostprocessor(options);
    }
    
    throw new IllegalArgumentException("Invalid postprocessor language code: " + language);
  }
}
