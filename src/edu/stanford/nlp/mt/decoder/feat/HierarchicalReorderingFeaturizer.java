package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ExtendedLexicalReorderingTable;
import edu.stanford.nlp.mt.base.ExtendedLexicalReorderingTable.ReorderingTypes;
import edu.stanford.nlp.mt.train.AlignmentGrid;
import edu.stanford.nlp.util.Index;

/**
 * Featurizer for a lexicalized re-ordering model that uses hierarchical
 * structure inferred in linear time.
 * 
 * @author Michel Galley
 * 
 * @see LexicalReorderingFeaturizer
 */
public class HierarchicalReorderingFeaturizer extends
    NeedsState<IString, String> implements
    RichCombinationFeaturizer<IString, String>, NeedsReorderingRecombination<IString, String>,
    NeedsCloneable<IString, String> {

  public static final String DEBUG_PROPERTY = "DebugHierarchicalReorderingFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugHierarchicalReorderingFeaturizer";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System
      .getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  public static class HierBlock {

    final int stackSz;
    final CoverageSet cs;
    final Featurizable<IString, String> previousF;

    HierBlock(CoverageSet cs, Featurizable<IString, String> previousF,
        int stackSz) {
      this.cs = cs;
      this.previousF = previousF;
      this.stackSz = stackSz;
    }

    public int fStart() {
      int i = cs.nextSetBit(0);
      assert (i >= 0);
      return i;
    }

    public int fEnd() {
      int i = cs.length() - 1;
      assert (i >= 0);
      return i;
    }

    public boolean isPrefix() {
      return cs.cardinality() == cs.length();
    }
  }

  // local: should produce the same as the non-hierarchical reordering model in
  // LexicalReorderingFeaturizer
  // hierarchical (default): as described in the 2008 EMNLP paper (runtime: O(1)
  // for each call of listFeaturize())
  // backtrack (experimental): backtracks to find max size contiguous block
  // preceding current block.
  // It was implemented to test the accuracy of hierarchical.
  // Runtime: O(n) for each call of listFeaturize(), where n is the len of the
  // input sentence. It is close to O(1) on average,
  // though sometimes gets stuck very long on some sentences with a lot of
  // reordering.
  // backtrackFast (experimental): faster version than backtrack, though its
  // predictions are currently incorrect in some
  // boundary conditions.
  // Note: hierarchical chokes on constructions that are not ITG binarizable,
  // e.g.,
  // ..x..
  // ...x.
  // x....
  // .x...
  // ....a
  // Here, 'a' should have monotone orientation, though hierarchical predicts
  // discontinuous. backtrack and backtrackFast
  // both predict monotone here.
  // Performance: hierarchical is yields slightly higher BLEU scores.
  // Performance: backtrackFast is the fastest. The two others are about 2-5%
  // slower.
  private enum ForwardOrientationComputation {
    local, hierarchical, backtrack, backtrackFast
  }

  private ForwardOrientationComputation forwardOrientationComputation = ForwardOrientationComputation.hierarchical;

  // local: should produce the same as the non-hierarchical reordering model in
  // LexicalReorderingFeaturizer
  // hierarchical: predict discontinuous only if we know for sure orientation
  // will be discontinuous
  // (specifically, if current block ranges fi...fj and next block ranges
  // fk...fl:
  // if j+1==k, predict Monotone (always correct)
  // if l+1==i, predict Swap (always correct)
  // if j+1<k, predict Monotone if f_{j+1}...f_{k-1} have not yet been
  // translated (possible error)
  // if l+1<i, predict Swap if f_{l+1}...f_{k-1} have not yet been translated
  // (possible error)
  // predict Discontinuous otherwise)
  private enum BackwardOrientationComputation {
    local, hierarchical
  }

  private BackwardOrientationComputation backwardOrientationComputation = BackwardOrientationComputation.hierarchical;

  String FEATURE_PREFIX = "LexR";
  String NB_FEATURE_PREFIX = "LexR:NB";

  // Extra features:
  boolean nbFeature = false, // penalty if not binarizable
      finalizeFeature = false, // compute backward model score on final phrase
      binarize = false; // if false, k-arize instead of binarize

  final boolean has2Disc, hasContainment;
  final String[] featureTags;
  final ExtendedLexicalReorderingTable mlrt;
  private BitSet tmpCoverage = new BitSet();

  public HierarchicalReorderingFeaturizer(String... args) throws IOException {

    if (args.length < 2 || args.length > 6)
      throw new RuntimeException(
          "Usage: HierarchicalReorderingFeaturizer(ordering_table_file,model_type,feature_prefix?,forward_model_type?,backward_model_type?,extra_features?)");

    String modelFilename = args[0];
    String modelType = args[1];
    has2Disc = modelType.contains("msd2");
    hasContainment = modelType.contains("msd2c");
    if (args.length >= 3) {
      FEATURE_PREFIX = args[2];
      if (args.length >= 4) {
        forwardOrientationComputation = ForwardOrientationComputation
            .valueOf(args[3]);
        if (args.length >= 5) {
          backwardOrientationComputation = BackwardOrientationComputation
              .valueOf(args[4]);
          if (args.length >= 6) {
            for (String opt : args[5].split(":")) {
              if ("nb".equals(opt)) {
                nbFeature = true;
              }
              if ("fin".equals(opt)) {
                finalizeFeature = true;
              }
              if ("bin".equals(opt)) {
                binarize = true;
              }
            }
          }
        }
      }
    }

    System.err.println("Hierarchical reordering model:");
    System.err.println("Distinguish between left and right discontinuous: "
        + has2Disc);
    System.err.println("Use containment orientation: " + hasContainment);
    System.err.printf("Forward orientation: %s\n",
        forwardOrientationComputation);
    System.err.printf("Backward orientation: %s\n",
        backwardOrientationComputation);

    mlrt = new ExtendedLexicalReorderingTable(modelFilename, modelType);

    featureTags = new String[mlrt.positionalMapping.length];
    for (int i = 0; i < mlrt.positionalMapping.length; i++)
      featureTags[i] = String.format("%s:%s", FEATURE_PREFIX,
          mlrt.positionalMapping[i]);
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {

    List<FeatureValue<String>> values = new LinkedList<FeatureValue<String>>();

    boolean locallyMonotone = f.linearDistortion == 0;
    boolean locallySwapping = (f.prior != null && f.derivation.rule.sourceCoverage
        .length() == f.prior.sourcePosition);
    boolean discont2 = (f.prior != null && fEnd(f) <= fStart(f.prior));

    float[] scores = mlrt
        .getReorderingScores(f.derivation.rule.abstractRule.id);
    float[] priorScores = (f.prior == null ? null : mlrt
        .getReorderingScores(f.prior.derivation.rule.abstractRule.id));

    ReorderingTypes forwardOrientation = ReorderingTypes.discontinuousWithPrevious, backwardOrientation = ReorderingTypes.discontinuousWithNext;

    if (DETAILED_DEBUG) {
      CoverageSet fCoverage = f.derivation.sourceCoverage;
      System.err.printf("----\n");
      System.err.printf("Partial translation (pos=%d): %s\n",
          f.targetPosition, f.targetPrefix);
      System.err.printf("Foreign sentence (pos=%d): %s\n", f.sourcePosition,
          f.sourceSentence);
      System.err.printf("Coverage: %s (size=%d)\n", fCoverage,
          fCoverage.length());
      System.err.printf("%s(%d) => %s(%d)\n", f.sourcePhrase,
          f.sourcePosition, f.targetPhrase, f.targetPosition);
      if (f.prior == null)
        System.err.printf("Prior <s> => <s>\n");
      else
        System.err.printf("Prior %s(%d) => %s(%d)\n", f.prior.sourcePhrase,
            f.prior.sourcePosition, f.prior.targetPhrase,
            f.prior.targetPosition);
      System.err.printf("Monotone: %s\nSwap: %s\n", locallyMonotone,
          locallySwapping);
      System.err.printf("PriorScores: %s\nScores: %s\n",
          (priorScores == null ? "null" : Arrays.toString(priorScores)),
          (scores == null ? "null" : Arrays.toString(scores)));
    }

    boolean containmentOrientation = false;
    if (hasContainment && f.prior != null) {
      CoverageSet prevCS = f.prior.derivation.rule.sourceCoverage;
      CoverageSet curCS = f.derivation.rule.sourceCoverage;
      containmentOrientation = CoverageSet.cross(prevCS, curCS);
    }

    // Determine forward orientation:
    {
      if (containmentOrientation) { // Containment:
        forwardOrientation = ReorderingTypes.containmentWithPrevious;
      } else { // MSD classes:
        boolean monotone = false, swap = false;
        switch (forwardOrientationComputation) {
        case hierarchical:
          if (monotoneWithPrevious(f))
            monotone = true;
          else if (swapWithPrevious(f))
            swap = true;
          break;
        case backtrack: // backtracks instead of using a stack; only for
                        // debugging
          if (backtrackForMonotoneWithPrevious(f))
            monotone = true;
          else if (backtrackForSwapWithPrevious(f))
            swap = true;
          break;
        case backtrackFast: // only for debugging
          if (backtrackForMonotoneWithPreviousFast(f))
            monotone = true;
          else if (backtrackForSwapWithPreviousFast(f))
            swap = true;
          break;
        case local:
          monotone = locallyMonotone;
          swap = locallySwapping;
          break;
        default:
          throw new RuntimeException(
              "HierarchicalReorderingFeaturizer: not yet implemented: "
                  + forwardOrientationComputation);
        }
        assert (!monotone || !swap);
        if (monotone)
          forwardOrientation = ReorderingTypes.monotoneWithPrevious;
        if (swap)
          forwardOrientation = ReorderingTypes.swapWithPrevious;
      }
    }

    // Determine backward orientation:
    {
      if (containmentOrientation) { // Containment:
        backwardOrientation = ReorderingTypes.containmentWithNext;
      } else { // MSD classes:
        boolean monotone = false, swap = false;
        switch (backwardOrientationComputation) {
        case local:
          monotone = locallyMonotone;
          swap = locallySwapping;
          break;
        case hierarchical:
          if (possiblyMonotoneWithNext(f))
            monotone = true;
          else if (possiblySwappingWithNext(f))
            swap = true;
          break;
        default:
          throw new RuntimeException(
              "HierarchicalReorderingFeaturizer: not yet implemented: "
                  + backwardOrientation);
        }
        assert (!monotone || !swap);
        if (monotone)
          backwardOrientation = ReorderingTypes.monotoneWithNext;
        if (swap)
          backwardOrientation = ReorderingTypes.swapWithNext;
      }
    }

    // Distinguish between forward and backward discontinuous:
    if (has2Disc && discont2) {
      if (forwardOrientation == ReorderingTypes.discontinuousWithPrevious)
        forwardOrientation = ReorderingTypes.discontinuous2WithPrevious;
      if (backwardOrientation == ReorderingTypes.discontinuousWithNext)
        backwardOrientation = ReorderingTypes.discontinuous2WithNext;
    }

    // Create feature functions:
    for (int i = 0; i < mlrt.positionalMapping.length; i++) {
      ReorderingTypes type = mlrt.positionalMapping[i];
      if (type == forwardOrientation || type == backwardOrientation) {
        if (!usePrior(mlrt.positionalMapping[i])) {
          boolean firstInDTU = f.getSegmentIdx() == 0;
          if (scores != null && firstInDTU) {
            values.add(new FeatureValue<String>(featureTags[i], scores[i]));
          }
        } else {
          boolean lastInDTU = (f.prior == null)
              || f.prior.getSegmentIdx() + 1 == f.prior.getSegmentNumber();
          if (priorScores != null && lastInDTU) {
            values
                .add(new FeatureValue<String>(featureTags[i], priorScores[i]));
          }
        }
      }
    }

    if (DETAILED_DEBUG) {
      System.err.printf("Feature values:\n");
      for (FeatureValue<String> value : values)
        System.err.printf("\t%s: %f\n", value.name, value.value);
    }

    if (forwardOrientationComputation == ForwardOrientationComputation.hierarchical) {
      // Update stack of hierarchical blocks:
      buildHierarchicalBlocks(f);

      // Determine if alignment grid is binarizable. If not, use stack size as a
      // feature:
      if (nbFeature) {
        HierBlock hb = (HierBlock) f.getState(this);
        HierBlock phb = (f.prior != null) ? (HierBlock) f.prior.getState(this)
            : null;
        int stackSzDelta = (phb != null) ? (hb.stackSz - phb.stackSz)
            : (hb.stackSz - 1);
        if (stackSzDelta != 0)
          values.add(new FeatureValue<String>(NB_FEATURE_PREFIX, -1.0
              * stackSzDelta));
      }
    }

    // Add backward model score on last phrase (missing/inconsistent in Moses):
    if (f.done && finalizeFeature) {
      int fEndPos = f.derivation.rule.sourceCoverage.length();
      int fLen = f.sourceSentence.size();
      assert (fEndPos <= fLen);
      ReorderingTypes finalBackwardOrientation = (fEndPos == fLen) ? ReorderingTypes.monotoneWithNext
          : ReorderingTypes.discontinuousWithNext;

      float[] finalScores = mlrt
          .getReorderingScores(f.derivation.rule.abstractRule.id);
      // Create feature functions:
      for (int i = 0; i < mlrt.positionalMapping.length; ++i) {
        ReorderingTypes type = mlrt.positionalMapping[i];
        if (type == finalBackwardOrientation) {
          if (usePrior(mlrt.positionalMapping[i])) {
            if (finalScores != null)
              values.add(new FeatureValue<String>(featureTags[i],
                  finalScores[i]));
          }
        }
      }
    }

    return values;
  }

  private static boolean usePrior(
      ExtendedLexicalReorderingTable.ReorderingTypes type) {
    switch (type) { // returns true if dealing with backward model:
    case monotoneWithNext:
    case swapWithNext:
    case discontinuousWithNext:
    case discontinuous2WithNext:
    case nonMonotoneWithNext:
    case containmentWithNext:
      return true;
    }
    return false;
  }

  private boolean isBinarizable(CoverageSet curBlockCS,
      CoverageSet prevBlockCS, CoverageSet fullCS) {
    int min1 = curBlockCS.nextSetBit(0);
    int max1 = curBlockCS.length();
    int min2 = prevBlockCS.nextSetBit(0);
    int max2 = prevBlockCS.length();

    // Check if there is a gap between curBlockCS and prevBlockCS. If so, return
    // false:
    // if (max2 < min1 || max1 < min2) return IsBinarizable.NO;

    int min = Math.min(min1, min2);
    int max = Math.max(max1, max2);

    for (int i = min + 1; i < max; ++i) {
      if (!curBlockCS.get(i) && !prevBlockCS.get(i))
        if (!fullCS.get(i))
          return false;
        else {
          if (binarize)
            return false;
        }
    }
    return true;
  }

  private void buildHierarchicalBlocks(Featurizable<IString, String> f) {

    CoverageSet curCS = new CoverageSet(f.rule.sourceCoverage.size());
    curCS.or(f.rule.sourceCoverage);
    Featurizable<IString, String> curF = f;
    boolean canMerge = true;

    while (canMerge) {
      HierBlock prevBlock = null;
      int merges = 0;

      if (curF.prior != null) {

        prevBlock = (HierBlock) curF.prior.getState(this);
        // Check if new Block should contain curBlock:
        if (isBinarizable(curCS, prevBlock.cs, f.derivation.sourceCoverage)) {
          if (DETAILED_DEBUG)
            System.err.printf(
                "HierarchicalReorderingFeaturizer: merged (%s) with (%s)\n",
                curCS, prevBlock.cs);
          curCS.or(prevBlock.cs);
          ++merges;
        } else {
          canMerge = false;
          if (DETAILED_DEBUG)
            System.err
                .printf(
                    "HierarchicalReorderingFeaturizer: can't merge (%s) with (%s)\n",
                    curCS, prevBlock.cs);
        }
      } else {
        canMerge = false;
      }

      if (canMerge) {
        curF = prevBlock.previousF;
      } else {
        HierBlock newBlock = new HierBlock(curCS, curF, prevBlock == null ? 1
            : (prevBlock.stackSz + 1 - merges));
        f.setState(this, newBlock);
        if (DETAILED_DEBUG)
          System.err.printf(
              "HierarchicalReorderingFeaturizer: new block (%s)\n",
              curCS.toString());
      }
    }
  }

  /**
   * Returns true if current phrase is monotone according to the hierarchical
   * model.
   */
  private boolean monotoneWithPrevious(Featurizable<IString, String> f) {
    if (f.prior == null)
      return f.linearDistortion == 0;
    HierBlock phb = (HierBlock) f.prior.getState(this);
    return phb.cs.isContiguous() && (phb.fEnd() + 1 == fStart(f));
  }

  /**
   * Returns true if current phrase is swap according to the hierarchical model.
   */
  private boolean swapWithPrevious(Featurizable<IString, String> f) {
    if (f.prior == null)
      return false;
    HierBlock phb = (HierBlock) f.prior.getState(this);
    return f.prior != null && phb.cs.isContiguous()
        && (phb.fStart() == fEnd(f) + 1);
  }

  /**
   * Returns true if current block (nextF.prior) is possibly monotone with next
   * (nextF). Specifically, if current block ranges fi...fj and next block
   * ranges fk...fl, return true if j+1 <= k and f_{j+1}...f_{k-1} have not yet
   * been translated. Return false otherwise.
   */
  private static boolean possiblyMonotoneWithNext(
      Featurizable<IString, String> nextF) {
    if (nextF.prior == null)
      return false;
    Featurizable<IString, String> currentF = nextF.prior;
    if (fStart(nextF) <= fEnd(currentF)) {
      if (fEnd(nextF) >= fStart(currentF)) {
        // May assert false with gappy phrases:
        // System.err.printf("range conflict : prevStr=%s prevCov=[%s,%s] prev=[%d-%d] curStr=%s curCov=[%s,%s] cur=[%d-%d]\n",
        // currentF.foreignPhrase, currentF.hyp.translationOpt.foreignCoverage,
        // currentF.hyp.foreignCoverage, fStart(currentF), fEnd(currentF),
        // nextF.foreignPhrase, nextF.hyp.translationOpt.foreignCoverage,
        // nextF.hyp.foreignCoverage, fStart(nextF), fEnd(nextF));
        // assert(false);
      }
      return false;
    }
    CoverageSet fCoverage = nextF.derivation.sourceCoverage;
    for (int i = fEnd(currentF) + 1; i < fStart(nextF); ++i)
      if (fCoverage.get(i))
        return false;
    // AlignmentGrid.printDecoderGrid(nextF,System.err);
    return true;
  }

  /**
   * Returns true if current block is possibly monotone with next. Specifically,
   * if current block ranges fi...fj and next block ranges fk...fl, return true
   * if l+1 <= i and f_{l+1}...f_{i-1} have not yet been translated. Return
   * false otherwise.
   */
  private static boolean possiblySwappingWithNext(
      Featurizable<IString, String> nextF) {
    if (nextF.prior == null)
      return false;
    CoverageSet fCoverage = nextF.derivation.sourceCoverage;
    Featurizable<IString, String> currentF = nextF.prior;
    if (fStart(currentF) <= fEnd(nextF)) {
      if (fEnd(currentF) >= fStart(nextF)) {
        // May assert false with gappy phrases:
        // System.err.printf("range conflict : prevStr=%s prevCov=[%s,%s] prev=[%d-%d] curStr=%s curCov=[%s,%s] cur=[%d-%d]\n",
        // currentF.foreignPhrase, currentF.hyp.translationOpt.foreignCoverage,
        // currentF.hyp.foreignCoverage, fStart(currentF), fEnd(currentF),
        // nextF.foreignPhrase, nextF.hyp.translationOpt.foreignCoverage,
        // nextF.hyp.foreignCoverage, fStart(nextF), fEnd(nextF));
        // assert(false);
      }
      return false;
    }
    for (int i = fEnd(nextF) + 1; i < fStart(currentF); ++i)
      if (fCoverage.get(i))
        return false;
    return true;
  }

  @Override
  public void dump(Featurizable<IString, String> f) {
    if (DEBUG) {
      assert (f.done);
      if (forwardOrientationComputation != ForwardOrientationComputation.hierarchical)
        return;
      System.err.printf(
          "Stack for hierarchical reordering (length of input sentence: %d)\n",
          f.sourceSentence.size());
      if (DEBUG && f.sourceSentence.size() < 20) {
        AlignmentGrid.printDecoderGrid(f, System.err);
        System.err.println();
      }
      Deque<String> lines = new LinkedList<String>();
      while (f != null) {
        HierBlock hb = (HierBlock) f.getState(this);
        lines.addFirst(String.format("cs=%s sz=%d (M,S)=(%d,%d) (M,S)=(%d,%d)",
            hb.cs, hb.stackSz, monotoneWithPrevious(f) ? 1 : 0,
                swapWithPrevious(f) ? 1 : 0, possiblyMonotoneWithNext(f) ? 1 : 0,
                    possiblySwappingWithNext(f) ? 1 : 0));
        f = f.prior;
      }
      int i = 0;
      for (String line : lines) {
        System.err.printf(" block[%d] %s\n", ++i, line);
      }
    }
  }

  private static int fStart(Featurizable<IString, String> f) {
    return f.sourcePosition;
  }

  private static int fEnd(Featurizable<IString, String> f) {
    return f.derivation.rule.sourceCoverage.length() - 1;
  }

  private static boolean contiguous(BitSet bs) {
    return (bs.nextSetBit(bs.nextClearBit(bs.nextSetBit(0))) < 0);
  }

  @Override
  public void rerankingMode(boolean reranking) {
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public NeedsCloneable<IString, String> clone()
      throws CloneNotSupportedException {
    HierarchicalReorderingFeaturizer featurizer = (HierarchicalReorderingFeaturizer) super
        .clone();
    featurizer.tmpCoverage = new BitSet();
    return featurizer;
  }

  // //////////////
  // Debug code //
  // //////////////

  /**
   * Returns true if all words that have been translated so far form a
   * contiguous block. Note that, if this condition is true, we can immediately
   * infer that the current phrase is globally monotone with what comes next.
   */
  static boolean isStronglyMonotone(Featurizable<IString, String> f) {
    CoverageSet fCoverage = f.derivation.sourceCoverage;
    return (fCoverage.length() - fCoverage.cardinality() == 0);
  }

  /**
   * Returns true if current block is monotone with any contiguous set of
   * previous blocks.
   */
  private boolean backtrackForMonotoneWithPrevious(
      Featurizable<IString, String> f) {

    // Five easy cases where M vs. not-M can be detected without backtracking:
    // Case 1: Handle case where fStart is the first input word, and/or there is
    // no prior phrase:
    int indexLeftCurrentPhrase = fStart(f) - 1; // if f_x...f_y is the current
                                                // phrase: f_here [ f_x ... f_y
                                                // ]
    if (indexLeftCurrentPhrase < 0) {
      return (f.prior == null); // start of the sentence => monotone
    } else if (f.prior == null) {
      return false;
    }

    // Case 2: If f.prior comes after f, return false:
    if (fStart(f) < fStart(f.prior))
      return false;

    // Case 3: If phrases are adjacent, return true:
    int indexRightPreviousPhrase = fEnd(f.prior) + 1; // if f_x...f_y is the
                                                      // previous phrase: [ f_x
                                                      // ... f_y ] f_here
    if (indexRightPreviousPhrase - 1 == indexLeftCurrentPhrase)
      return true;

    // Case 4: Analyze gap between previous and current phrase. If any
    // untranslated word, monotone is impossible.
    CoverageSet fCoverage = f.derivation.sourceCoverage;
    for (int i = indexRightPreviousPhrase; i <= indexLeftCurrentPhrase; ++i) {
      if (!fCoverage.get(i))
        return false;
    }

    // Case 5: If first uncovered word is after indexLeftCurrentPhrase, it must
    // be monotone:
    if (fCoverage.nextClearBit(0) > indexLeftCurrentPhrase
        && fCoverage.nextSetBit(fEnd(f) + 1) < 0)
      return true;

    // Otherwise, traverse previous blocks until we reach the one translating
    // indexLeftCurrentPhrase.
    // If any of these blocks lies after f in foreign side, we know it's not
    // monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    tmpCoverage.clear();
    boolean foundAdjPhrase = false;
    while (true) {
      if (fEnd(tmp_f) == indexLeftCurrentPhrase)
        foundAdjPhrase = true;
      int fStart = fStart(tmp_f);
      int fEnd = fEnd(tmp_f);
      tmpCoverage.set(fStart, fEnd + 1);
      if (foundAdjPhrase && contiguous(tmpCoverage))
        break;
      if (fStart > indexLeftCurrentPhrase)
        return false;
      tmp_f = tmp_f.prior;
      if (tmp_f == null)
        return false;
    }
    return true;
  }

  /**
   * Returns true if current block is swap with any contiguous set of previous
   * blocks.
   */
  private boolean backtrackForSwapWithPrevious(Featurizable<IString, String> f) {

    // Five easy cases where S vs. not-S can be detected without backtracking:
    // Case 1: If f.prior does not exist, can't be swapping:
    if (f.prior == null)
      return false;

    // Case 2: If f.prior comes before f, return false:
    if (fStart(f.prior) < fStart(f))
      return false;

    // Case 3: If phrases are adjacent, return true:
    int indexRightCurrentPhrase = fEnd(f) + 1;
    int indexLeftPreviousPhrase = fStart(f.prior) - 1; // if f_x...f_y is the
                                                       // previous phrase:
                                                       // f_here [ f_x ... f_y ]
    if (indexLeftPreviousPhrase + 1 == indexRightCurrentPhrase)
      return true;

    // Case 4: Analyze gap between previous and current phrase. If any
    // untranslated word, monotone is impossible.
    CoverageSet fCoverage = f.derivation.sourceCoverage;
    for (int i = indexRightCurrentPhrase; i <= indexLeftPreviousPhrase; ++i) {
      if (!fCoverage.get(i))
        return false;
    }

    // Otherwise, traverse previous blocks until we reach the one translating
    // indexRightCurrentPhrase.
    // If any of these blocks lies before f in foreign side, we know it's not
    // monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    tmpCoverage.clear();
    boolean foundAdjPhrase = false;
    while (true) {
      if (fStart(tmp_f) == indexRightCurrentPhrase)
        foundAdjPhrase = true;
      int fStart = fStart(tmp_f);
      int fEnd = fEnd(tmp_f);
      tmpCoverage.set(fStart, fEnd + 1);
      if (foundAdjPhrase && contiguous(tmpCoverage))
        break;
      if (fEnd < indexRightCurrentPhrase)
        return false;
      tmp_f = tmp_f.prior;
      if (tmp_f == null)
        return false;
    }
    return true;
  }

  /**
   * Returns true if current block is monotone with any contiguous set of
   * previous blocks. Result is incorrect in some boundary conditions, but it is
   * faster.
   */
  private static boolean backtrackForMonotoneWithPreviousFast(
      Featurizable<IString, String> f) {
    int indexPreviousForeign = fStart(f) - 1; // if f_x...f_y is the current
                                              // phrase: f_here [ f_x ... f_y ]
    if (indexPreviousForeign < 0) {
      return (f.prior == null); // start of the sentence => monotone
    } else if (f.prior == null) {
      return false;
    }

    // If previous foreign isn't yet translated, current block can't be monotone
    // with what comes before:
    CoverageSet fCoverage = f.derivation.sourceCoverage;
    if (!fCoverage.get(indexPreviousForeign)) {
      return false;
    }

    // Traverse previous blocks until we reach the one translating
    // indexPreviousForeign.
    // If any of these blocks lies after f in foreign side, we know it's not
    // monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    int indexLeftmostForeign = indexPreviousForeign;
    while (fEnd(tmp_f) != indexPreviousForeign) {
      int fStart = fStart(tmp_f);
      if (fStart > indexPreviousForeign) {
        return false;
      }
      if (fStart < indexLeftmostForeign)
        indexLeftmostForeign = fStart;
      tmp_f = tmp_f.prior;
      if (tmp_f == null)
        return false;
    }
    // Make sure all foreign words between indexLeftmostForeign and
    // indexPreviousForeign are translated:
    // (even if this loop does not return false, the result may still be
    // "false")
    for (int i = indexLeftmostForeign; i <= indexPreviousForeign; ++i) {
      if (!fCoverage.get(i))
        return false;
    }
    return true;
  }

  /**
   * Returns true if current block is swapping with any contiguous set of
   * blocks. Result is incorrect in some boundary conditions, but it is faster.
   */
  private static boolean backtrackForSwapWithPreviousFast(
      Featurizable<IString, String> f) {
    int indexNextForeign = fEnd(f) + 1;
    // If next foreign isn't yet translated, current block can't be swapping
    // with what comes next:
    CoverageSet fCoverage = f.derivation.sourceCoverage;
    if (!fCoverage.get(indexNextForeign))
      return false;
    // Traverse previous blocks until we reach the one translating
    // indexNextForeign.
    // If any of these blocks lies before f in foreign side, we know it's not
    // monotone:
    Featurizable<IString, String> tmp_f = f.prior;
    int indexRightmostForeign = indexNextForeign;
    while (fStart(tmp_f) != indexNextForeign) {
      int fEnd = fEnd(tmp_f);
      if (fEnd < indexNextForeign) {
        return false;
      }
      if (fEnd > indexRightmostForeign)
        indexRightmostForeign = fEnd;
      tmp_f = tmp_f.prior;
      if (tmp_f == null)
        return false;
    }
    // Check all foreign words between indexNextForeign and
    // indexRightmostForeign are translated:
    for (int i = indexNextForeign; i <= indexRightmostForeign; ++i) {
      if (!fCoverage.get(i))
        return false;
    }
    return true;
  }

}
