package edu.stanford.nlp.mt.base;

import java.io.IOException;

import edu.stanford.nlp.objectbank.ObjectBank;

/**
 *
 * @author danielcer
 *
 */
public class LanguageModels {

  // Supported language models
  public static final String KEN_LM_TAG = "kenlm:";
  public static final String SRI_LM_TAG = "srilm:";

  public static final int MAX_NGRAM_ORDER = 10;

  private LanguageModels() {}

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

  public static LanguageModel<IString> load(String filename) throws IOException {
    return load(filename, null);
  }

  public static LanguageModel<IString> load(String filename,
      String vocabFilename) throws IOException {
    if (ARPALanguageModel.lmStore.containsKey(filename))
      return ARPALanguageModel.lmStore.get(filename);

    LanguageModel<IString> alm;

    if (filename.endsWith("bloomlm")) {
      return FrequencyMultiScoreLanguageModel.load(filename);
    } else if (filename.endsWith(".disklm")) {
      return new DiskLM(filename);
    } else if (filename.startsWith(KEN_LM_TAG)) {
      String realFilename = filename.substring(KEN_LM_TAG.length());
      alm = new KenLanguageModel(realFilename);
    } else if (filename.startsWith(SRI_LM_TAG)) {
      String realFilename = filename.substring(SRI_LM_TAG.length());
      boolean useSRILM = true;
      try {
        alm = new SRILanguageModel(realFilename, vocabFilename);
      } catch (UnsatisfiedLinkError e) {
        // e.printStackTrace();
        System.err
            .println("Unable to load SRILM library. Default to Java ARPA implementation.");
        alm = new ARPALanguageModel(realFilename);
        useSRILM = false;
      } catch (NoClassDefFoundError e) {
        // e.printStackTrace();
        System.err
            .println("Unable to load SRILM library. Default to Java ARPA implementation.");
        alm = new ARPALanguageModel(realFilename);
        useSRILM = false;
      }

      if (vocabFilename != null && !useSRILM)
        System.err.printf("Warning: vocabulary file %s is ignored.\n",
            vocabFilename);

      if (alm instanceof ARPALanguageModel)
        ARPALanguageModel.lmStore.put(filename, (ARPALanguageModel) alm);
    } else {
       return new ARPALanguageModel(filename);
    }
    return alm;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err
          .printf("Usage:\n\tjava ...LanguageModels [berkeleylm:](arpa format model) \"sentence or file to score\"\n");
      System.exit(-1);
    }

    // verbose = true;
    String model = args[0];
    String fileOrSentence = args[1];
    System.out.printf("Loading lm: %s...\n", model);
    LanguageModel<IString> lm = load(model);

    long startTimeMillis = System.currentTimeMillis();
    try {
      double logSum = 0;
      for (String sent : ObjectBank.getLineIterator(fileOrSentence)) {
        sent = sent.toLowerCase();
        System.out.printf("Sentence: %s\n", sent);
        Sequence<IString> seq = new SimpleSequence<IString>(
            IStrings.toIStringArray(sent.split("\\s")));
        double score = scoreSequence(lm, seq);
        System.out.printf("Sequence score: %f score_log10: %f\n", score, score
            / Math.log(10));
        logSum += Math.log(score);
      }
      System.out.printf("Log sum score: %e\n", logSum);
    } catch (RuntimeException e) {
      if (!e.getMessage().contains("FileNotFoundException")) throw e;
      Sequence<IString> seq = new SimpleSequence<IString>(
          IStrings.toIStringArray(fileOrSentence.split("\\s")));
      double score = scoreSequence(lm, seq);
      System.out.printf("Sequence score: %f score_log10: %f\n", score, score
          / Math.log(10));
    }
    double totalSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    System.err.printf("secs = %.3f\n", totalSecs);
  }
}
