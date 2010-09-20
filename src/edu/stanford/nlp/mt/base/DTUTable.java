package edu.stanford.nlp.mt.base;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Arrays;
import java.io.IOException;
import java.io.File;
import java.util.regex.Pattern;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;

public class DTUTable<FV> extends MosesPhraseTable<FV> {

  static public final IString GAP_STR = new IString("X");
  static public final boolean DEBUG = true;

  public static final String MIN_GAP_SIZE_PROPERTY = "minGapSize";
  public static final int MIN_GAP_SIZE = Integer.parseInt(System.getProperty(MIN_GAP_SIZE_PROPERTY, "1"));
  static { System.err.println("Minimum gap size: "+MIN_GAP_SIZE); }

  public static int maxPhraseSpan = 12;
  public static int maxNumberTargetSegments = 2;

  // Note: unpredictable result with more than one phrase table! (TODO: make non-static)
  private static ArrayList<float[][]> gapSizeScoresF, gapSizeScoresE;

  public static void setMaxPhraseSpan(int m) {
    maxPhraseSpan = m;
  }

  @SuppressWarnings("unused")
  public static float getSourceGapScore(int fIndex, int gapId, int binId) {
    if (gapSizeScoresF == null) return 0.0f;
    return gapSizeScoresF.get(fIndex)[gapId][binId];
  }

  @SuppressWarnings("unused")
  public static float getTargetGapScore(int fIndex, int gapId, int binId) {
    if (gapSizeScoresE == null) return 0.0f;
    return gapSizeScoresE.get(fIndex)[gapId][binId];
  }

