package edu.stanford.nlp.mt.base;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Arrays;
import java.io.IOException;
import java.io.File;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.train.DTUPhraseExtractor;

public class DTUTable<FV> extends PharaohPhraseTable<FV> {

  public static int maxPhraseSpan = 12;
  public static int maxTargetSpans = 2;

  public static void setMaxPhraseSpan(int m) {
    maxPhraseSpan = m;
  }

  public DTUTable(IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer, Scorer<FV> scorer, String filename) throws IOException {
		super(phraseFeaturizer, scorer, filename, true);
    System.err.println("DTU phrase table: "+filename);
    File f = new File(filename);
    name = String.format("DTU(%s)", f.getName());
	}

  class MatchState {
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
  }

  @Override
  @SuppressWarnings("unchecked")
	public List<ConcreteTranslationOption<IString>> translationOptions(Sequence<IString> sequence, List<Sequence<IString>> targets, int translationId) {
    assert(targets == null);
    List<ConcreteTranslationOption<IString>> opts = new LinkedList<ConcreteTranslationOption<IString>>();
		int sequenceSz = sequence.size();
    System.err.println("sent: "+sequence);

    assert(foreignIndex instanceof TrieIntegerArrayIndex);
    TrieIntegerArrayIndex trieIndex = (TrieIntegerArrayIndex) foreignIndex;

    for (int startIdx = 0; startIdx < sequenceSz; startIdx++) {
      //System.err.println("s: "+startIdx);
      Deque<MatchState> deque = new LinkedList<MatchState>();
      deque.add(new MatchState(TrieIntegerArrayIndex.IDX_ROOT, startIdx));
      while(!deque.isEmpty()) {
        MatchState s = deque.pop();
        if(translations.get(s.state) != null) {
          // Final state:
          List<IntArrayTranslationOption> intTransOpts = translations.get(s.state);
          if(intTransOpts != null) {
            List<TranslationOption<IString>> transOpts = new ArrayList<TranslationOption<IString>>(intTransOpts.size());
            for (IntArrayTranslationOption intTransOpt : intTransOpts) {

              if(intTransOpt instanceof DTUIntArrayTranslationOption) {
                // Gaps in target:
                //System.err.println("option: target dtus for input: "+Arrays.toString(s.foreign));
                DTUIntArrayTranslationOption multiIntTransOpt = (DTUIntArrayTranslationOption) intTransOpt;
                RawSequence<IString>[] dtus = new RawSequence[multiIntTransOpt.dtus.length];
                for(int i=0; i<multiIntTransOpt.dtus.length; ++i) {
                  dtus[i] =  new RawSequence<IString>(multiIntTransOpt.dtus[i],
                     IString.identityIndex());
                  //System.err.printf("dtu[%d]: %s\n", i, dtus[i].toString());
                }
                transOpts.add(
                    new DTUOption<IString>(intTransOpt.scores, scoreNames,
                        dtus, new RawSequence(s.foreign), intTransOpt.alignment));
              } else {
                // No gaps in target:
                RawSequence<IString> translation = new RawSequence<IString>(intTransOpt.translation,
                     IString.identityIndex());
                transOpts.add(
                     new TranslationOption<IString>(intTransOpt.scores, scoreNames,
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
        // Try to match the next terminal at s.pos:
        if(s.pos < sequence.size()) {
          long terminalTransition = trieIndex.getTransition(s.state, sequence.get(s.pos).id);
          int nextState = trieIndex.map.get(terminalTransition);
          if(nextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
            CoverageSet coverage = s.coverage.clone();
            coverage.set(s.pos);
            IString[] foreign = new IString[s.foreign.length+1];
            System.arraycopy(s.foreign, 0, foreign, 0, s.foreign.length);
            foreign[foreign.length-1] = sequence.get(s.pos);
            deque.add(new MatchState(nextState, s.pos+1, coverage, foreign));
          }
        }
        // try to match an X at s.pos:
        if(s.pos > startIdx && s.pos+1 < sequence.size()) {
          long nonterminalTransition = trieIndex.getTransition(s.state, DTUPhraseExtractor.GAP_STR.id);
          int nextState = trieIndex.map.get(nonterminalTransition);
          if(nextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
            //System.err.printf("X after %s\n", new SimpleSequence<IString>(true, s.foreign));
            // OK, we found an X, now must determine how long:
            for(int afterX=s.pos+1; afterX <= startIdx+maxPhraseSpan && afterX <sequence.size(); ++afterX) {
              long terminalTransition = trieIndex.getTransition(nextState, sequence.get(afterX).id);
              int next2State = trieIndex.map.get(terminalTransition);
              if(next2State != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
                //System.err.printf("Found a DTU that covers %s\n", sequence.subsequence(startIdx, afterX+1));
                //System.err.printf("  X covers %s\n", sequence.subsequence(s.pos, afterX));
                CoverageSet coverage = s.coverage.clone();
                coverage.set(afterX);
                IString[] foreign = new IString[s.foreign.length+2];
                System.arraycopy(s.foreign, 0, foreign, 0, s.foreign.length);
                foreign[foreign.length-2] = DTUPhraseExtractor.GAP_STR;
                foreign[foreign.length-1] = sequence.get(afterX);
                deque.add(new MatchState(next2State, afterX, coverage, foreign));
              }
            }
          }
        }
      }
		}
    return opts;
  }

  @Override
  protected void addEntry(Sequence<IString> foreignSequence, Sequence<IString> translationSequence, PhraseAlignment alignment,
			float[] scores) {

    int[] foreignInts = Sequences.toIntArray(foreignSequence);
    int[] translationInts = Sequences.toIntArray(translationSequence);
		int fIndex = foreignIndex.indexOf(foreignInts, true);

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

    int numSpans = 1;
    for (int el : translationInts) {
      if (el == DTUPhraseExtractor.GAP_STR.id) {
        ++numSpans;
      }
    }
    if (numSpans == 1) {
      intTransOpts.add(new IntArrayTranslationOption(translationInts, scores, alignment));
      //System.err.printf("no gap in target: {{{%s}}} {{{%s}}} {{{%s}}}\n", translationSequence, foreignSequence, Arrays.toString(scores));
    } else {
      if(numSpans > maxTargetSpans)
        return;
      //System.err.printf("gap in target: {{{%s}}} {{{%s}}} {{{%s}}}\n", translationSequence, foreignSequence, Arrays.toString(scores));
      int start=0, pos=0;
      int i = -1;
      int[][] dtus = new int[numSpans][];
      while (pos <= translationInts.length) {
        if (pos == translationInts.length || translationInts[pos] == DTUPhraseExtractor.GAP_STR.id) {
          dtus[++i] = Arrays.copyOfRange(translationInts,start,pos);
          //System.err.printf("Span: {{{%s}}}\n", Arrays.toString(IStrings.toStringArray(dtus[i])));
          start = pos+1;
          if (pos == translationInts.length)
            break;
        }
        ++pos;
      }
      intTransOpts.add(new DTUIntArrayTranslationOption(dtus, scores, alignment));
    }
  }

  protected static class DTUIntArrayTranslationOption extends IntArrayTranslationOption {

    final int[][] dtus;

    public DTUIntArrayTranslationOption(int[][] dtus, float[] scores, PhraseAlignment alignment) {
      super(new int[0], scores, alignment);
      this.dtus = dtus;
    }
	}

  @Override
	public List<TranslationOption<IString>> getTranslationOptions(Sequence<IString> foreignSequence) {
    throw new UnsupportedOperationException();
  }
}
