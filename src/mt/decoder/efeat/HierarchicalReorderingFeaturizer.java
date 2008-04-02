package mt.decoder.efeat;

import mt.*;
import java.util.*;
import java.io.*;

import edu.stanford.nlp.util.IntPair;
import mt.base.ARPALanguageModel;
import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IString;
import mt.base.MosesLexicalReorderingTable;
import mt.base.Sequence;
import mt.base.SimpleSequence;
import mt.base.MosesLexicalReorderingTable.ReorderingTypes;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.feat.LexicalReorderingFeaturizer;
import mt.train.AlignmentGrid;

/**
 * Featurizer for a lexicalized re-ordering model that uses hierarchical structure
 * inferred in linear time.
 * 
 * @author Michel Galley
 *
 * @see LexicalReorderingFeaturizer
 */
public class HierarchicalReorderingFeaturizer implements IncrementalFeaturizer<IString, String> {

  //public static int featurizerCall = 0;

  public static final String DEBUG_PROPERTY = "DebugHierarchicalReorderingFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugHierarchicalReorderingFeaturizer";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System.getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  class HierBlock {
    int fStart, fEnd;
    // featurizable objects identifying start and end of the HierBlock object:
    Featurizable<IString, String> start; 
    HierBlock(int fStart, int fEnd, Featurizable<IString, String> start)
    { this.fStart = fStart; this.fEnd = fEnd; this.start = start; }
  }

  // local: should produce the same as the non-hierarchical reordering model in LexicalReorderingFeaturizer
  // hierblock: as described in the ACL short paper submission (runtime: O(1) for each call of listFeaturize())
  // backtrack: backtracks to find max size contiguous block preceding current block 
  //    (runtime: O(n) for each call of listFeaturize(), where n is the len of the input sentence)
	private enum ForwardOrientationComputation { local, hierblock, backtrack };
  private ForwardOrientationComputation forwardOrientationComputation = ForwardOrientationComputation.hierblock;

  // local: should produce the same as the non-hierarchical reordering model in LexicalReorderingFeaturizer
  // contiuousDefault: predict discontinous only if we know for sure orientation will be discontinuous
  // (specifically, if current block ranges fi...fj and next block ranges fk...fl: 
  //    if j+1==k, predict Monotone (always correct)
  //    if l+1==i, predict Swap (always correct)
  //    if j+1<k, predict Monotone if f_{j+1}...f_{k-1} have not yet been translated (possible error)
  //    if l+1<i, predict Swap if f_{l+1}...f_{k-1} have not yet been translated (possible error)
  //    predict Discontinuous otherwise)
  // reranking: featurizer returns no forward-looking features until done decoding. Once done, compute
  // all feature values exactly.
	private enum BackwardOrientationComputation { local, contiuousDefault, reranking, continuousDefaultWithReranking };
  private BackwardOrientationComputation backwardOrientationComputation = BackwardOrientationComputation.local;

	public static final Sequence<IString> INITIAL_PHRASE = new SimpleSequence<IString>(ARPALanguageModel.START_TOKEN);

	static final String FEATURE_PREFIX = "HLexR:";

	final String[] featureTags;
	final MosesLexicalReorderingTable mlrt;
  final Map<Featurizable<IString, String>,HierBlock> hBlocks = new HashMap<Featurizable<IString, String>,HierBlock>();

	public HierarchicalReorderingFeaturizer(String... args) throws IOException {  
    if(args.length < 2 || args.length > 4)
      throw new RuntimeException("HierarchicalReorderingFeaturizer: args.length != 2");
    String modelFilename=args[0];
    String modelType=args[1];
    if(args.length >= 3) {
      forwardOrientationComputation = ForwardOrientationComputation.valueOf(args[2]);
      if(args.length == 4)
        backwardOrientationComputation = BackwardOrientationComputation.valueOf(args[3]);
    }
    System.err.printf("HierarchicalReorderingFeaturizer: forward orientation computation: %s.\n", forwardOrientationComputation);
    System.err.printf("HierarchicalReorderingFeaturizer: backward orientation computation: %s.\n", backwardOrientationComputation);
    mlrt = new MosesLexicalReorderingTable(modelFilename, modelType);
		featureTags = new String[mlrt.positionalMapping.length];
		for (int i = 0; i < mlrt.positionalMapping.length; i++) 
      featureTags[i] = String.format("%s:%s", FEATURE_PREFIX, mlrt.positionalMapping[i]);
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) { 
    System.err.println("HierarchicalReorderingFeaturizer: initialize.");
  } 

