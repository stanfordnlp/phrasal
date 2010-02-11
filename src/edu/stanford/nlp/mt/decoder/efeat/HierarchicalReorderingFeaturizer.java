package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.ExtendedLexicalReorderingTable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ExtendedLexicalReorderingTable.ReorderingTypes;
import edu.stanford.nlp.mt.decoder.feat.*;
import edu.stanford.nlp.mt.train.AlignmentGrid;


/**
 * Featurizer for a lexicalized re-ordering model that uses hierarchical structure
 * inferred in linear time.
 * 
 * @author Michel Galley
 *
 * @see LexicalReorderingFeaturizer
 */
@SuppressWarnings("unused")
public class HierarchicalReorderingFeaturizer extends StatefulFeaturizer<IString,String> implements RichIncrementalFeaturizer<IString, String>, MSDFeaturizer, ClonedFeaturizer<IString,String> {

  public static final String DEBUG_PROPERTY = "DebugHierarchicalReorderingFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugHierarchicalReorderingFeaturizer";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System.getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  class HierBlock {
    int fStart, fEnd;
    // featurizable objects identifying start location:
    Featurizable<IString, String> start; 
    HierBlock(int fStart, int fEnd, Featurizable<IString, String> start)
    { this.fStart = fStart; this.fEnd = fEnd; this.start = start; }
  }

  // local: should produce the same as the non-hierarchical reordering model in LexicalReorderingFeaturizer
  // hierblock (default): as described in the 2008 EMNLP paper (runtime: O(1) for each call of listFeaturize())
  // backtrack (experimental): backtracks to find max size contiguous block preceding current block.
  //   It was implemented to test the accuracy of hierblock.
  //   Runtime: O(n) for each call of listFeaturize(), where n is the len of the input sentence. It is close to O(1) on average,
  //   though sometimes gets stuck very long on some sentences with a lot of reordering.
  // backtrackFast (experimental): faster version than backtrack, though its predictions are currently incorrect in some
  //   boundary conditions.
  // Note: hierblock chokes on constructions that are not ITG binarizable, e.g.,
  //    ..x..
  //    ...x.
  //    x....
  //    .x...
  //    ....a
  // Here, 'a' should have monotone orientation, though hierblock predicts discontinuous. backtrack and backtrackFast
  // both predict monotone here.
  // Performance: hierblock is yields slightly higher BLEU scores.
  // Performance: backtrackFast is the fastest. The two others are about 2-5% slower.
	private enum ForwardOrientationComputation { local, hierblock, backtrack, backtrackFast }
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
	private enum BackwardOrientationComputation { local, continuousDefault }
  private BackwardOrientationComputation backwardOrientationComputation = BackwardOrientationComputation.local;

	String FEATURE_PREFIX = "HLexR";

  final boolean has2Disc;
	final String[] featureTags;
	final ExtendedLexicalReorderingTable mlrt;
  private BitSet tmpCoverage = new BitSet();