  public DTUTable(IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer, Scorer<FV> scorer, String filename) throws IOException {
		super(phraseFeaturizer, scorer, filename);
    System.err.println("DTU phrase table: "+filename);
    File f = new File(filename);
    name = String.format("DTU(%s)", f.getName());
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
      assert(o instanceof MatchState);
      MatchState s = (MatchState) o;
      return
        state == s.state &&
        pos == s.pos &&
        coverage.equals(s.coverage) &&
        (Arrays.equals(foreign, s.foreign));
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new int[]
        { state, pos, coverage.hashCode(), Arrays.hashCode(foreign)});
    }
  }

  @Override
  @SuppressWarnings("unchecked")
	public List<ConcreteTranslationOption<IString>> translationOptions(Sequence<IString> sequence, List<Sequence<IString>> targets, int translationId) {

    assert (targets == null);
    List<ConcreteTranslationOption<IString>> opts = new LinkedList<ConcreteTranslationOption<IString>>();
		int sequenceSz = sequence.size();
    //System.err.println("Seq to match: "+sequence);

    assert (foreignIndex instanceof TrieIntegerArrayIndex);
    TrieIntegerArrayIndex trieIndex = (TrieIntegerArrayIndex) foreignIndex;

    for (int startIdx = 0; startIdx < sequenceSz; startIdx++) {
      //System.err.println("startIdx: "+startIdx);
      Deque<MatchState> deque = new LinkedList<MatchState>();
      deque.add(new MatchState(TrieIntegerArrayIndex.IDX_ROOT, startIdx));

      while (!deque.isEmpty()) {

        MatchState s = deque.pop();
        //System.err.println("Current state: "+s);

        if (translations.get(s.state) != null) {

          // Final state:
          List<IntArrayTranslationOption> intTransOpts = translations.get(s.state);
          if (intTransOpts != null) {
            //System.err.printf("Full match: %s\n",s);
            List<TranslationOption<IString>> transOpts = new ArrayList<TranslationOption<IString>>(intTransOpts.size());
            for (IntArrayTranslationOption intTransOpt : intTransOpts) {
              if (intTransOpt instanceof DTUIntArrayTranslationOption) {
                // Gaps in target:
                DTUIntArrayTranslationOption multiIntTransOpt = (DTUIntArrayTranslationOption) intTransOpt;
                RawSequence<IString>[] dtus = new RawSequence[multiIntTransOpt.dtus.length];
                for (int i=0; i<multiIntTransOpt.dtus.length; ++i) {
                  dtus[i] =  new RawSequence<IString>(multiIntTransOpt.dtus[i],
                     IString.identityIndex());
                }
                transOpts.add(
                    new DTUOption<IString>(intTransOpt.id, intTransOpt.scores, scoreNames,
                        dtus, new RawSequence(s.foreign), intTransOpt.alignment));
              } else {
                // No gaps in target:
                RawSequence<IString> translation = new RawSequence<IString>(intTransOpt.translation,
                     IString.identityIndex());
                transOpts.add(
                     new TranslationOption<IString>(intTransOpt.id, intTransOpt.scores, scoreNames,
                          translation, new RawSequence(s.foreign), intTransOpt.alignment));
              }
            }

            for (TranslationOption<IString> abstractOpt : transOpts) {
              if(abstractOpt instanceof DTUOption)
                opts.add(new ConcreteTranslationOption<IString>(abstractOpt, s.coverage, phraseFeaturizer, scorer, sequence, this.getName(), translationId, true));
              else
                opts.add(new ConcreteTranslationOption<IString>(abstractOpt, s.coverage, phraseFeaturizer, scorer, sequence, this.getName(), translationId));
            }
          }
        }

        // Match the next word at s.pos:
        if (s.pos < sequence.size()) {
          long terminalTransition = trieIndex.getTransition(s.state, sequence.get(s.pos).id);
          //System.err.printf("Trying to match word: %s [%d,%d]\n",sequence.get(s.pos), s.state, sequence.get(s.pos).id);
          int nextState = trieIndex.map.get(terminalTransition);
          if (nextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
            CoverageSet coverage = s.coverage.clone();
            coverage.set(s.pos);
            IString[] foreign = new IString[s.foreign.length+1];
            System.arraycopy(s.foreign, 0, foreign, 0, s.foreign.length);
            foreign[foreign.length-1] = sequence.get(s.pos);
            MatchState newS = new MatchState(nextState, s.pos+1, coverage, foreign);
            deque.add(newS);
            //System.err.printf("Matched word: %s\n",newS);
          } else {
            //System.err.printf("Unable to match word: %s\n", sequence.get(s.pos));
          }
        }

        // Match an X at s.pos:
        if (s.pos > startIdx && s.pos+1 < sequence.size()) {
          long nonterminalTransition = trieIndex.getTransition(s.state, GAP_STR.id);
          int nextState = trieIndex.map.get(nonterminalTransition);
          if (nextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
            // Starting a gap, now must determine how long:
            for (int afterX = s.pos+MIN_GAP_SIZE; afterX <= startIdx+maxPhraseSpan && afterX <sequence.size(); ++afterX) {
              long terminalTransition = trieIndex.getTransition(nextState, sequence.get(afterX).id);
              int next2State = trieIndex.map.get(terminalTransition);
              if (next2State != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
                CoverageSet coverage = s.coverage.clone();
                coverage.set(afterX);
                IString[] foreign = new IString[s.foreign.length+2];
                System.arraycopy(s.foreign, 0, foreign, 0, s.foreign.length);
                foreign[foreign.length-2] = GAP_STR;
                foreign[foreign.length-1] = sequence.get(afterX);
                MatchState newS = new MatchState(next2State, afterX+1, coverage, foreign);
                deque.add(newS);
                //System.err.printf("Matched gap: %s\n",newS);
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
    for (int i=0; i<seq.size(); ++i) {
      IString el = seq.get(i);
      char c0 = el.word().charAt(0);
      if (Character.isUpperCase(c0)) {
        assert (el.word().startsWith(GAP_STR.word()));
        arr[i] = GAP_STR.id;
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
    for (int i=0; i<seq.size(); ++i) {
      IString el = seq.get(i);
      char c0 = el.word().charAt(0);
      if (Character.isUpperCase(c0)) {
        assert (el.word().startsWith(GAP_STR.word()));
        if (el.id != GAP_STR.id) {
          String[] strs = pattern.split(el.word());
          assert (strs.length == 5);
          float[] scores = new float[strs.length-1];
          for (int j=1; j<strs.length; ++j) {
            float score = Float.parseFloat(strs[j]);
            scores[j-1] = (float) Math.log(score); 
          }
          list.add(scores);
        }
      }
    }
    return list.toArray(new float[list.size()][]);
  }

  @Override
  protected void addEntry(Sequence<IString> foreignSequence, Sequence<IString> translationSequence, PhraseAlignment alignment,
			float[] scores) {

    int[] foreignInts = toWordIndexArray(foreignSequence);
    int[] translationInts = toWordIndexArray(translationSequence);
		int fIndex = foreignIndex.indexOf(foreignInts, true);
    int eIndex = translationIndex.indexOf(translationInts, true);
    int id = translationIndex.indexOf(new int[] {fIndex, eIndex}, true);

    float[][] foreignGapSzScores = toGapSizeScores(foreignSequence);
    float[][] translationGapSzScores = toGapSizeScores(translationSequence);

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
			translations.ensureCapacity(fIndex+1);
			while (translations.size() <= fIndex)
        translations.add(null);
		}

    List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);

    if (intTransOpts == null) {
			intTransOpts = new LinkedList<IntArrayTranslationOption>();
			translations.set(fIndex, intTransOpts);
		}

    int numTgtSegments = 1;
    for (int el : translationInts) {
      if (el == GAP_STR.id) {
        ++numTgtSegments;
      }
    }
    if (numTgtSegments == 1) {
      // no gaps:
      intTransOpts.add(new IntArrayTranslationOption(id, translationIndex.get(eIndex), scores, alignment));
    } else {
      // gaps:
      if(numTgtSegments > maxNumberTargetSegments)
        return;
      int start=0, pos=0;
      int i = -1;
      int[][] dtus = new int[numTgtSegments][];
      while (pos <= translationInts.length) {
        if (pos == translationInts.length || translationInts[pos] == GAP_STR.id) {
          dtus[++i] = Arrays.copyOfRange(translationInts,start,pos);
          start = pos+1;
          if (pos == translationInts.length)
            break;
        }
        ++pos;
      }
      intTransOpts.add(new DTUIntArrayTranslationOption(id, dtus, scores, alignment));
    }
  }

  protected static class DTUIntArrayTranslationOption extends IntArrayTranslationOption {

    final int[][] dtus;

    public DTUIntArrayTranslationOption(int id, int[][] dtus, float[] scores, PhraseAlignment alignment) {
      super(id, new int[0], scores, alignment);
      this.dtus = dtus;
    }
	}

  @Override
	public List<TranslationOption<IString>> getTranslationOptions(Sequence<IString> foreignSequence) {
    throw new UnsupportedOperationException();
  }

  @Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
}
