package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;

import java.util.Iterator;

/**
 * 
 * @author Michel Galley
 *
 * @param <TK>
 * @param <FV>
 */
public class DTURecombinationFilter<TK, FV> implements RecombinationFilter<Hypothesis<TK, FV>> {

  public static final boolean DEBUG = false;

  @Override
	public Object clone() throws CloneNotSupportedException {
    return super.clone();
	}


  @Override
	public boolean combinable(Hypothesis<TK, FV> hypA, Hypothesis<TK, FV> hypB) {
		boolean isDTU_A = hypA instanceof DTUHypothesis;
    boolean isDTU_B = hypB instanceof DTUHypothesis;
    boolean combine;
    if(!isDTU_A && !isDTU_B) {
      combine = true;
    } else if(isDTU_A && !isDTU_B) {
      DTUHypothesis dtuA = (DTUHypothesis<TK,FV>) hypA;
      combine = (dtuA.pendingPhrases.isEmpty());
    } else if(!isDTU_A) {
      DTUHypothesis dtuB = (DTUHypothesis<TK,FV>) hypB;
      combine = (dtuB.pendingPhrases.isEmpty());
    } else {
      DTUHypothesis<TK,FV> dtuA = (DTUHypothesis<TK,FV>) hypA;
      DTUHypothesis<TK,FV> dtuB = (DTUHypothesis<TK,FV>) hypB;
      if (dtuA.pendingPhrases.size() != dtuB.pendingPhrases.size()) {
        combine = false;
      } else {
        combine = true;
        Iterator<DTUHypothesis.PendingPhrase<TK,FV>> itA = dtuA.pendingPhrases.iterator();
        Iterator<DTUHypothesis.PendingPhrase<TK,FV>> itB = dtuB.pendingPhrases.iterator();
        while (itA.hasNext()) {
          assert (itB.hasNext());
          DTUHypothesis.PendingPhrase<TK,FV> elA = itA.next();
          DTUHypothesis.PendingPhrase<TK,FV> elB = itB.next();
          if(elA.concreteOpt.abstractOption != elB.concreteOpt.abstractOption || elA.segmentIdx != elB.segmentIdx) {
            combine = false;
            break;
          }
        }
        assert (!combine || !itB.hasNext());
      }
    }
    //System.err.printf("Recombine\t%s\t{{{%s}}}\t{{{%s}}}\n", combine, hypA.toString(), hypB.toString());
    return combine;
  }

	@Override
	public long recombinationHashCode(Hypothesis<TK, FV> hyp) {
		return hyp.foreignCoverage.hashCode();
	}

}
