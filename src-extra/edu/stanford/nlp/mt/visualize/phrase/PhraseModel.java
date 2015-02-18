package edu.stanford.nlp.mt.visualize.phrase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author Spence Green
 */
public class PhraseModel {

  // For validating the input file format
  private static final int OPT_TOKS_PER_LINE = 5;

  private int NUM_VISUAL_OPTION_ROWS = 10;
  private boolean NORM_SCORES = false;
  private boolean VERBOSE = false;

  private final File sourceFile;
  private final File optsFile;
  private final Map<Integer, Translation> translations;
  private final Map<Integer, TranslationLayout> layouts;
  private int minTranslationId = Integer.MIN_VALUE;
  private ScoreDistribution scoreDist;

  private boolean isBuilt = false;

  public PhraseModel(File source, File opts) {
    sourceFile = source;
    optsFile = opts;

    translations = new HashMap<Integer, Translation>();
    layouts = new HashMap<Integer, TranslationLayout>();

    if (!sourceFile.exists())
      throw new RuntimeException(String.format("%s: %s does not exist", this
          .getClass().getName(), sourceFile.getPath()));
    if (!optsFile.exists())
      throw new RuntimeException(String.format("%s: %s does not exist", this
          .getClass().getName(), sourceFile.getPath()));
  }

  public void setVerbose(boolean verbose) {
    VERBOSE = verbose;
  }

  public boolean load(int firstId, int lastId, int scoreHalfRange) {
    minTranslationId = firstId;
    try {

      scoreDist = new ScoreDistribution(scoreHalfRange);

      LineNumberReader sourceReader = new LineNumberReader(new FileReader(
          sourceFile));
      LineNumberReader optsReader = new LineNumberReader(new FileReader(
          optsFile));

      String[] lastOpt = null;
      int transId;
      for (transId = 0; sourceReader.ready(); transId++) {
        String source = sourceReader.readLine();

        if (transId < firstId)
          continue;
        else if (transId > lastId)
          break;

        if (minTranslationId < 0)
          minTranslationId = transId;

        Translation translation = new Translation(transId, source);

        if (lastOpt != null && Integer.parseInt(lastOpt[0]) == transId) {
          String english = lastOpt[2].intern();

          double score = Double.parseDouble(lastOpt[3]);
          if (NORM_SCORES)
            score /= (double) english.split("\\s+").length;
          score = Math.exp(score);

          scoreDist.add(score);
          translation.addPhrase(Math.exp(score), english, lastOpt[4]);
        }
        lastOpt = null;

        for (int optsRead = 0; optsReader.ready(); optsRead++) {
          String transOpt = optsReader.readLine();
          String[] optToks = transOpt.split("\\s*\\|\\|\\|\\s*");
          assert optToks.length == OPT_TOKS_PER_LINE;

          final int transIdForOpt = Integer.parseInt(optToks[0]);
          if (transIdForOpt < transId)
            continue;
          else if (transIdForOpt != transId) {
            lastOpt = optToks;
            break;
          }

          String english = optToks[2].intern();
          String coverage = optToks[4];

          double score = Double.parseDouble(optToks[3]);
          if (NORM_SCORES)
            score /= (double) english.split("\\s+").length;
          score = Math.exp(score);

          scoreDist.add(score);
          translation.addPhrase(score, english, coverage);
        }
        translations.put(transId, translation);
      }

      if (VERBOSE)
        System.err.printf("%s: Read %d source sentences from %s\n", this
            .getClass().getName(), transId, sourceFile.getPath());

      sourceReader.close();
      optsReader.close();

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open file\n%s\n", this.getClass()
          .getName(), e.toString());
      return false;
    } catch (IOException e) {
      System.err.printf("%s: Error while reading file\n%s\n", this.getClass()
          .getName(), e.toString());
      return false;
    }

    return true;
  }

  public boolean buildLayouts(boolean rightToLeft) {
    for (Integer translationId : translations.keySet()) {
      Translation t = translations.get(translationId);
      TranslationLayout layout = new TranslationLayout(t, rightToLeft);
      layout.createLayout(NUM_VISUAL_OPTION_ROWS);
      layouts.put(translationId, layout);
    }

    scoreDist.computeDistribution();
    isBuilt = true;

    return true;
  }

  public int getNumTranslations() {
    return (layouts != null) ? layouts.keySet().size() : 0;
  }

  public boolean isBuilt() {
    return isBuilt;
  }

  public TranslationLayout getTranslationLayout(int translationId) {
    return (layouts != null) ? layouts.get(translationId) : null;
  }

  public String getTranslationSource(int translationId) {
    if (translations != null && translations.get(translationId) != null)
      return translations.get(translationId).getSource();
    return null;
  }

  public int getMinTranslationId() {
    return minTranslationId;
  }

  public int getScoreRank(double score) {
    return scoreDist.getStdDev(score);
  }

  public void normalizePhraseScores(boolean newState) {
    NORM_SCORES = newState;
  }

  public void setNumberOfOptionRows(int rows) {
    NUM_VISUAL_OPTION_ROWS = rows;
  }

}
