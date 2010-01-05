package mt.decoder.recomb;

import mt.decoder.util.Hypothesis;
import mt.decoder.util.DTUHypothesis;
import mt.base.Sequence;

/**
 * 
 * @author Michel Galley
 *
 * @param <TK>
 * @param <FV>
 */
public class DTURecombinationFilter<TK, FV> implements RecombinationFilter<Hypothesis<TK, FV>> {

  public static final boolean DEBUG = false;

  @SuppressWarnings("unchecked")
	public RecombinationFilter<Hypothesis<TK,FV>> clone() {
		try {
			return (RecombinationFilter<Hypothesis<TK,FV>>)super.clone(); 
		} catch (CloneNotSupportedException e) { return null; /* wnh */ }
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
      combine = (dtuA.sortedFloatingPhrases.size() == 0);
    } else if(!isDTU_A) {
      DTUHypothesis dtuB = (DTUHypothesis<TK,FV>) hypB;
      combine = (dtuB.sortedFloatingPhrases.size() == 0);
    } else {
      DTUHypothesis<TK,FV> dtuA = (DTUHypothesis<TK,FV>) hypA;
      DTUHypothesis<TK,FV> dtuB = (DTUHypothesis<TK,FV>) hypB;
      if (dtuA.sortedFloatingPhrases.size() != dtuB.sortedFloatingPhrases.size()) {
        combine = false;
      } else {
        combine = true;
        for (int i=0; i<dtuA.sortedFloatingPhrases.size(); ++i) {
          Sequence<TK> seqA = dtuA.sortedFloatingPhrases.get(i);
          Sequence<TK> seqB = dtuB.sortedFloatingPhrases.get(i);
          if(!seqA.equals(seqB)) {
            combine = false;
            break;
          }
        }
      }
      /*
      StringBuilder sbA = new StringBuilder(), sbB = new StringBuilder();
      for(Sequence<TK> el : dtuA.sortedFloatingPhrases)
        sbA.append("|").append(el);
      for(Sequence<TK> el : dtuB.sortedFloatingPhrases)
        sbB.append("|").append(el);
      System.err.printf("Recombine\t%s\t%s\t%s\n", combine, sbA, sbB);
      */
    }
    //System.err.printf("Recombine\t%s\t{{{%s}}}\t{{{%s}}}\n", combine, hypA.toString(), hypB.toString());
    return combine;
  }

	@Override
	public long recombinationHashCode(Hypothesis<TK, FV> hyp) {
		return hyp.foreignCoverage.hashCode();
	}

}