	public void reset() { 
    System.err.println("HierarchicalReorderingFeaturizer: reset.");
    hBlocks.clear();
  }

	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
		List<FeatureValue<String>> values = new LinkedList<FeatureValue<String>>();

    boolean locallyMonotone = f.linearDistortion == 0; 
    boolean locallySwapping = (f.prior == null ? false : f.foreignPosition + f.foreignPhrase.size() == f.prior.foreignPosition);

    double[] scores = mlrt.getReorderingScores(f.foreignPhrase, f.translatedPhrase);
    double[] priorScores = (f.prior == null ? null : mlrt.getReorderingScores(f.prior.foreignPhrase, f.prior.translatedPhrase));

    ReorderingTypes 
      forwardOrientation = ReorderingTypes.discontinousWithPrevious, 
      backwardOrientation = ReorderingTypes.discontinousWithNext; 
   
    if(DETAILED_DEBUG) {
      CoverageSet fCoverage = f.hyp.foreignCoverage;
      System.err.printf("----\n");
      System.err.printf("Partial translation (pos=%d): %s\n", f.translationPosition, f.partialTranslation);
      System.err.printf("Foreign sentence (pos=%d): %s\n", f.foreignPosition, f.foreignSentence);
      System.err.printf("Coverage: %s (size=%d)\n", fCoverage, fCoverage.length());
      System.err.printf("%s(%d) => %s(%d)\n", f.foreignPhrase, f.foreignPosition, f.translatedPhrase, f.translationPosition);
      if (f.prior == null) System.err.printf("Prior <s> => <s>\n");
      else System.err.printf("Prior %s(%d) => %s(%d)\n", 	f.prior.foreignPhrase, f.prior.foreignPosition, f.prior.translatedPhrase, f.prior.translationPosition);
      System.err.printf("Monotone: %s\nSwap: %s\n", locallyMonotone, locallySwapping);
      System.err.printf("PriorScores: %s\nScores: %s\n", (priorScores == null ? "null" : Arrays.toString(priorScores)), (scores == null ? "null" : Arrays.toString(scores)));			
    }
 
    // Determine forward orientation:
    {
      boolean monotone=false, swap=false;
      switch(forwardOrientationComputation) {
      case hierblock:
        if(monotoneWithPrevious(f)) monotone = true;
        else if(swapWithPrevious(f)) swap = true;
        break;
      case backtrack:
        if(backtrackForMonotoneWithPrevious(f)) monotone = true;
        else if(backtrackForSwapWithPrevious(f)) swap = true;
        break;
      case local:
        monotone = locallyMonotone;
        swap = locallySwapping;
        break;
      default: 
        throw new RuntimeException("HierarchicalReorderingFeaturizer: not yet implemented: "+forwardOrientationComputation);
      }
      assert(!monotone || !swap);
      if(monotone) forwardOrientation = ReorderingTypes.monotoneWithPrevious;
      if(swap) forwardOrientation = ReorderingTypes.swapWithPrevious;
    }

    // Determine backward orientation:
    {
      boolean monotone=false, swap=false;
      switch(backwardOrientationComputation) {
      case local:
        monotone = locallyMonotone;
        swap = locallySwapping;
        break;
      case contiuousDefault:
        if(possiblyMonotoneWithNext(f)) monotone = true;
        else if(possiblySwappingWithNext(f)) swap = true;
        break;
      default:
        throw new RuntimeException("HierarchicalReorderingFeaturizer: not yet implemented: "+backwardOrientation);
      }
      assert(!monotone || !swap);
      if(monotone) backwardOrientation = ReorderingTypes.monotoneWithNext;
      if(swap) backwardOrientation = ReorderingTypes.swapWithNext;
    }

    // Create feature functions:
    for (int i = 0; i < mlrt.positionalMapping.length; i++) {
      ReorderingTypes type = mlrt.positionalMapping[i];
      if(type == forwardOrientation || type == backwardOrientation) {
        if (!usePrior(mlrt.positionalMapping[i])) {
          if (scores != null) values.add(new FeatureValue<String>(featureTags[i], scores[i]));
        } else {
          if (priorScores != null) values.add(new FeatureValue<String>(featureTags[i], priorScores[i]));
        }
      }
    }

