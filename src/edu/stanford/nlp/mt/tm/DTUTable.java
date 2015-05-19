package edu.stanford.nlp.mt.tm;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Arrays;
import java.io.IOException;
import java.io.File;
import java.io.LineNumberReader;
import java.util.regex.Pattern;

import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.IntegerArrayIndex;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.ProbingIntegerArrayIndex;
import edu.stanford.nlp.mt.util.RawSequence;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TrieIntegerArrayIndex;
import edu.stanford.nlp.util.StringUtils;

/**
 * Phrase table with gaps.
 * 
 * @author Michel Galley
 *
 * @param <FV>
 */
public class DTUTable<FV> extends AbstractPhraseGenerator<IString, FV>
implements PhraseTable<IString> {

  public static final IString GAP_STR = new IString("X");
  public static final boolean DEBUG = true;

  private static final int INITIAL_CAPACITY = 50000;
  
  private static final String MIN_GAP_SIZE_PROPERTY = "minSourceGapSize";
  public static final int MIN_GAP_SIZE = Integer.parseInt(System.getProperty(
      MIN_GAP_SIZE_PROPERTY, "1"));
  static {
    System.err.println("Minimum gap size: " + MIN_GAP_SIZE);
  }

  public IntegerArrayIndex sourceIndex;
  public IntegerArrayIndex ruleIndex;
  public final List<List<PhraseTableEntry>> translations;
  protected String name;
  private int numRules = 0;
  
  public final String[] scoreNames;
  
  protected int longestSourcePhrase = -1;
  protected int longestTargetPhrase = -1;
  
  public static int maxPhraseSpan = 12;
  public static int maxNumberTargetSegments = 2;

  // Note: unpredictable result with more than one phrase table! (TODO: make
  // non-static)
  private static ArrayList<float[][]> gapSizeScoresF, gapSizeScoresE;

  public static void setMaxPhraseSpan(int m) {
    maxPhraseSpan = m;
  }

  public static float getSourceGapScore(int fIndex, int gapId, int binId) {
    if (gapSizeScoresF == null)
      return 0.0f;
    return gapSizeScoresF.get(fIndex)[gapId][binId];
  }

  public static float getTargetGapScore(int fIndex, int gapId, int binId) {
    if (gapSizeScoresE == null)
      return 0.0f;
    return gapSizeScoresE.get(fIndex)[gapId][binId];
  }

  public DTUTable(String filename) throws IOException {
    super(null);
    System.err.println("DTU phrase table: " + filename);
    File f = new File(filename);
    name = String.format("DTU(%s)", f.getName());
    translations = new ArrayList<>(INITIAL_CAPACITY);
    sourceIndex = new TrieIntegerArrayIndex();
    ruleIndex = new ProbingIntegerArrayIndex();
    int countScores = init(f);
    scoreNames = new String[countScores];
    for (int i = 0; i < countScores; i++) {
      scoreNames[i] = String.format("%s.%d", CompiledPhraseTable.DEFAULT_FEATURE_PREFIX, i);
    }
  }
  
  /**
   * Load the phrase table from file. 
   * 
   * @param f
   * @param reverse
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
      assert fields.size() == 5 : String.format("phrase table line %d has %d fields", 
          reader.getLineNumber(), fields.size());
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

  static class MatchState {
    final int state;
    final int pos;
    final CoverageSet coverage;
    final IString[] foreign;

    MatchState(int state, int pos) {
      this.state = state;
      this.pos = pos;
      coverage = new CoverageSet();
      foreign = new IString[0];
    }

    MatchState(int state, int pos, CoverageSet coverage, IString[] foreign) {
      this.state = state;
      this.pos = pos;
      this.coverage = coverage;
      this.foreign = foreign;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("state=").append(state).append(" ");
      sb.append("pos=").append(pos).append(" ");
      sb.append("cs=").append(coverage).append(" ");
      sb.append("foreign=").append(Arrays.toString(foreign));
      return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
      assert (o instanceof MatchState);
      MatchState s = (MatchState) o;
      return state == s.state && pos == s.pos && coverage.equals(s.coverage)
          && (Arrays.equals(foreign, s.foreign));
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new int[] { state, pos, coverage.hashCode(),
          Arrays.hashCode(foreign) });
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ConcreteRule<IString,FV>> getRules(
      Sequence<IString> sequence, InputProperties sourceInputProperties, List<Sequence<IString>> targets,
      int sourceInputId, Scorer<FV> scorer) {

    assert (targets == null);
    List<ConcreteRule<IString,FV>> opts = new LinkedList<ConcreteRule<IString,FV>>();
    int sequenceSz = sequence.size();
    // System.err.println("Seq to match: "+sequence);

    assert (sourceIndex instanceof TrieIntegerArrayIndex);
    TrieIntegerArrayIndex trieIndex = (TrieIntegerArrayIndex) sourceIndex;

    for (int startIdx = 0; startIdx < sequenceSz; startIdx++) {
      // System.err.println("startIdx: "+startIdx);
      Deque<MatchState> deque = new LinkedList<MatchState>();
      deque.add(new MatchState(TrieIntegerArrayIndex.IDX_ROOT, startIdx));

      while (!deque.isEmpty()) {

        MatchState s = deque.pop();
        // System.err.println("Current state: "+s);

        if (translations.get(s.state) != null) {

          // Final state:
          List<PhraseTableEntry> intTransOpts = translations
              .get(s.state);
          if (intTransOpts != null) {
            // System.err.printf("Full match: %s\n",s);
            List<Rule<IString>> transOpts = new ArrayList<Rule<IString>>(
                intTransOpts.size());
            for (PhraseTableEntry intTransOpt : intTransOpts) {
              if (intTransOpt instanceof DTUIntArrayTranslationOption) {
                // Gaps in target:
                DTUIntArrayTranslationOption multiIntTransOpt = (DTUIntArrayTranslationOption) intTransOpt;
                Sequence<IString>[] dtus = new RawSequence[multiIntTransOpt.dtus.length];
                for (int i = 0; i < multiIntTransOpt.dtus.length; ++i) {
                  dtus[i] = IStrings.toIStringSequence(multiIntTransOpt.dtus[i]);
                }
                transOpts.add(new DTURule<IString>(intTransOpt.id,
                    intTransOpt.scores, scoreNames, dtus, new RawSequence<IString>(
                        s.foreign), intTransOpt.alignment));
              } else {
                // No gaps in target:
                Sequence<IString> translation = IStrings.toIStringSequence(
                    intTransOpt.targetArray);
                transOpts.add(new Rule<IString>(intTransOpt.id,
                    intTransOpt.scores, scoreNames, translation,
                    new RawSequence<IString>(s.foreign), intTransOpt.alignment));
              }
            }

            for (Rule<IString> abstractOpt : transOpts) {
              if (abstractOpt instanceof DTURule)
                opts.add(new ConcreteRule<IString,FV>(abstractOpt,
                    s.coverage, phraseFeaturizer, scorer, sequence, this
                        .getName(), sourceInputId, true, sourceInputProperties));
              else
                opts.add(new ConcreteRule<IString,FV>(abstractOpt,
                    s.coverage, phraseFeaturizer, scorer, sequence, this
                        .getName(), sourceInputId, sourceInputProperties));
            }
          }
        }

        // Match the next word at s.pos:
        if (s.pos < sequence.size()) {
          long terminalTransition = trieIndex.getTransition(s.state,
              sequence.get(s.pos).id);
          // System.err.printf("Trying to match word: %s [%d,%d]\n",sequence.get(s.pos),
          // s.state, sequence.get(s.pos).id);
          int nextState = trieIndex.map.get(terminalTransition);
          if (nextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
            CoverageSet coverage = s.coverage.clone();
            coverage.set(s.pos);
            IString[] foreign = new IString[s.foreign.length + 1];
            System.arraycopy(s.foreign, 0, foreign, 0, s.foreign.length);
            foreign[foreign.length - 1] = sequence.get(s.pos);
            MatchState newS = new MatchState(nextState, s.pos + 1, coverage,
                foreign);
            deque.add(newS);
            // System.err.printf("Matched word: %s\n",newS);
          } else {
            // System.err.printf("Unable to match word: %s\n",
            // sequence.get(s.pos));
          }
        }

        // Match an X at s.pos:
        if (s.pos > startIdx && s.pos + 1 < sequence.size()) {
          long nonterminalTransition = trieIndex.getTransition(s.state,
              GAP_STR.id);
          int nextState = trieIndex.map.get(nonterminalTransition);
          if (nextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
            // Starting a gap, now must determine how long:
            for (int afterX = s.pos + MIN_GAP_SIZE; afterX <= startIdx
                + maxPhraseSpan
                && afterX < sequence.size(); ++afterX) {
              long terminalTransition = trieIndex.getTransition(nextState,
                  sequence.get(afterX).id);
              int next2State = trieIndex.map.get(terminalTransition);
              if (next2State != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
                CoverageSet coverage = s.coverage.clone();
                coverage.set(afterX);
                IString[] foreign = new IString[s.foreign.length + 2];
                System.arraycopy(s.foreign, 0, foreign, 0, s.foreign.length);
                foreign[foreign.length - 2] = GAP_STR;
                foreign[foreign.length - 1] = sequence.get(afterX);
                MatchState newS = new MatchState(next2State, afterX + 1,
                    coverage, foreign);
                deque.add(newS);
                // System.err.printf("Matched gap: %s\n",newS);
              }
            }
          }
        }
      }
    }
    return opts;
  }

  // Custom version of Sequences.toIntArray, which converts tags to X.
  static int[] toWordIndexArray(Sequence<IString> seq) {
    int[] arr = new int[seq.size()];
    for (int i = 0; i < seq.size(); ++i) {
      IString el = seq.get(i);
      char c0 = el.toString().charAt(0);
      if (Character.isUpperCase(c0)) {
        if (el.toString().startsWith(GAP_STR.toString())) {
          arr[i] = GAP_STR.id;
        } else {
          System.err.println("Ill-formed symbol: "+ el.toString());
          arr[i] = el.id;
        }
      } else {
        arr[i] = el.id;
      }
    }
    return arr;
  }

  static int[] toWordIndexArray(IString[] seq) {
    return toWordIndexArray(new SimpleSequence<IString>(true, seq));
  }

  private final static Pattern pattern = Pattern.compile("[,X\\[\\]]+");

  // Custom version of Sequences.toIntArray, which converts tags to X.
  static float[][] toGapSizeScores(Sequence<IString> seq) {
    List<float[]> list = new LinkedList<float[]>();
    for (int i = 0; i < seq.size(); ++i) {
      IString el = seq.get(i);
      char c0 = el.toString().charAt(0);
      if (Character.isUpperCase(c0)) {
        if (el.toString().startsWith(GAP_STR.toString())) {
          if (el.id != GAP_STR.id) {
            String[] strs = pattern.split(el.toString());
            assert (strs.length == 5);
            float[] scores = new float[strs.length - 1];
            for (int j = 1; j < strs.length; ++j) {
              float score = Float.parseFloat(strs[j]);
              scores[j - 1] = (float) Math.log(score);
            }
            list.add(scores);
          }
        }
      }
    }
    return list.toArray(new float[list.size()][]);
  }

  protected void addEntry(Sequence<IString> sourceSequence,
      Sequence<IString> targetSequence, PhraseAlignment alignment,
      float[] scores) {
    int[] foreignInts = toWordIndexArray(sourceSequence);
    int[] translationInts = toWordIndexArray(targetSequence);
    int fIndex = sourceIndex.indexOf(foreignInts, true);
    int eIndex = ruleIndex.indexOf(translationInts, true);
    int id = ruleIndex.indexOf(new int[] { fIndex, eIndex }, true);

    float[][] foreignGapSzScores = toGapSizeScores(sourceSequence);
    float[][] translationGapSzScores = toGapSizeScores(targetSequence);

    if (foreignGapSzScores.length > 0) {
      if (gapSizeScoresF == null)
        gapSizeScoresF = new ArrayList<float[][]>();
      while (id >= gapSizeScoresF.size())
        gapSizeScoresF.add(null);
      gapSizeScoresF.set(id, foreignGapSzScores);
    }

    if (translationGapSzScores.length > 0) {
      if (gapSizeScoresE == null)
        gapSizeScoresE = new ArrayList<float[][]>();
      while (id >= gapSizeScoresE.size())
        gapSizeScoresE.add(null);
      gapSizeScoresE.set(id, translationGapSzScores);
    }

    if (translations.size() <= fIndex) {
      while (translations.size() <= fIndex)
        translations.add(null);
    }

    List<PhraseTableEntry> intTransOpts = translations.get(fIndex);

    if (intTransOpts == null) {
      intTransOpts = new LinkedList<PhraseTableEntry>();
      translations.set(fIndex, intTransOpts);
      numRules++;
    }

    int numTgtSegments = 1;
    for (int el : translationInts) {
      if (el == GAP_STR.id) {
        ++numTgtSegments;
      }
    }
    if (numTgtSegments == 1) {
      // no gaps:
      intTransOpts.add(new PhraseTableEntry(id, ruleIndex.get(eIndex), scores, alignment));
    } else {
      // gaps:
      if (numTgtSegments > maxNumberTargetSegments)
        return;
      int start = 0, pos = 0;
      int i = -1;
      int[][] dtus = new int[numTgtSegments][];
      while (pos <= translationInts.length) {
        if (pos == translationInts.length || translationInts[pos] == GAP_STR.id) {
          dtus[++i] = Arrays.copyOfRange(translationInts, start, pos);
          start = pos + 1;
          if (pos == translationInts.length)
            break;
        }
        ++pos;
      }
      intTransOpts.add(new DTUIntArrayTranslationOption(id, dtus, scores,
          alignment));
    }
  }

  protected static class DTUIntArrayTranslationOption extends
      PhraseTableEntry {

    final int[][] dtus;

    public DTUIntArrayTranslationOption(int id, int[][] dtus, float[] scores,
        PhraseAlignment alignment) {
      super(id, new int[0], scores, alignment);
      this.dtus = dtus;
    }
  }

  @Override
  public List<Rule<IString>> query(
      Sequence<IString> foreignSequence) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public List<String> getFeatureNames() { return Arrays.asList(scoreNames); }
  
  @Override
  public int getId(Sequence<IString> sourceSequence,
      Sequence<IString> targetSequence) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return numRules;
  }

  @Override
  public String getName() {
    return name;
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
  public int minRuleIndex() {
    return 0;
  }

  @Override
  public RuleGrid<IString, FV> getRuleGrid(Sequence<IString> source,
      InputProperties sourceInputProperties, List<Sequence<IString>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public void setName(String name) { this.name = name; }
}
