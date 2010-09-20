package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;

import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Michel Galley
 */
public class DTURecombinationFilter<TK, FV> implements RecombinationFilter<Hypothesis<TK, FV>> {

  public static final boolean DEBUG = false;

  // With any of these options turned on, the filter may combine hypotheses that shouldn't be combined,
  // but this increases stack diversity:
  public static final String EQ_NUM_PHRASES_RECOMBINATION_PROPERTY = "phraseCountDTURecombination";
  public static final boolean EQ_NUM_PHRASES_RECOMBINATION = Boolean.parseBoolean(System.getProperty(EQ_NUM_PHRASES_RECOMBINATION_PROPERTY, "false"));

  public static final String EQ_NUM_WORDS_RECOMBINATION_PROPERTY = "wordCountDTURecombination";
  public static final boolean EQ_NUM_WORDS_RECOMBINATION = Boolean.parseBoolean(System.getProperty(EQ_NUM_WORDS_RECOMBINATION_PROPERTY, "false"));

  public static final String SIMPLE_RECOMBINATION_PROPERTY = "simpleDTURecombination";
  public static final boolean SIMPLE_RECOMBINATION = Boolean.parseBoolean(System.getProperty(SIMPLE_RECOMBINATION_PROPERTY, "false"));

  @Override
	public Object clone() throws CloneNotSupportedException {
    return super.clone();
	}

  @Override
	public boolean combinable(Hypothesis<TK, FV> hypA, Hypothesis<TK, FV> hypB) {

		boolean isDTU_A = hypA instanceof DTUHypothesis;
    boolean isDTU_B = hypB instanceof DTUHypothesis;

    if (SIMPLE_RECOMBINATION) {
      return isDTU_A == isDTU_B;
    }

    if (!isDTU_A && !isDTU_B) {
      return true;
    } else if(isDTU_A && !isDTU_B) {
      DTUHypothesis dtuA = (DTUHypothesis<TK,FV>) hypA;
      return (dtuA.pendingPhrases.isEmpty());
    } else if(!isDTU_A) {
      DTUHypothesis dtuB = (DTUHypothesis<TK,FV>) hypB;
      return (dtuB.pendingPhrases.isEmpty());
    } else {
      DTUHypothesis<TK,FV> dtuA = (DTUHypothesis<TK,FV>) hypA;
      DTUHypothesis<TK,FV> dtuB = (DTUHypothesis<TK,FV>) hypB;

      if (EQ_NUM_WORDS_RECOMBINATION) {
        return dtuA.pendingWords() == dtuB.pendingWords();
      } else {
        if (dtuA.pendingPhrases.size() != dtuB.pendingPhrases.size()) {
          return false;
        } else {
          if (EQ_NUM_PHRASES_RECOMBINATION) {
            return true;
          } else {
            // Correct recombination:
            boolean combinable = true;
            Iterator<DTUHypothesis.PendingPhrase<TK,FV>> itA = dtuA.pendingPhrases.iterator();
            Iterator<DTUHypothesis.PendingPhrase<TK,FV>> itB = dtuB.pendingPhrases.iterator();
            while (itA.hasNext()) {
              assert (itB.hasNext());
              DTUHypothesis.PendingPhrase<TK,FV> elA = itA.next();
              DTUHypothesis.PendingPhrase<TK,FV> elB = itB.next();
              if (elA.concreteOpt.abstractOption != elB.concreteOpt.abstractOption || elA.segmentIdx != elB.segmentIdx) {
                combinable = false;
                break;
              }
            }
            assert (!combinable || !itB.hasNext());
            return combinable;
          }
        }
      }
    }
  }

	@Override
	public long recombinationHashCode(Hypothesis<TK, FV> hyp) {
    boolean isDTU = hyp instanceof DTUHypothesis;
    int isDTUn = isDTU ? 1 : 0;
    if (SIMPLE_RECOMBINATION) return isDTUn;
    DTUHypothesis<TK,FV> dtu = isDTU ? (DTUHypothesis<TK,FV>) hyp : null;
    if (EQ_NUM_WORDS_RECOMBINATION) {
      int pendingWords = dtu == null ? 0 : dtu.pendingWords();
      return Arrays.hashCode(new int[] {isDTUn, pendingWords});
    } else if (EQ_NUM_PHRASES_RECOMBINATION) {
      int pendingPhrases = dtu == null ? 0 : dtu.pendingPhrases.size();
      return Arrays.hashCode(new int[] {isDTUn, pendingPhrases});
    } else  {
      int pendingPhrases = dtu == null ? 0 : dtu.pendingPhrases.size();
      int[] hashes = new int[pendingPhrases+1];
      hashes[0] = isDTUn;
      int i=0;
      for (DTUHypothesis.PendingPhrase pp : dtu.pendingPhrases) {
        hashes[++i] = pp.concreteOpt.abstractOption.hashCode();
      }
      return Arrays.hashCode(hashes);
    }
	}

}
