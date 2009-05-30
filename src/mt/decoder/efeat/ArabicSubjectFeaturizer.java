package mt.decoder.efeat;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.CoverageSet;
import mt.decoder.feat.IncrementalFeaturizer;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.Pair;

public class ArabicSubjectFeaturizer implements IncrementalFeaturizer<IString, String> {

	private static final String FEATURE_NAME = "ArabicTotalSubj";
	private static final double COMPLETE_OVERLAP_PENALTY = 10.0;
  private static final double BAD_HYPOTHESIS_PENALTY = -1.0 * (double) ArabicSubjectBank.getInstance().maxSubjectLength;
	private final String cFilePath;
	private final String rFilePath;
	private boolean noSubjects = false;
	private List<Pair<Integer,Integer>> subjectSpans = null;
	
	public ArabicSubjectFeaturizer(String... args) {
		rFilePath = args[0];
		cFilePath = args[1];
	}
	
//	private boolean isContiguous(List<Integer> span) {
//		//WSGDEBUG Null case? Is this right (an unaligned f phrase)?
//		if(span == null) 
//			return false;
//		else if(span.size() == 1)
//			return true;
//		
//		Collections.sort(span);
//		Set<Integer> sortedSet = new HashSet<Integer>(span);
//		if(sortedSet.size() == 1)
//			return true;
//		
//		Iterator<Integer> itr = sortedSet.iterator();
//		int lastSpanIndex = itr.next();
//		while(itr.hasNext()) {
//			int curSpanIndex = itr.next();
//			if((lastSpanIndex + 1) != curSpanIndex)
//				return false;
//			lastSpanIndex = curSpanIndex;
//		}
//		
//		return true;
//	}
	
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
		if(noSubjects) return null;
		
		CoverageSet coverageForPhrase = f.option.foreignCoverage;
    int fStart = f.foreignPosition;
    int fEnd = fStart + f.foreignPhrase.size() - 1;
    
		Iterator<Pair<Integer,Integer>> itr = subjectSpans.iterator();
    int maxPartialOverlap = 0;
    while(itr.hasNext()) {
      int partialOverlaps = 0;
			Pair<Integer,Integer> subject = itr.next();
      boolean completeOverlap = (fStart == subject.first && fEnd == subject.second); //Same endpoints
      
      //Now ensure consistency (and calculate partials)
      for(int j = subject.first; j <= subject.second; j++) {
        completeOverlap &= coverageForPhrase.get(j);
        partialOverlaps = (coverageForPhrase.get(j)) ? partialOverlaps + 1 : partialOverlaps;
      }
      
      if(partialOverlaps > maxPartialOverlap)
        maxPartialOverlap = partialOverlaps;
      
      if(completeOverlap) {
        //System.err.printf("%s: Complete overlap %s\n", this.getClass().getName(), f.foreignPhrase.toString());
        return new FeatureValue<String>(FEATURE_NAME, COMPLETE_OVERLAP_PENALTY);
      }
		}
    
    if(maxPartialOverlap > 0) {
      double penalty = BAD_HYPOTHESIS_PENALTY / ((double) maxPartialOverlap * (double) maxPartialOverlap);
      return new FeatureValue<String>(FEATURE_NAME, penalty);
    }

    return null;
  }
		//WSGDEBUG Need to penalize current phrase?
		//Fire in two cases:
		// 1) When there is a violation in the f2t [word][phrase_start][phrase_stop]
		// 2) When the coverage set in the hypothesis completely overlaps a hypothesis
		//    indicating that it was translated as a chunk
//		if(lastSubjectInFPhrase >= 0) {
//			//Read the current subject and find its associated span in the alignment index
//			for(int i = 0; i <= lastSubjectInFPhrase; i++) {
//				List<Integer> targetSpan = new ArrayList<Integer>();
//				Pair<Integer,Integer> subject = subjectSpans.get(i);
//				for(int j = subject.first; j <= subject.second; j++) {
//					int startOfTPhrase = f.f2tAlignmentIndex[j][f.PHRASE_START];
//					int endOfTPhrase = f.f2tAlignmentIndex[j][f.PHRASE_END];
//					for(int k = startOfTPhrase; k <= endOfTPhrase; k++)
//						targetSpan.add(k);
//        }
//				if(!isContiguous(targetSpan))
//					return new FeatureValue<String>(FEATURE_NAME, ARABIC_SUBJECT_PENALTY);
//			}
//			//See if each f subject span is translated to a contiguous span
//			//in the target alignment index
//				
//		}
//      
//	}

	public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {
		ArabicSubjectBank sb = ArabicSubjectBank.getInstance();
		sb.load(rFilePath,cFilePath);
		
		//WSGDEBUG Does toString() need a delimiter
		subjectSpans = sb.subjectsForSentence(foreign);
		if(subjectSpans == null)
			throw new RuntimeException("*!subjectfeaturizer: Null subject span for sentence");

    System.err.printf("%s: %d subjects for sentence\n%s\n", this.getClass().getName(), subjectSpans.size(),foreign.toString());
    
		noSubjects = (subjectSpans.size() == 0);
	}

	// Unused but required methods
	public List<FeatureValue<String>> listFeaturize(Featurizable f) { return null; }
	public void reset() {}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