    if(DETAILED_DEBUG) {
      System.err.printf("Feature values:\n");
			for(FeatureValue<String> value : values) 
        System.err.printf("\t%s: %f\n", value.name, value.value); 
    }	
    if(DEBUG && f.done)
      AlignmentGrid.printDecoderGrid(f, System.err);
    if(forwardOrientationComputation == ForwardOrientationComputation.hierblock)
      buildHierarchicalBlocks(f);
		return values;
	}
	
	private boolean usePrior(MosesLexicalReorderingTable.ReorderingTypes type) {
		switch(type) { case monotoneWithNext: case swapWithNext: case discontinousWithNext: case nonMonotoneWithNext: return true; }
		return false;
	}

  private void buildHierarchicalBlocks(Featurizable<IString, String> f) {
    //++featurizerCall;
    int fStart = fStart(f), fEnd = fEnd(f);
    Featurizable<IString, String> curF = f;
    boolean canMerge = true; 
    while(canMerge) {
      //System.err.printf("HierarchicalReorderingFeaturizer: run%d [%d-%d]\n", featurizerCall, fStart, fEnd);
      HierBlock prevBlock = null;
      if(curF.prior != null) {
        prevBlock = hBlocks.get(curF.prior);
        // Check if new Block should contain curBlock:
        if(prevBlock.fEnd+1 == fStart) {
          if(DETAILED_DEBUG)
            System.err.printf("HierarchicalReorderingFeaturizer: grow left: [%d-%d] -> [%d-%d]\n",
              fStart,fEnd,prevBlock.fStart,fEnd);
          fStart = prevBlock.fStart;
        } else if(prevBlock.fStart == fEnd+1) {
          if(DETAILED_DEBUG)
            System.err.printf("HierarchicalReorderingFeaturizer: grow right: [%d-%d] -> [%d-%d]\n",
              fStart,fEnd,fStart,prevBlock.fEnd);
          fEnd = prevBlock.fEnd;
        } else {
          canMerge = false;
          if(DETAILED_DEBUG)
            System.err.printf("HierarchicalReorderingFeaturizer: can't merge [%d-%d] with [%d-%d]\n",
              fStart,fEnd,prevBlock.fStart,prevBlock.fEnd);
        }
      } else {
        canMerge = false;
      }
      if(canMerge) {
        curF = prevBlock.start;
      } else {
        HierBlock newBlock = new HierBlock(fStart,fEnd,curF);
        hBlocks.put(f,newBlock);
        if(DETAILED_DEBUG)
          System.err.printf("HierarchicalReorderingFeaturizer: new block [%d-%d]\n", fStart, fEnd);
      }
    }
  }

  /**
   * Returns true if current phrase is monotone according to the hierarchical model.
   */
  private boolean monotoneWithPrevious(Featurizable<IString, String> f) {
    if(f.prior == null)
      return f.linearDistortion == 0; 
    return (hBlocks.get(f.prior).fEnd+1 == fStart(f));
  }

  /**
   * Returns true if current phrase is swap according to the hierarchical model.
   */
  private boolean swapWithPrevious(Featurizable<IString, String> f) { 
    if(f.prior == null)
      return false;
    return (hBlocks.get(f.prior).fStart == fEnd(f)+1);
  }

  /**
   * Returns true if current block is possibly monotone with next. 
   * Specifically, if current block ranges fi...fj and next block ranges fk...fl, return true
   * if j+1 <= k and f_{j+1}...f_{k-1} have not yet been translated. Return false otherwise.
   */
  private static boolean possiblyMonotoneWithNext(Featurizable<IString, String> nextF) { 
    if(nextF.prior == null) return false;
    CoverageSet fCoverage = nextF.hyp.foreignCoverage;
    Featurizable<IString, String> currentF = nextF.prior;
    if(fStart(nextF) <= fEnd(currentF)) {
      if(fEnd(nextF) >= fStart(currentF)) {
        System.err.printf("range conflict: prev=[%d-%d] cur=[%d-%d]\n",
        fStart(currentF),fEnd(currentF), fStart(nextF),fEnd(nextF));
        assert(false);
      }
      return false;
    }
    for(int i = fEnd(currentF)+1; i<fStart(nextF); ++i)
      if(fCoverage.get(i))
        return false;
    return true;
  }

  /**
   * Returns true if current block is possibly monotone with next. 
   * Specifically, if current block ranges fi...fj and next block ranges fk...fl, return true
   * if l+1 <= i and f_{l+1}...f_{i-1} have not yet been translated. Return false otherwise.
   */
  private static boolean possiblySwappingWithNext(Featurizable<IString, String> nextF) {
    if(nextF.prior == null) return false;
    CoverageSet fCoverage = nextF.hyp.foreignCoverage;
    Featurizable<IString, String> currentF = nextF.prior;
    if(fStart(currentF) <= fEnd(nextF)) {
      if(fEnd(currentF) >= fStart(nextF)) {
        System.err.printf("range conflict: prev=[%d-%d] cur=[%d-%d]\n",
        fStart(currentF),fEnd(currentF), fStart(nextF),fEnd(nextF));
        assert(false);
      }
      return false;
    }
    for(int i = fEnd(nextF)+1; i<fStart(currentF); ++i)
      if(fCoverage.get(i))
        return false;
    return true;
  }

  /**
   * Returns true if current block is monotone with any contiguous set
   * of previous blocks. This is a slow version that steps back to check 
   */
  private static boolean backtrackForMonotoneWithPrevious(Featurizable<IString, String> f) {
    int indexPreviousForeign = fStart(f)-1;
    if(indexPreviousForeign < 0) {
      boolean monotone = (f.prior == null);
      return monotone;
    } else if(f.prior == null) {
      return false;
    }
    // If previous foreign isn't yet translated, current block can't be monontone
    // with what comes before:
    CoverageSet fCoverage = f.hyp.foreignCoverage;
    if(!fCoverage.get(indexPreviousForeign)) {
      return false;
    }
    // Traverse previous blocks until we reach the one translating indexPreviousForeign.
    // If any of these blocks lies after f in foreign side, we know it's not monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    int indexLeftmostForeign = indexPreviousForeign;
    int step = 0;
    while(fEnd(tmp_f) != indexPreviousForeign) {
      int fStart = fStart(tmp_f);
      if(fStart > indexPreviousForeign) {
        assert(fEnd(f) < fStart);
        return false;
      }
      if(fStart < indexLeftmostForeign)
        indexLeftmostForeign = fStart;
      tmp_f = tmp_f.prior;
      assert(tmp_f != null);
    }
    // Make sure all foreign words between indexLeftmostForeign and indexPreviousForeign are translated:
    for(int i=indexLeftmostForeign; i<=indexPreviousForeign; ++i) {
      if(!fCoverage.get(i))
        return false;
    }
    return true;
	}

  /**
   * Returns true if current block is swapping with any contiguous set of blocks.
   */
  private static boolean backtrackForSwapWithPrevious(Featurizable<IString, String> f) {
    int indexNextForeign = fEnd(f)+1;
    // If previous foreign isn't yet translated, current block can't be swapping
    // with what comes next:
    CoverageSet fCoverage = f.hyp.foreignCoverage;
    if(!fCoverage.get(indexNextForeign))
      return false;
    // Traverse previous blocks until we reach the one translating indexNextForeign.
    // If any of these blocks lies before f in foreign side, we know it's not monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    int indexRightmostForeign = indexNextForeign;
    int step = 0;
    while(fStart(tmp_f) != indexNextForeign) {
      int fEnd = fEnd(tmp_f);
      if(fEnd < indexNextForeign) {
        assert(fStart(f) > fEnd);
        return false;
      }
      if(fEnd > indexRightmostForeign)
        indexRightmostForeign = fEnd;
      tmp_f = tmp_f.prior;
      assert(tmp_f != null);
    }
    // Check all foreign words between indexNextForeign and indexRightmostForeign are translated:
    for(int i=indexNextForeign; i<=indexRightmostForeign; ++i) {
      if(!fCoverage.get(i))
        return false;
    }
    return true;
	}

  /**
   * Returns true if all words that have been translated so far form a contiguous block.
   * Note that, if this condition is true, we can immediately infer that the current phrase
   * is globally monotone with what comes next.
   */
  private static boolean isStronglyMonotone(Featurizable<IString, String> f) {
    CoverageSet fCoverage = f.hyp.foreignCoverage;
    return (fCoverage.length()-fCoverage.cardinality() == 0);
  }

  private static int fStart(Featurizable<IString, String> f) { return f.foreignPosition; }
  private static int fEnd(Featurizable<IString, String> f) { return f.foreignPosition+f.foreignPhrase.size()-1; }
  private static int eStart(Featurizable<IString, String> f) { return f.translationPosition; }
  private static int eEnd(Featurizable<IString, String> f) { return f.translationPosition+f.foreignPhrase.size()-1; }

}
