package edu.stanford.nlp.mt.base;

import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;

/**
 * 
 * @author Daniel Cer
 */
public class FlatPhraseTable<FV> extends AbstractPhraseGenerator<IString, FV>
    implements PhraseTable<IString> {
  
  public static final String TRIE_INDEX_PROPERTY = "TriePhraseTable";
  public static final boolean TRIE_INDEX = Boolean.parseBoolean(System
      .getProperty(TRIE_INDEX_PROPERTY, "false"));

  public static final String DISABLED_SCORES_PROPERTY = "disableScores";
  public static final String DISABLED_SCORES = System
      .getProperty(DISABLED_SCORES_PROPERTY);

  public static IntegerArrayIndex foreignIndex;
  static IntegerArrayIndex translationIndex;

  static String[] customScores;

  final String[] scoreNames;
  String name;

  // Originally, PharaohPhraseTables were backed by a nice simple
  // HashMap from a foreign sequence to a list of translations.
  //
  // However, this resulted in a phrase table that only
  // occupies 113 MiB on disk requiring 1304 MiB of RAM, ugh.
  //
  // So...we now have the slightly more evil, but hopefully not
  // too evil, implementation below.
  //
  // To compare, for the phrase table huge/filtered-mt03/phrase-table.0-0:
  //
  // with hash maps:
  //
  // java 6 32-bit 862 MiB
  // java 6 64-bit 1304 MiB
  //
  // with new implementation:
  //
  // java 6 32-bit 160 MiB
  // java 6 64-bit 254 MiB
  // ///////////////////////////////////////////////////////////////

  int longestForeignPhrase;

  public static class IntArrayTranslationOption implements
      Comparable<IntArrayTranslationOption> {
    public final int[] translation;
    final float[] scores;
    final PhraseAlignment alignment;
    final int id;

    public IntArrayTranslationOption(int id, int[] translation, float[] scores,
        PhraseAlignment alignment) {
      this.id = id;
      this.translation = translation;
      this.scores = scores;
      this.alignment = alignment;
    }

    @Override
    public int compareTo(IntArrayTranslationOption o) {
      return (int) Math.signum(o.scores[0] - scores[0]);
    }
  }

  public final ArrayList<List<IntArrayTranslationOption>> translations;

  /**
   * Convert rule scores from string to a numeric array.
   * 
   * @param sList
   * @return
   */
  private static float[] stringProbListToFloatProbArray(List<String> sList) {
    float[] fArray = new float[sList.size()];
    int i = 0;
    for (String s : sList) {
      float f = Float.parseFloat(s);
      
      if (Float.isNaN(f)) {
        throw new RuntimeException(String.format(
            "Bad phrase table. %s parses as (float) %f", s, f));
      }
      float newF = f;
//      float newF =  (f <= 0 ? f : (float) Math.log(f));

      if (Float.isNaN(newF)) {
        throw new RuntimeException(String.format(
            "Bad phrase table. %s parses as (float) %f", s, newF));
      }
      fArray[i++] = newF;
    }
    return fArray;
  }

  protected void addEntry(Sequence<IString> foreignSequence,
      Sequence<IString> translationSequence, PhraseAlignment alignment,
      float[] scores) {
    int[] foreignInts = Sequences.toIntArray(foreignSequence);
    int[] translationInts = Sequences.toIntArray(translationSequence);
    int fIndex = foreignIndex.indexOf(foreignInts, true);
    int eIndex = translationIndex.indexOf(translationInts, true);
    int id = translationIndex.indexOf(new int[] { fIndex, eIndex }, true);
    /*
     * System.err.printf("foreign ints: %s translation ints: %s\n",
     * Arrays.toString(foreignInts), Arrays.toString(translationInts));
     * System.err.printf("fIndex: %d\n", fIndex);
     */
    if (translations.size() <= fIndex) {
      translations.ensureCapacity(fIndex + 1);
      while (translations.size() <= fIndex)
        translations.add(null);
    }
    List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
    if (intTransOpts == null) {
      intTransOpts = new LinkedList<IntArrayTranslationOption>();
      translations.set(fIndex, intTransOpts);
    }
    intTransOpts.add(new IntArrayTranslationOption(id, translationIndex
        .get(eIndex), scores, alignment));
  }


  public FlatPhraseTable(
      IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer,
      String filename) throws IOException {
    // default is not to do log rithm on the scores
    this(phraseFeaturizer, filename, false);
  }
  
  public FlatPhraseTable(String filename) throws IOException {
    this(null, filename, false);
  }
  
  public FlatPhraseTable(String filename, boolean reverse) throws IOException {
    this(null, filename, reverse);
  }

  /**
   * 
   * @throws IOException
   */
  public FlatPhraseTable(
      IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer,
      String filename, boolean reverse) throws IOException {
    super(phraseFeaturizer);
    File f = new File(filename);
    name = String.format("FlatPhraseTable(%s)", f.getName());
    // arrayIndex = trieIndex ? new TrieIntegerArrayIndex() : new
    // DynamicIntegerArrayIndex();
    translations = new ArrayList<List<IntArrayTranslationOption>>();
    int countScores = init(f, reverse);
    scoreNames = getScoreNames(countScores);
  }

  private static String[] getScoreNames(int countScores) {
    String[] scoreNames;
    
    scoreNames = new String[countScores];
    for (int i = 0; i < countScores; i++) {
        scoreNames[i] = String.format("FPT.%d", i);
    }

    return scoreNames;
  }

  private int init(File f, boolean reverse) throws IOException {
    System.gc();
    
    Runtime rt = Runtime.getRuntime();
    long prePhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();

    LineNumberReader reader;
    if (f.getAbsolutePath().endsWith(".gz")) {
      reader = new LineNumberReader(new InputStreamReader(new GZIPInputStream(
          new FileInputStream(f)), "UTF-8"));
    } else {
      reader = new LineNumberReader(new InputStreamReader(
          new FileInputStream(f), "UTF-8"));
    }
    int countScores = -1;
    for (String line; (line = reader.readLine()) != null;) {
      if (line.startsWith("Java HotSpot(TM) 64-Bit"))
        // Skip JVM debug messages sent to stdout instead of stderr
        continue;
      // System.err.println("line: "+line);
      StringTokenizer toker = new StringTokenizer(line);
      Collection<String> foreignTokenList = new LinkedList<String>();
      do {
        String token = toker.nextToken();
        if ("|||".equals(token)) {
          break;
        }
        foreignTokenList.add(token);
      } while (toker.hasMoreTokens());

      if (!toker.hasMoreTokens()) {
        throw new RuntimeException(String.format(
            "Additional fields expected (line %d)", reader.getLineNumber()));
      }

      Collection<String> translationTokenList = new LinkedList<String>();

      do {
        String token = toker.nextToken();
        if ("|||".equals(token)) {
          break;
        }
        translationTokenList.add(token);
      } while (toker.hasMoreTokens());

      if (reverse) {
         Collection<String> tmp = translationTokenList;
         translationTokenList = foreignTokenList;
         foreignTokenList = tmp;
      }
      
      if (!toker.hasMoreTokens()) {
        throw new RuntimeException(String.format(
            "Additional fields expected (line %d)", reader.getLineNumber()));
      }
      Collection<String> constilationList = new LinkedList<String>();
      List<String> scoreList = new LinkedList<String>();
      boolean first = true;
      do {
        String token = toker.nextToken();
        if (token.startsWith("|||")) {
          constilationList.addAll(scoreList);
          scoreList = new LinkedList<String>();
          first = false;
          continue;
        }
        if (!first)
          scoreList.add(token);
      } while (toker.hasMoreTokens());

      IString[] foreignTokens = IStrings.toIStringArray(foreignTokenList);
      IString[] translationTokens = IStrings
          .toIStringArray(translationTokenList);

      if (countScores == -1) {
        countScores = scoreList.size();
      } else if (countScores != scoreList.size()) {
        throw new RuntimeException(
            String
                .format(
                    "Error (line %d): Each entry must have exactly the same number of translation\n"
                        + "scores per line. Prior entries had %d, while the current entry has %d:",
                    reader.getLineNumber(), countScores, scoreList.size()));
      }
      Sequence<IString> foreign = new SimpleSequence<IString>(true,
          foreignTokens);
      Sequence<IString> translation = new SimpleSequence<IString>(true,
          translationTokens);
      float[] scores;
      try {
        scores = stringProbListToFloatProbArray(scoreList);
      } catch (NumberFormatException e) {
        throw new RuntimeException(String.format(
            "Error on line %d: '%s' not a list of numbers",
            reader.getLineNumber(), scoreList));
      }

      StringBuilder constilationB = new StringBuilder();
      {
        int idx = -1;
        for (String t : constilationList) {
          idx++;
          if (idx > 0)
            constilationB.append(";");
          constilationB.append(t);
        }
      }

      String constilationBStr = constilationB.toString();
      if (constilationBStr.equals("")) {
        addEntry(foreign, translation, null, scores);
      } else {
        addEntry(foreign, translation,
            PhraseAlignment.getPhraseAlignment(constilationBStr), scores);
      }

      if (foreign.size() > longestForeignPhrase) {
        longestForeignPhrase = foreign.size();
      }
      /*
       * if (reader.getLineNumber() % 10000 == 0) {
       * System.out.printf("linenumber: %d\n", reader.getLineNumber()); long
       * memUsed = rt.totalMemory() - rt.freeMemory();
       * System.out.printf("mem used: %d\n", memUsed/(1024*1024)); }
       */
      // System.out.printf("foreign: '%s' english: '%s' scores: %s\n",
      // foreign, translation, Arrays.toString(scores));
    }

    reader.close();

    System.gc();

    // print some status information
    long postPhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
    System.err
        .printf(
            "Done loading pharoah phrase table: %s (mem used: %d MiB time: %.3f s)\n",
            f.getAbsolutePath(),
            (postPhraseTableLoadMemUsed - prePhraseTableLoadMemUsed)
                / (1024 * 1024), loadTimeMillis / 1000.0);
    System.err.println("Longest foreign phrase: " + longestForeignPhrase);
    return countScores;
  }

  @Override
  public int longestForeignPhrase() {
    return longestForeignPhrase;
  }

  @Override
  public List<TranslationOption<IString>> getTranslationOptions(
      Sequence<IString> foreignSequence) {
    RawSequence<IString> rawForeign = new RawSequence<IString>(foreignSequence);
    int[] foreignInts = Sequences.toIntArray(foreignSequence,
        IString.identityIndex());
    int fIndex = foreignIndex.indexOf(foreignInts);
    if (fIndex == -1)
      return null;
    List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
    List<TranslationOption<IString>> transOpts = new ArrayList<TranslationOption<IString>>(
        intTransOpts.size());
    // int intTransOptsSize = intTransOpts.size();
    // for (int i = 0; i < intTransOptsSize; i++) {
    for (IntArrayTranslationOption intTransOpt : intTransOpts) {
      // IntArrayTranslationOption intTransOpt = intTransOpts.get(i);
      // System.out.printf("%d:%f\n", i, intTransOpt.scores[0]);
      RawSequence<IString> translation = new RawSequence<IString>(
          intTransOpt.translation, IString.identityIndex());
      transOpts.add(new TranslationOption<IString>(intTransOpt.id,
          intTransOpt.scores, scoreNames, translation, rawForeign,
          intTransOpt.alignment));
    }
    return transOpts;
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.out
          .println("Usage:\n\tjava ...FlatPhraseTable (phrasetable file) (entry to look up)");
      System.exit(-1);
    }

    String model = args[0];
    String phrase = args[1];
    long startTimeMillis = System.currentTimeMillis();
    System.out.printf("Loading phrase table: %s\n", model);
    FlatPhraseTable<String> ppt = new FlatPhraseTable<String>(null,
        model);
    long totalMemory = Runtime.getRuntime().totalMemory() / (1 << 20);
    long freeMemory = Runtime.getRuntime().freeMemory() / (1 << 20);
    double totalSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    System.err.printf(
        "size = %d, secs = %.3f, totalmem = %dm, freemem = %dm\n",
        foreignIndex.size(), totalSecs, totalMemory, freeMemory);

    List<TranslationOption<IString>> translationOptions = ppt
        .getTranslationOptions(new SimpleSequence<IString>(IStrings
            .toIStringArray(phrase.split("\\s+"))));

    System.out.printf("Phrase: %s\n", phrase);

    if (translationOptions == null) {
      System.out.printf("No translation options found.");
      System.exit(-1);
    }

    System.out.printf("Options:\n");
    for (TranslationOption<IString> opt : translationOptions) {
      System.out.printf("\t%s : %s\n", opt.translation,
          Arrays.toString(opt.scores));
    }
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public void setCurrentSequence(Sequence<IString> foreign,
      List<Sequence<IString>> tranList) {
    // no op
  }

  public static void createIndex(boolean withGaps) {
    foreignIndex = (withGaps || TRIE_INDEX) ? new TrieIntegerArrayIndex()
        : new DynamicIntegerArrayIndex();
    translationIndex = new DynamicIntegerArrayIndex();
  }

  public static void lockIndex() {
    foreignIndex.lock();
  }

}
