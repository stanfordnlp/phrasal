package edu.stanford.nlp.mt.lm;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.InsertedStartEndToken;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Factory for loading n-gram language models. Also includes a main method for scoring
 * sequences with a language model.
 *
 * @author danielcer
 * @author Spence Green
 *
 */
public class LanguageModels {

  // Supported language models
  public static final String KEN_LM_TAG = "kenlm:";

  public static final int MAX_NGRAM_ORDER = 10;

  private LanguageModels() {}

  public static <T> double scoreSequence(LanguageModel<T> lm, Sequence<T> s2) {
    double logP = 0;
    Sequence<T> s = new InsertedStartEndToken<T>(s2, lm.getStartToken(),
        lm.getEndToken());
    int sz = s.size();
    for (int i = 1; i < sz; i++) {
      Sequence<T> ngram = s.subsequence(0, i + 1);
      double ngramScore = lm.score(ngram).getScore();
      if (ngramScore == Double.NEGATIVE_INFINITY) {
        // like sri lm's n-gram utility w.r.t. closed vocab models,
        // right now we silently ignore unknown words.
        continue;
      }
      logP += ngramScore;
    }
    return logP;
  }

  public static LanguageModel<IString> load(String filename) throws IOException {
    return load(filename, null);
  }

  public static LanguageModel<IString> load(String filename,
      String vocabFilename) throws IOException {
    
    if (ARPALanguageModel.lmStore.containsKey(filename))
      return ARPALanguageModel.lmStore.get(filename);

    LanguageModel<IString> languageModel;
    if (filename.startsWith(KEN_LM_TAG)) {
      String realFilename = filename.substring(KEN_LM_TAG.length());
      languageModel = new KenLanguageModel(realFilename);
    
    } else {
      // Default Java LM data structure
      languageModel = new ARPALanguageModel(filename);
    }
    return languageModel;
  }

  /**
   * 
   * @param args
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
          .printf("Usage: java %s type:path [input_file] < input_file%n", LanguageModels.class.getName());
      System.exit(-1);
    }

    String model = args[0];
    System.out.printf("Loading lm: %s...%n", model);
    LanguageModel<IString> lm = load(model);

    LineNumberReader reader = (args.length == 1) ? 
        new LineNumberReader(new InputStreamReader(System.in)) :
          IOTools.getReaderFromFile(args[1]);
    
    double logSum = 0;
    final long startTimeMillis = System.nanoTime();
    for (String sent; (sent = reader.readLine()) != null;) {
      Sequence<IString> seq = IStrings.tokenize(sent);
      double score = scoreSequence(lm, seq);
      logSum += Math.log(score);
      
      System.out.println("Sentence: " + sent);
      System.out.printf("Sequence score: %f score_log10: %f%n", score, score
          / Math.log(10));
    }
    reader.close();
    System.out.printf("Log sum score: %e%n", logSum);
        
    double elapsed = (System.nanoTime() - startTimeMillis) / 1e9;
    System.err.printf("Elapsed time: %.3fs%n", elapsed);
  }
}
