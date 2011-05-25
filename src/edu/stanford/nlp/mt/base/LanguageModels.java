package edu.stanford.nlp.mt.base;

import java.io.File;
import java.io.IOException;

/**
 * 
 * @author danielcer
 * 
 */
public class LanguageModels {
  public static <T> double scoreSequence(LanguageModel<T> lm, Sequence<T> s2) {
    double logP = 0;
    Sequence<T> s = new InsertedStartEndToken<T>(s2, lm.getStartToken(),
        lm.getEndToken());
    int sz = s.size();
    for (int i = 1; i < sz; i++) {

      Sequence<T> ngram = s.subsequence(0, i + 1);

      double ngramScore = lm.score(ngram);

      if (ngramScore == Double.NEGATIVE_INFINITY) {
        // like sri lm's n-gram utility w.r.t. closed vocab models,
        // right now we silently ignore unknown words.
        continue;
      }
      logP += ngramScore;
    }
    return logP;
  }

  public static final String BERKELEY_LM_TAG = "berkeleylm:";
  
  public static int MAX_NGRAM_ORDER = 10;
  
  public static LanguageModel<IString> load(String filename) throws IOException {
    return load(filename, null);
  }
  
  public static LanguageModel<IString> load(String filename,
      String vocabFilename) throws IOException {
    File f = new File(filename);
    String filepath = f.getAbsolutePath();
    if (ARPALanguageModel.lmStore.containsKey(filepath))
      return ARPALanguageModel.lmStore.get(filepath);
  
    boolean useSRILM = true;
    LanguageModel<IString> alm;
    if (filename.startsWith(BERKELEY_LM_TAG)) {
     String realFilename = filename.substring(BERKELEY_LM_TAG.length());
     alm = new BerkeleyLM(realFilename, MAX_NGRAM_ORDER);
    } else if (vocabFilename == null) {
      return new ARPALanguageModel(filename);
    } else {
      try {
        alm = new SRILanguageModel(filename, vocabFilename);
      } catch (UnsatisfiedLinkError e) {
        // e.printStackTrace();
        System.err
            .println("Unable to load SRILM library. Default to Java ARPA implementation.");
        alm = new ARPALanguageModel(filename);
        useSRILM = false;
      } catch (NoClassDefFoundError e) {
        // e.printStackTrace();
        System.err
            .println("Unable to load SRILM library. Default to Java ARPA implementation.");
        alm = new ARPALanguageModel(filename);
        useSRILM = false;
      }
    
      if (vocabFilename != null && !useSRILM)
        System.err.printf("Warning: vocabulary file %s is ignored.\n",
            vocabFilename);
    
      if (alm instanceof ARPALanguageModel)
        ARPALanguageModel.lmStore.put(filepath, (ARPALanguageModel) alm);
    } 
    return alm;
  }
}
