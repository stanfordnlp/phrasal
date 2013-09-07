package edu.stanford.nlp.mt.base;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

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

  public static IntegerArrayIndex sourceIndex;
  public static IntegerArrayIndex ruleIndex;

  public final String[] scoreNames;
  protected String name;
  public final List<List<IntArrayTranslationOption>> translations;
  
  private int longestSourcePhrase = -1;
  private int longestTargetPhrase = -1;

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

  public static class IntArrayTranslationOption implements
      Comparable<IntArrayTranslationOption> {
    public final int[] translation;
    public final float[] scores;
    public final PhraseAlignment alignment;
    public final int id;

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

  /**
   * Convert rule scores from string to a numeric array.
   *
   * @param sList
   * @return
   */
  private static float[] stringProbListToFloatProbArray(List<String> sList) throws NumberFormatException {
    float[] fArray = new float[sList.size()];
    int i = 0;
    for (String s : sList) {
      float f = Float.parseFloat(s);
      if (Float.isNaN(f)) {
        throw new NumberFormatException("Unparseable number: " + s);
      }
      fArray[i++] = f;
    }
    return fArray;
  }

  protected void addEntry(Sequence<IString> foreignSequence,
      Sequence<IString> translationSequence, PhraseAlignment alignment,
      float[] scores) {
    int[] foreignInts = Sequences.toIntArray(foreignSequence);
    int[] translationInts = Sequences.toIntArray(translationSequence);
    int fIndex = sourceIndex.indexOf(foreignInts, true);
    int eIndex = ruleIndex.indexOf(translationInts, true);
    int id = ruleIndex.indexOf(new int[] { fIndex, eIndex }, true);
    /*
     * System.err.printf("foreign ints: %s translation ints: %s\n",
     * Arrays.toString(foreignInts), Arrays.toString(translationInts));
     * System.err.printf("fIndex: %d\n", fIndex);
     */
    if (translations.size() <= fIndex) {
      while (translations.size() <= fIndex)
        translations.add(null);
    }
    List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
    if (intTransOpts == null) {
      intTransOpts = Generics.newLinkedList();
      translations.set(fIndex, intTransOpts);
    }
    intTransOpts.add(new IntArrayTranslationOption(id, ruleIndex
        .get(eIndex), scores, alignment));
  }


  public FlatPhraseTable(
      RuleFeaturizer<IString, FV> phraseFeaturizer,
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
      RuleFeaturizer<IString, FV> phraseFeaturizer,
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

  /**
   * Load the phrase table from file. 
   * 
   * @param f
   * @param reverse
   * @return
   * @throws IOException
   */
  private int init(File f, boolean reverse) throws IOException {
    Runtime rt = Runtime.getRuntime();
    long prePhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();

    LineNumberReader reader = IOTools.getReaderFromFile(f);
    int numScores = -1;
    for (String line; (line = reader.readLine()) != null;) {
      List<List<String>> fields = StringUtils.splitFieldsFast(line, FlatNBestList.FIELD_DELIM);
      
      // The standard format has five fields
      assert fields.size() == 5 : String.format("n-best list line %d has %d fields", 
          reader.getLineNumber(), fields.size());
      Sequence<IString> source = IStrings.toIStringSequence(fields.get(0));
      Sequence<IString> target = IStrings.toIStringSequence(fields.get(1));
//      String sourceConstellation = fields[2];
      String targetConstellation = StringUtils.join(fields.get(3));
      List<String> scoreList = fields.get(4);
      
      if (reverse) {
        Sequence<IString> tmp = source;
        source = target;
        target = tmp;
      }

      // Ensure that all rules in the phrase table have the same number of scores
      if (numScores < 0) {
        numScores = scoreList.size();
      } else if (numScores != scoreList.size()) {
        throw new RuntimeException(
            String
                .format(
                    "Error (line %d): Each entry must have exactly the same number of translation\n"
                        + "scores per line. Prior entries had %d, while the current entry has %d:",
                    reader.getLineNumber(), numScores, scoreList.size()));
      }
      float[] scores;
      try {
        scores = stringProbListToFloatProbArray(scoreList);
      } catch (NumberFormatException e) {
        e.printStackTrace();
        throw new RuntimeException(String.format("Number format error on line %d",
            reader.getLineNumber()));
      }

      if (targetConstellation.equals("")) {
        addEntry(source, target, null, scores);
      } else {
        addEntry(source, target,
            PhraseAlignment.getPhraseAlignment(targetConstellation), scores);
      }

      if (source.size() > longestSourcePhrase) {
        longestSourcePhrase = source.size();
      }
      if (target.size() > longestTargetPhrase) {
        longestTargetPhrase = target.size();
      }
    }
    reader.close();

    // print some status information
    long postPhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    System.err
        .printf(
            "Done loading phrase table: %s (mem used: %d MiB time: %.3f s)%n",
            f.getAbsolutePath(),
            (postPhraseTableLoadMemUsed - prePhraseTableLoadMemUsed)
                / (1024 * 1024), elapsedTime);
    System.err.println("Longest foreign phrase: " + longestSourcePhrase);
    System.err.printf("Phrase table signature: %d%n", getSignature());
    return numScores;
  }

  @Override
  public int longestSourcePhrase() {
    return longestSourcePhrase;
  }
  
  @Override
  public int longestTargetPhrase() {
    return longestTargetPhrase;
  }

  @Override
  public List<Rule<IString>> query(
      Sequence<IString> foreignSequence) {
    RawSequence<IString> rawForeign = new RawSequence<IString>(foreignSequence);
    int[] foreignInts = Sequences.toIntArray(foreignSequence,
        IString.identityIndex());
    int fIndex = sourceIndex.indexOf(foreignInts);
    if (fIndex == -1)
      return null;
    List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
    List<Rule<IString>> transOpts = new ArrayList<Rule<IString>>(
        intTransOpts.size());
    // int intTransOptsSize = intTransOpts.size();
    // for (int i = 0; i < intTransOptsSize; i++) {
    for (IntArrayTranslationOption intTransOpt : intTransOpts) {
      // IntArrayTranslationOption intTransOpt = intTransOpts.get(i);
      // System.out.printf("%d:%f\n", i, intTransOpt.scores[0]);
      RawSequence<IString> translation = new RawSequence<IString>(
          intTransOpt.translation, IString.identityIndex());
      transOpts.add(new Rule<IString>(intTransOpt.id,
          intTransOpt.scores, scoreNames, translation, rawForeign,
          intTransOpt.alignment));
    }
    return transOpts;
  }

  public static void main(String[] args) throws Exception {
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
    long totalMemory = Runtime.getRuntime().totalMemory() / (1L << 20);
    long freeMemory = Runtime.getRuntime().freeMemory() / (1L << 20);
    double totalSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    System.err.printf(
        "size = %d, secs = %.3f, totalmem = %dm, freemem = %dm\n",
        sourceIndex.size(), totalSecs, totalMemory, freeMemory);

    List<Rule<IString>> translationOptions = ppt
        .query(new SimpleSequence<IString>(IStrings
            .toIStringArray(phrase.split("\\s+"))));

    System.out.printf("Phrase: %s\n", phrase);

    if (translationOptions == null) {
      System.out.printf("No translation options found.");
      System.exit(-1);
    }

    System.out.printf("Options:\n");
    for (Rule<IString> opt : translationOptions) {
      System.out.printf("\t%s : %s\n", opt.target,
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

  /**
   * Sort of like hashCode(), but for debugging purposes
   * only.
   * 
   * @return
   */
  public long getSignature() {
    DynamicIntegerArrayIndex index = (DynamicIntegerArrayIndex) ruleIndex;
    long signature = 0;
    for (int[] rule : index) {
      signature += Arrays.hashCode(rule);
    }
    return signature;
  }
  
  public static void createIndex(boolean withGaps) {
    sourceIndex = (withGaps || TRIE_INDEX) ? new TrieIntegerArrayIndex()
        : new DynamicIntegerArrayIndex();
    ruleIndex = new DynamicIntegerArrayIndex();
  }

  public static void lockIndex() {
    sourceIndex.lock();
  }
}
