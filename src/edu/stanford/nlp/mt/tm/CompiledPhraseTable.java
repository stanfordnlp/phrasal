package edu.stanford.nlp.mt.tm;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.IntegerArrayIndex;
import edu.stanford.nlp.mt.util.IntegerArrayRawIndex;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.ProbingIntegerArrayIndex;
import edu.stanford.nlp.mt.util.ProbingIntegerArrayRawIndex;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
import edu.stanford.nlp.util.StringUtils;

/**
 * A basic phrase table implementation. Does *not* support gappy rules.
 *
 * @author Daniel Cer
 * @author Spence Green
 *
 */
public class CompiledPhraseTable<FV> extends AbstractPhraseGenerator<IString, FV>
    implements PhraseTable<IString> {

  private static final int INITIAL_CAPACITY = 50000;

  public static final String FIELD_DELIM = "|||";
  public static final String DEFAULT_FEATURE_PREFIX = "FPT";

  // Static so that even when multiple phrase tables are loaded, each rule
  // is assured of received a unique, non-negative id.
  private static final AtomicInteger ruleIdCounter = new AtomicInteger();

  protected final IntegerArrayRawIndex sourceToRuleIndex;
  protected final IntegerArrayIndex targetIndex;
  protected final int minRuleIndex;
  protected final String[] scoreNames;
  protected final String name;
  protected final List<List<PhraseTableEntry>> ruleLists;

  protected int longestSourcePhrase = -1;
  protected int longestTargetPhrase = -1;

  /**
   * Constructor.
   *
   * @param filename
   * @throws IOException
   */
  public CompiledPhraseTable(String filename) throws IOException {
    this(DEFAULT_FEATURE_PREFIX, filename);
  }

  /**
   * Constructor.
   *
   * @param featurePrefix
   * @param filename
   * @throws IOException
   */
  public CompiledPhraseTable(
      String featurePrefix,
      String filename) throws IOException {
    super(null);
    File f = new File(filename);
    name = String.format("%s:%s", this.getClass().getName(), f.getPath()).intern();
    minRuleIndex = ruleIdCounter.get();
    ruleLists = new ArrayList<>(INITIAL_CAPACITY);
    sourceToRuleIndex = new ProbingIntegerArrayRawIndex();
    targetIndex = new ProbingIntegerArrayIndex();
    int countScores = init(f);
    scoreNames = new String[countScores];
    for (int i = 0; i < countScores; i++) {
      scoreNames[i] = String.format("%s.%d", featurePrefix, i);
    }
  }

  @Override
  public int size() { return ruleIdCounter.get(); }

  /**
   * Add a rule to the phrase table.
   *
   * @param sourceSequence
   * @param targetSequence
   * @param alignment
   * @param scores
   */
  protected void addEntry(Sequence<IString> sourceSequence,
      Sequence<IString> targetSequence, PhraseAlignment alignment,
      float[] scores) {
    int[] sourceArray = Sequences.toIntArray(sourceSequence);
    int[] targetArray = Sequences.toIntArray(targetSequence);
    int fIndex = sourceToRuleIndex.insertIntoIndex(sourceArray);
    int eIndex = this.targetIndex.indexOf(targetArray, true);

    if (ruleLists.size() <= fIndex) {
      while (ruleLists.size() <= fIndex)
        ruleLists.add(null);
    }
    List<PhraseTableEntry> intTransOpts = ruleLists.get(fIndex);
    if (intTransOpts == null) {
      intTransOpts = new LinkedList<>();
      ruleLists.set(fIndex, intTransOpts);
    }
    intTransOpts.add(new PhraseTableEntry(ruleIdCounter.getAndIncrement(),
        targetIndex.get(eIndex), scores, alignment));
  }

  @Override
  public List<String> getFeatureNames() { return Arrays.asList(scoreNames); }

  /**
   * Load the phrase table from file.
   *
   * @param f
   * @return
   * @throws IOException
   */
  private int init(File f) throws IOException {
    Runtime rt = Runtime.getRuntime();
    long prePhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();

    LineNumberReader reader = IOTools.getReaderFromFile(f);
    int numScores = -1;
    for (String line; (line = reader.readLine()) != null;) {
      List<List<String>> fields = StringUtils.splitFieldsFast(line, CompiledPhraseTable.FIELD_DELIM);

      // The standard format has five fields
      assert fields.size() == 5 : String.format("phrase table line %d has %d fields: %s",
          reader.getLineNumber(), fields.size(), line);
      Sequence<IString> source = IStrings.toIStringSequence(fields.get(0));
      Sequence<IString> target = IStrings.toIStringSequence(fields.get(1));
//      String sourceConstellation = fields[2];
      String targetConstellation = StringUtils.join(fields.get(3));
      List<String> scoreList = fields.get(4);

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
        scores = IOTools.stringListToNumeric(scoreList);
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
    return numScores;
  }

  @Override
  public int maxLengthSource() {
    return longestSourcePhrase;
  }

  @Override
  public int maxLengthTarget() {
    return longestTargetPhrase;
  }

  @Override
  public List<Rule<IString>> query(Sequence<IString> sourceSequence) {
    int[] sourceArray = Sequences.toIntArray(sourceSequence);
    int fIndex = sourceToRuleIndex.getIndex(sourceArray);
    if (fIndex == -1 || fIndex >= ruleLists.size())
      return null;
    List<PhraseTableEntry> hits = ruleLists.get(fIndex);
    if (hits == null)
      return null;
    List<Rule<IString>> ruleList = new ArrayList<>(hits.size());
    for (PhraseTableEntry hit : hits) {
      Sequence<IString> targetSequence = IStrings.toIStringSequence(
          hit.targetArray);
      ruleList.add(new Rule<IString>(hit.id,
          hit.scores, scoreNames, targetSequence, sourceSequence,
          hit.alignment));
    }
    return ruleList;
  }

  @Override
  public int getId(Sequence<IString> sourceSequence,
      Sequence<IString> targetSequence) {
    int[] sourceArray = Sequences.toIntArray(sourceSequence);
    int fIndex = sourceToRuleIndex.getIndex(sourceArray);
    if (fIndex == -1 || fIndex >= ruleLists.size()) {
      return -1;
    }
    List<PhraseTableEntry> intTransOpts = ruleLists.get(fIndex);
    int[] targetArray = Sequences.toIntArray(targetSequence);
    for (PhraseTableEntry intTransOpt : intTransOpts) {
      if (Arrays.equals(targetArray, intTransOpt.targetArray)) {
        return intTransOpt.id;
      }
    }
    return -1;
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
  public int minRuleIndex() {
    return minRuleIndex;
  }

  @Override
  public RuleGrid<IString, FV> getRuleGrid(Sequence<IString> source,
      InputProperties sourceInputProperties, List<Sequence<IString>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
  
  /**
   *
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.printf("Usage: java %s pt_file file%n", 
          CompiledPhraseTable.class.getName());
      System.exit(-1);
    }
    String model = args[0];
    String inputFile = args[1];
    
    // Load
    TimeKeeper timer = TimingUtils.start();
    TranslationModel<IString,String> tm = 
        TranslationModelFactory.<String>factory(model);
    tm = new CombinedTranslationModel<>(tm);
    timer.mark("Load");
    
    List<Sequence<IString>> sourceFile = IStrings.tokenizeFile(inputFile);
    System.out.printf("#source segments: %d%n", sourceFile.size());
    
    // Query
    long startTime = TimingUtils.startTime();
    for (Sequence<IString> source : sourceFile) {
      tm.getRuleGrid(source, null, null, 0, null);
    }
    timer.mark("Decode");
    double timePerSegment = TimingUtils.elapsedSeconds(startTime) / (double) sourceFile.size();
    System.out.println(timer);
    System.out.printf("Time/segment:\t%.3fs%n", timePerSegment);
  }
}