	public HierarchicalReorderingFeaturizer(String... args) throws IOException {  
    if(args.length < 2 || args.length > 5)
      throw new RuntimeException
        ("Usage: HierarchicalReorderingFeaturizer(ordering_table_file,model_type,forward_model_type?,backward_model_type?,feature_prefix?)");
    String modelFilename=args[0];
    String modelType=args[1];
    has2Disc = modelType.indexOf("msd2") >= 0;
    System.err.println("Distinguishes between left and right discontinuous: "+has2Disc);
    if(args.length >= 3) {
      forwardOrientationComputation = ForwardOrientationComputation.valueOf(args[2]);
      if(args.length >= 4) {
        backwardOrientationComputation = BackwardOrientationComputation.valueOf(args[3]);
        if(args.length == 5)
          FEATURE_PREFIX = args[4];
      }
    }
    System.err.printf("HierarchicalReorderingFeaturizer: forward orientation computation: %s.\n", forwardOrientationComputation);
    System.err.printf("HierarchicalReorderingFeaturizer: backward orientation computation: %s.\n", backwardOrientationComputation);
    mlrt = new ExtendedLexicalReorderingTable(modelFilename, modelType);
		featureTags = new String[mlrt.positionalMapping.length];
		for (int i = 0; i < mlrt.positionalMapping.length; i++) 
      featureTags[i] = String.format("%s:%s", FEATURE_PREFIX, mlrt.positionalMapping[i]);
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    
		List<FeatureValue<String>> values = new LinkedList<FeatureValue<String>>();

    boolean locallyMonotone = f.linearDistortion == 0;
    boolean locallySwapping = (f.prior != null && f.hyp.translationOpt.foreignCoverage.length() == f.prior.foreignPosition);
    boolean discont2 = (f.prior != null && fEnd(f) <= fStart(f.prior));

    float[] scores = mlrt.getReorderingScores(f.foreignPhrase, f.translatedPhrase);
    float[] priorScores = (f.prior == null ? null : mlrt.getReorderingScores(f.prior.foreignPhrase, f.prior.translatedPhrase));

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
      case backtrackFast:
        if(backtrackForMonotoneWithPreviousFast(f)) monotone = true;
        else if(backtrackForSwapWithPreviousFast(f)) swap = true;
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
      case continuousDefault:
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

    // Distinguish between forward and backward discontinuous:
    if(has2Disc && discont2) {
      if(forwardOrientation == ReorderingTypes.discontinousWithPrevious) 
         forwardOrientation = ReorderingTypes.discontinous2WithPrevious;
      if(backwardOrientation == ReorderingTypes.discontinousWithNext) 
         backwardOrientation = ReorderingTypes.discontinous2WithNext;
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
    if(forwardOrientationComputation == ForwardOrientationComputation.hierblock)
      buildHierarchicalBlocks(f);

    if (f.done) {

      boolean incomplete = false;
      if (forwardOrientationComputation == ForwardOrientationComputation.hierblock) {
        HierBlock hb = (HierBlock) f.getState(this);
        incomplete = (hb.fStart != 0 || hb.fEnd+1 != f.foreignSentence.size());
      }

      if(DEBUG) {
        AlignmentGrid.printDecoderGrid(f, System.err);
        if(incomplete) dump(f);
        System.err.println("\n");
      }
    }
		return values;
	}
	
  private boolean usePrior(ExtendedLexicalReorderingTable.ReorderingTypes type) {
    switch(type) {  // returns true if dealing with backward model:
      case monotoneWithNext: 
      case swapWithNext: 
      case discontinousWithNext: 
      case discontinous2WithNext: 
      case nonMonotoneWithNext:
      case fromEnd:
      case toEnd:
      return true; }
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
        prevBlock = (HierBlock) curF.prior.getState(this);
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
        f.setState(this,newBlock);
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
    return (((HierBlock)f.prior.getState(this)).fEnd+1 == fStart(f));
  }

  /**
   * Returns true if current phrase is swap according to the hierarchical model.
   */
  private boolean swapWithPrevious(Featurizable<IString, String> f) {
    return f.prior != null && (((HierBlock)f.prior.getState(this)).fStart == fEnd(f) + 1);
    //return f.prior != null && (hBlocks.get(f.prior).fStart == fEnd(f) + 1);
  }

  /**
   * Returns true if current block is possibly monotone with next. 
   * Specifically, if current block ranges fi...fj and next block ranges fk...fl, return true
   * if j+1 <= k and f_{j+1}...f_{k-1} have not yet been translated. Return false otherwise.
   */
  private static boolean possiblyMonotoneWithNext(Featurizable<IString, String> nextF) { 
    if(nextF.prior == null) return false;
    Featurizable<IString, String> currentF = nextF.prior;
    if(fStart(nextF) <= fEnd(currentF)) {
      if(fEnd(nextF) >= fStart(currentF)) {
        // May assert false with gappy phrases:
        //System.err.printf("range conflict : prevStr=%s prevCov=[%s,%s] prev=[%d-%d] curStr=%s curCov=[%s,%s] cur=[%d-%d]\n",
        //     currentF.foreignPhrase, currentF.hyp.translationOpt.foreignCoverage, currentF.hyp.foreignCoverage, fStart(currentF), fEnd(currentF),
        //     nextF.foreignPhrase, nextF.hyp.translationOpt.foreignCoverage, nextF.hyp.foreignCoverage, fStart(nextF), fEnd(nextF));
        //assert(false);
      }
      return false;
    }
    CoverageSet fCoverage = nextF.hyp.foreignCoverage;
    for(int i = fEnd(currentF)+1; i<fStart(nextF); ++i)
      if(fCoverage.get(i))
        return false;
    //AlignmentGrid.printDecoderGrid(nextF,System.err);
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
        // May assert false with gappy phrases:
        //System.err.printf("range conflict : prevStr=%s prevCov=[%s,%s] prev=[%d-%d] curStr=%s curCov=[%s,%s] cur=[%d-%d]\n",
        //     currentF.foreignPhrase, currentF.hyp.translationOpt.foreignCoverage, currentF.hyp.foreignCoverage, fStart(currentF), fEnd(currentF),
        //     nextF.foreignPhrase, nextF.hyp.translationOpt.foreignCoverage, nextF.hyp.foreignCoverage, fStart(nextF), fEnd(nextF));
        //assert(false);
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
   * of previous blocks.
   */
  private boolean backtrackForMonotoneWithPrevious(Featurizable<IString, String> f) {
    // Five easy cases where M vs. not-M can be detected without backtracking:
    // Case 1: Handle case where fStart is the first input word, and/or there is no prior phrase:
    int indexLeftCurrentPhrase = fStart(f)-1; // if f_x...f_y is the current phrase: f_here [ f_x ... f_y ]
    if(indexLeftCurrentPhrase < 0) {
      return (f.prior == null); // start of the sentence => monotone
    } else if(f.prior == null) {
      return false;
    }
    // Case 2: If f.prior comes after f, return false:
    if (fStart(f) < fStart(f.prior)) return false;
    // Case 3: If phrases are adjacent, return true:
    int indexRightPreviousPhrase = fEnd(f.prior)+1; // if f_x...f_y is the previous phrase: [ f_x ... f_y ] f_here
    if (indexRightPreviousPhrase-1 == indexLeftCurrentPhrase) return true;
    // Case 4: Analyze gap between previous and current phrase. If any untranslated word, monotone is impossible.
    CoverageSet fCoverage = f.hyp.foreignCoverage;
    for (int i=indexRightPreviousPhrase; i<=indexLeftCurrentPhrase; ++i) {
      if(!fCoverage.get(i))
        return false;
    }
    // Case 5: If first uncovered word is after indexLeftCurrentPhrase, it must be monotone:
    if (fCoverage.nextClearBit(0) > indexLeftCurrentPhrase &&
        fCoverage.nextSetBit(fEnd(f)+1) < 0) return true;
    // Otherwise, traverse previous blocks until we reach the one translating indexLeftCurrentPhrase.
    // If any of these blocks lies after f in foreign side, we know it's not monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    tmpCoverage.clear();
    boolean foundAdjPhrase = false;
    for(;;) {
      if (fEnd(tmp_f) == indexLeftCurrentPhrase)
        foundAdjPhrase = true;
      int fStart = fStart(tmp_f);
      int fEnd = fEnd(tmp_f);
      tmpCoverage.set(fStart,fEnd+1);
      if (foundAdjPhrase && contiguous(tmpCoverage))
        break;
      if(fStart > indexLeftCurrentPhrase)
        return false;
      tmp_f = tmp_f.prior;
      if(tmp_f == null) return false;
    }
    return true;
	}

  /**
   * Returns true if current block is swap with any contiguous set
   * of previous blocks.
   */
  private boolean backtrackForSwapWithPrevious(Featurizable<IString, String> f) {
    // Five easy cases where S vs. not-S can be detected without backtracking:
    // Case 1: If f.prior does not exist, can't be swapping:
    if(f.prior == null)
      return false;
    // Case 2: If f.prior comes before f, return false:
    if (fStart(f.prior) < fStart(f)) return false;
    // Case 3: If phrases are adjacent, return true:
    int indexRightCurrentPhrase = fEnd(f)+1;
    int indexLeftPreviousPhrase = fStart(f.prior)-1; // if f_x...f_y is the previous phrase: f_here [ f_x ... f_y ]
    if (indexLeftPreviousPhrase+1 == indexRightCurrentPhrase) return true;
    // Case 4: Analyze gap between previous and current phrase. If any untranslated word, monotone is impossible.
    CoverageSet fCoverage = f.hyp.foreignCoverage;
    for (int i=indexRightCurrentPhrase; i<=indexLeftPreviousPhrase; ++i) {
      if(!fCoverage.get(i))
        return false;
    }
    // Otherwise, traverse previous blocks until we reach the one translating indexRightCurrentPhrase.
    // If any of these blocks lies before f in foreign side, we know it's not monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    tmpCoverage.clear();
    boolean foundAdjPhrase = false;
    for(;;) {
      if(fStart(tmp_f) == indexRightCurrentPhrase)
        foundAdjPhrase = true;
      int fStart = fStart(tmp_f);
      int fEnd = fEnd(tmp_f);
      tmpCoverage.set(fStart,fEnd+1);
      if (foundAdjPhrase && contiguous(tmpCoverage))
        break;
      if(fEnd < indexRightCurrentPhrase)
        return false;
      tmp_f = tmp_f.prior;
      if(tmp_f == null) return false;
    }
    return true;
	}

  /**
   * Returns true if current block is monotone with any contiguous set
   * of previous blocks. Result is incorrect in some boundary conditions, but it is faster.
   */
  private static boolean backtrackForMonotoneWithPreviousFast(Featurizable<IString, String> f) {
    int indexPreviousForeign = fStart(f)-1; // if f_x...f_y is the current phrase: f_here [ f_x ... f_y ]
    if(indexPreviousForeign < 0) {
      return (f.prior == null); // start of the sentence => monotone
    } else if(f.prior == null) {
      return false;
    }
    // If previous foreign isn't yet translated, current block can't be monotone
    // with what comes before:
    CoverageSet fCoverage = f.hyp.foreignCoverage;
    if(!fCoverage.get(indexPreviousForeign)) {
      return false;
    }
    // Traverse previous blocks until we reach the one translating indexPreviousForeign.
    // If any of these blocks lies after f in foreign side, we know it's not monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    int indexLeftmostForeign = indexPreviousForeign;
    while(fEnd(tmp_f) != indexPreviousForeign) {
      int fStart = fStart(tmp_f);
      if(fStart > indexPreviousForeign) {
        //Asserts false with source gaps: TODO: figure out why
        //assert(fEnd(f) < fStart);
        return false;
      }
      if(fStart < indexLeftmostForeign)
        indexLeftmostForeign = fStart;
      tmp_f = tmp_f.prior;
      //Asserts false with source gaps: TODO: figure out why
      //assert(tmp_f != null);
      if(tmp_f == null) return false;
    }
    // Make sure all foreign words between indexLeftmostForeign and indexPreviousForeign are translated:
    // (even if this loop does not return false, the result may still be "false")
    for(int i=indexLeftmostForeign; i<=indexPreviousForeign; ++i) {
      if(!fCoverage.get(i))
        return false;
    }
    return true;
	}

  /**
   * Returns true if current block is swapping with any contiguous set of blocks.
   * Result is incorrect in some boundary conditions, but it is faster.
   */
  private static boolean backtrackForSwapWithPreviousFast(Featurizable<IString, String> f) {
    int indexNextForeign = fEnd(f)+1;
    // If next foreign isn't yet translated, current block can't be swapping
    // with what comes next:
    CoverageSet fCoverage = f.hyp.foreignCoverage;
    if(!fCoverage.get(indexNextForeign))
      return false;
    // Traverse previous blocks until we reach the one translating indexNextForeign.
    // If any of these blocks lies before f in foreign side, we know it's not monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    int indexRightmostForeign = indexNextForeign;
    while(fStart(tmp_f) != indexNextForeign) {
      int fEnd = fEnd(tmp_f);
      if(fEnd < indexNextForeign) {
        //Asserts false with source gaps: TODO: figure out why
        //assert(fStart(f) > fEnd);
        return false;
      }
      if(fEnd > indexRightmostForeign)
        indexRightmostForeign = fEnd;
      tmp_f = tmp_f.prior;
      //Asserts false with source gaps: TODO: figure out why
      //assert(tmp_f != null);
      if(tmp_f == null) return false;
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
  @SuppressWarnings("unused")
  static boolean isStronglyMonotone(Featurizable<IString, String> f) {
    CoverageSet fCoverage = f.hyp.foreignCoverage;
    return (fCoverage.length()-fCoverage.cardinality() == 0);
  }

  public void dump(Featurizable<IString, String> f) {
    if (forwardOrientationComputation != ForwardOrientationComputation.hierblock)
      return;
    System.err.println("input length: "+f.foreignSentence.size());
    Deque<String> lines = new LinkedList<String>();
    while (f != null) {
      HierBlock hb = (HierBlock) f.getState(this);
      lines.addFirst(String.format("%d-%d M=%s S=%s",
        hb.fStart, hb.fEnd, monotoneWithPrevious(f), swapWithPrevious(f)));
      f = f.prior;
    }
    int i=0;
    for (String line : lines)
      System.err.printf(" block[%d] %s\n",++i,line);
  }

  private static int fStart(Featurizable<IString, String> f) { return f.foreignPosition; }
  private static int fEnd(Featurizable<IString, String> f) { return f.hyp.translationOpt.foreignCoverage.length()-1; }

  private static boolean contiguous(BitSet bs) { return (bs.nextSetBit(bs.nextClearBit(bs.nextSetBit(0))) < 0); }

  public void rerankingMode(boolean reranking) {}

  @Override
	public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {}

	public void reset() {}

	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }


  public ClonedFeaturizer<IString,String> clone() throws CloneNotSupportedException {
    HierarchicalReorderingFeaturizer featurizer = (HierarchicalReorderingFeaturizer)super.clone();
    featurizer.tmpCoverage = new BitSet();
    return featurizer;
  }
}
