package edu.stanford.nlp.mt.lm;

import java.io.IOException;

import edu.stanford.nlp.mt.base.IString;

/**
 * Factory for loading n-gram language models. Also includes a main method for scoring
 * sequences with a language model.
 *
 * @author danielcer
 * @author Spence Green
 *
 */
public final class LanguageModelFactory {

  // Supported language models
  public static final String KEN_LM_TAG = "kenlm:";

  public static final int MAX_NGRAM_ORDER = 10;

  private LanguageModelFactory() {}

  public static LanguageModel<IString> load(String filename) throws IOException {
    return load(filename, 1);
  }

  public static LanguageModel<IString> load(String filename, int numThreads) throws IOException {
    LanguageModel<IString> languageModel;
    if (filename.startsWith(KEN_LM_TAG)) {
      String realFilename = filename.substring(KEN_LM_TAG.length());
      languageModel = new KenLanguageModel(realFilename, numThreads);
    
    } else {
      // Default Java LM data structure
      languageModel = new ARPALanguageModel(filename);
    }
    return languageModel;
  }


}
