package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.process.de.GermanPostprocessor;
import edu.stanford.nlp.mt.process.de.GermanPreprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPostprocessor;
import edu.stanford.nlp.mt.process.en.EnglishPreprocessor;
import edu.stanford.nlp.mt.process.es.SpanishPostprocessor;
import edu.stanford.nlp.mt.process.es.SpanishPreprocessor;
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
  public static enum Language {UNK,AR,EN,DE,FR,ES};
  
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

    switch (language) {
      case "en":
      case "english":
        return new EnglishPreprocessor();

      case "de":
      case "german":
        return new GermanPreprocessor();

      case "fr":
      case "french":
        return new FrenchPreprocessor();
        
      case "es":
      case "spanish":
        return new SpanishPreprocessor();
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

    switch (language) {
      case "en":
      case "english":
        return new EnglishPostprocessor(options);

      case "de":
      case "german":
        return new GermanPostprocessor(options);

      case "fr":
      case "french":
        return new FrenchPostprocessor(options);
        
      case "es":
      case "spanish":
        return new SpanishPostprocessor(options);
    }
    
    throw new IllegalArgumentException("Invalid postprocessor language code: " + language);
  }
}
