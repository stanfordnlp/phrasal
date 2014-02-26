package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.util.Pair;

/**
 * Hypothesis with words that still need to be added to the partial translation
 * (aka, "pending phrases").
 * 
 * @author Michel Galley
 */
public class DTUHypothesis<TK, FV> extends Derivation<TK, FV> {

  private static final String MIN_GAP_SIZE_PROPERTY = "minTargetGapSize";
  private static final int MIN_GAP_SIZE = Integer.parseInt(System.getProperty(
      MIN_GAP_SIZE_PROPERTY, "1"));
  static {
    System.err.println("Minimum target gap size: " + MIN_GAP_SIZE);
  }

  private static final double EXPIRATION_PENALTY = 11111.11; // When a
                                                             // DTUHypothesis
                                                             // expires, it
                                                             // suffers this cost
  private static int MAX_TARGET_PHRASE_SPAN = -1; // to make sure it is
                                                  // overridden (an assert will
                                                  // fail otherwise)
  private static int MAX_PENDING_PHRASES = Integer.parseInt(System.getProperty("maxPendingPhrases","2"));

  /**
   * This class represents a phrase with one or more discontinuities in its
   * target side. It holds this information: abstract translation option is used
   * to generate the discontinuous phrase, and index of the current contiguous
   * segment.
   */
  public static class PendingPhrase<TK, FV> implements
      Comparable<PendingPhrase<TK, FV>> {

    public final ConcreteRule<TK,FV> concreteOpt;

    public int segmentIdx; // Current segment of the translation option.
                           // For instance, segmentIdx=0 selects "ne" and
                           // segmentIdx=1 selects "pas"
    private int firstPosition; // the discontinuous phrases
                               // segmentIdx+1,segmentIdx+2,etc.
    private final int lastPosition; // must be generated within the range
                                    // [firstPosition,lastPosition] of target
                                    // words.

    private final double[] futureCosts; // future cost associated with each
                                        // segment

    // Copy constructor:
    public PendingPhrase(PendingPhrase<TK, FV> old) {
      this.concreteOpt = old.concreteOpt;
      this.segmentIdx = old.segmentIdx;
      this.firstPosition = old.firstPosition;
      this.lastPosition = old.lastPosition;
      this.futureCosts = old.futureCosts;
    }

    public PendingPhrase(ConcreteRule<TK,FV> concreteOpt,
        int sourceInputId, Derivation<TK, FV> hyp,
        CombinedFeaturizer<TK, FV> featurizer, Scorer<FV> scorer,
        int segmentIdx, int firstPosition, int lastPosition) {
      this.segmentIdx = segmentIdx;
      assert (segmentIdx == 0);
      this.firstPosition = firstPosition;
      this.lastPosition = lastPosition;
      this.concreteOpt = concreteOpt;
      this.futureCosts = setFutureCosts(sourceInputId, hyp, featurizer, scorer);
    }

    private static final ThreadLocal<MutableInteger> tlTranslationId = new ThreadLocal<MutableInteger>() {
      @Override
      protected MutableInteger initialValue() {
        return new MutableInteger();
      }
    };

    private static class SegId<TK> extends Pair<DTURule<TK>, Integer> {
      private static final long serialVersionUID = 1L;

      SegId(DTURule<TK> o, Integer i) {
        super(o, i);
      }
    }

    @SuppressWarnings("rawtypes")
    private static final ThreadLocal<Map<SegId, Double>> tlCache = new ThreadLocal<Map<SegId, Double>>() {
      @Override
      protected Map<SegId, Double> initialValue() {
        return new HashMap<SegId, Double>();
      }
    };

    private double[] setFutureCosts(int sourceInputId, Derivation<TK, FV> hyp,
        CombinedFeaturizer<TK, FV> featurizer, Scorer<FV> scorer) {

      // Do we clear the cache of future cost?
      MutableInteger lastId = tlTranslationId.get();
      @SuppressWarnings("rawtypes")
      Map<SegId, Double> fcCache = tlCache.get();
      if (lastId.intValue() != sourceInputId) {
        fcCache.clear();
        lastId.set(sourceInputId);
      }

      DTURule<TK> opt = (DTURule<TK>) concreteOpt.abstractRule;
      double[] fc = new double[opt.dtus.length];

      assert (segmentIdx == 0);
      for (int i = segmentIdx + 1; i < opt.dtus.length; ++i) {
        SegId<TK> id = new SegId<TK>(opt, i);
        Double score = fcCache.get(id);
        if (score == null) {
          Featurizable<TK, FV> f = new DTUFeaturizable<TK, FV>(
              hyp.sourceSequence, hyp.sourceInputProperties, concreteOpt, sourceInputId, i);
          List<FeatureValue<FV>> phraseFeatures = featurizer
              .ruleFeaturize(f);
          score = scorer.getIncrementalScore(phraseFeatures);
          fcCache.put(id, score);
        }
        fc[i] = score;
        // System.err.printf("Future cost: id=%d phrase={%s} features=%s fc=%.3f\n",
        // translationId, opt.dtus[i], phraseFeatures, fc[i]);
      }
      return fc;
    }

    // Need PendingPhrase instances to be sorted for recombination:
    @Override
    public int compareTo(PendingPhrase<TK, FV> o) {
      int sig = Integer.signum(this.segmentIdx - o.segmentIdx);
      if (this.concreteOpt == o.concreteOpt)
        return sig;
      int h1 = System.identityHashCode(concreteOpt);
      int h2 = System.identityHashCode(o.concreteOpt);
      if (h1 == h2)
        // This case (i.e., concreteOpt != o.concreteOpt && h1 == h2) can happen
        // with a 64-bit JVM, though it is very unlikely.
        return sig;
      return Integer.signum(h1 - h2);
    }
  }

  public final Set<PendingPhrase<TK, FV>> pendingPhrases; // discontinuous
                                                          // phrases that are
                                                          // still "pending"

  private final int segmentIdx;
  private final double pendingPhrasesCost; // future cost estimation, which
                                           // currently only accounts for the LM
                                           // score of "pending" phrases

  private boolean hasExpired = false;

  public static void setMaxTargetPhraseSpan(int m) {
    System.err.println("Setting new maximum target phrase span: " + m);
    MAX_TARGET_PHRASE_SPAN = m;
  }

  public static int getMaxTargetPhraseSpan() {
    return MAX_TARGET_PHRASE_SPAN;
  }

  public static void setMaxPendingPhrases(int m) {
    System.err.println("Setting new maximum number of stacked phrases: " + m);
    MAX_PENDING_PHRASES = m;
  }

  public int pendingWords() {
    int sz = 0;
    for (DTUHypothesis.PendingPhrase<TK, FV> elA : pendingPhrases) {
      DTURule<TK> dtuOpt = (DTURule<TK>) elA.concreteOpt.abstractRule;
      for (int segId = elA.segmentIdx + 1; segId < dtuOpt.dtus.length; ++segId) {
        sz += dtuOpt.dtus[segId].size();
      }
    }
    return sz;
  }

  @Override
  public boolean hasPendingPhrases() {
    return !pendingPhrases.isEmpty();
  }

  /**
   * Hypothesis is "done" when the following conditions are met. (a) The number
   * of untranslated tokens must be zero, the number of "pending" phrases should
   * be zero, and the hypothesis should be unexpired.
   */
  @Override
  public boolean isDone() {
    int nPendingPhrases = (pendingPhrases == null) ? 0 : pendingPhrases.size();
    return super.isDone() && nPendingPhrases == 0 && !hasExpired;
  }

  @Override
  public double finalScoreEstimate() { // normally: score (past cost) + h
                                       // (future cost)
    if (pendingPhrasesCost <= 0.0)
      return partialScore() + pendingPhrasesCost + h;
    return partialScore() + h;
  }

  @Override
  public double score() { // as in Hypothesis, score() == finalScoreEstimate()
    return finalScoreEstimate();
  }

  @Override
  public double partialScore() {
    return score + (hasExpired ? -EXPIRATION_PENALTY : 0.0);
  }

  @Override
  public boolean hasExpired() {
    return hasExpired;
  }

  public Rule<TK> getAbstractOption() {
    assert (rule.abstractRule != null);
    return rule.abstractRule;
  }

  public boolean targetOnly() {
    return segmentIdx > 0;
  }

  @Override
  public String toString() {

    StringBuilder sb = new StringBuilder(super.toString()); // Hypothesis's
                                                            // toString()
    sb.append(" segIx=").append(segmentIdx);
    sb.append(" id=").append(System.identityHashCode(this));
    sb.append(" tOpt=").append(System.identityHashCode(rule));
    sb.append(String.format(" c=[%.3f,%.3f,%.3f]", partialScore(),
        pendingPhrasesCost, h));

    if (pendingPhrases != null) {
      sb.append(" | expired:").append(hasExpired);
      sb.append(" | pending:");

      for (final PendingPhrase<TK, FV> discTargetPhrase : pendingPhrases) {
        DTURule<TK> opt = (DTURule<TK>) discTargetPhrase.concreteOpt.abstractRule;
        sb.append(" {");
        int si = discTargetPhrase.segmentIdx + 1;
        for (int i = si; i < opt.dtus.length; ++i) {
          if (i > si)
            sb.append(" ");
          sb.append(String.format("{%s} ([s=%d,e=%d,c=%.3f])",
              opt.dtus[i].toString(" "), discTargetPhrase.firstPosition,
              discTargetPhrase.lastPosition, discTargetPhrase.futureCosts[i]));
        }
        sb.append("} ");
      }
    }

    return sb.toString();
  }

  /**
   * Compute cost of pending phrases.
   */
  private double costPendingPhrases() {

    double score = 0.0;

    for (PendingPhrase<TK, FV> pendingPhrase : pendingPhrases) {
      ConcreteRule<TK,FV> opt = pendingPhrase.concreteOpt;
      assert (opt.abstractRule instanceof DTURule);
      DTURule<TK> dtuOpt = (DTURule<TK>) opt.abstractRule;
      for (int i = pendingPhrase.segmentIdx + 1; i < dtuOpt.dtus.length; ++i) {
        score += pendingPhrase.futureCosts[i];
        // System.err.printf("cost pending phrases: %s %f\n",
        // dtuOpt.dtus[i].toString(), score);
      }
    }
    return score;
  }

  /**
   * Merge current DTUHypothesis with PendingPhrase instances.
   */
  public List<DTUHypothesis<TK, FV>> mergeHypothesisAndPendingPhrase(
      int sourceInputId, CombinedFeaturizer<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic) {

    if (hasExpired)
      return new LinkedList<DTUHypothesis<TK, FV>>();

    for (PendingPhrase<TK, FV> pendingPhrase : pendingPhrases) {
      if (pendingPhrase.lastPosition < length) {
        hasExpired = true;
        return new LinkedList<DTUHypothesis<TK, FV>>();
      }
    }

    List<DTUHypothesis<TK, FV>> nextHyps = new LinkedList<DTUHypothesis<TK, FV>>();

    for (PendingPhrase<TK, FV> currentPhrase : pendingPhrases) {

      DTURule<TK> dtuOpt = (DTURule<TK>) currentPhrase.concreteOpt.abstractRule;

      if (currentPhrase.segmentIdx + 1 < dtuOpt.dtus.length) {

        int currentSegmentIdx = currentPhrase.segmentIdx + 1;

        if (currentPhrase.firstPosition <= length) {
          nextHyps.add(new DTUHypothesis<TK, FV>(sourceInputId,
              currentPhrase.concreteOpt, length, this, featurizer, scorer,
              heuristic, currentPhrase, currentSegmentIdx,
              currentPhrase.concreteOpt.abstractRule));
        }
      }
    }

    return nextHyps;
  }

  /**
   * Constructor used for 1st segment of a discontinuous phrase.
   */
  public DTUHypothesis(int sourceInputId,
      ConcreteRule<TK,FV> translationOpt, int insertionPosition,
      Derivation<TK, FV> baseHyp, CombinedFeaturizer<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic) {

    super(sourceInputId, translationOpt, translationOpt.abstractRule,
        insertionPosition, baseHyp, featurizer, scorer, heuristic, /*
                                                                    * targetPhrase=
                                                                    */
        getSegment(translationOpt.abstractRule, 0),
        /* hasPendingPhrases= */hasPendingPhrases(translationOpt, baseHyp,
            true, false), /* segmentIdx= */0);

    // Copy old pending phrases from parent hypothesis:
    this.pendingPhrases = new TreeSet<PendingPhrase<TK, FV>>();
    if (baseHyp instanceof DTUHypothesis) {
      Set<PendingPhrase<TK, FV>> oldPhrases = ((DTUHypothesis<TK, FV>) baseHyp).pendingPhrases;
      for (PendingPhrase<TK, FV> oldPhrase : oldPhrases) {
        this.pendingPhrases.add(new PendingPhrase<TK, FV>(oldPhrase));
        int lastPosition = oldPhrase.lastPosition;
        if (lastPosition < this.length)
          this.hasExpired = true;
      }
    }

    // First segment of a discontinuous phrase has both source and target:
    this.segmentIdx = 0;

    // If parent hypothesis has expired, so does the current:
    if (baseHyp.hasExpired())
      this.hasExpired = true;

    // Add new pending phrases:
    // assert (MAX_TARGET_PHRASE_SPAN >= 0);
    if (translationOpt.abstractRule instanceof DTURule) {
      PendingPhrase<TK, FV> newPhrase = new PendingPhrase<TK, FV>(
          translationOpt, sourceInputId, this, featurizer, scorer, 0,
          this.length + MIN_GAP_SIZE, this.length + MAX_TARGET_PHRASE_SPAN);
      pendingPhrases.add(newPhrase);
    }

    // Too many pending phrases?:
    if (pendingPhrases.size() > MAX_PENDING_PHRASES)
      this.hasExpired = true;

    // Estimate future cost for pending phrases:
    pendingPhrasesCost = costPendingPhrases();
    checkExpiration();
  }

  // Constructor used with successors:
  public DTUHypothesis(int sourceInputId,
      ConcreteRule<TK,FV> translationOpt, int insertionPosition,
      Derivation<TK, FV> baseHyp, CombinedFeaturizer<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic,
      PendingPhrase<TK, FV> currentPhrase, int currentSegmentIdx,
      Rule<TK> actualTranslationOption) {

    super(
        sourceInputId,
        translationOpt,
        actualTranslationOption,
        insertionPosition,
        baseHyp,
        featurizer,
        scorer,
        heuristic,
        getSegment(translationOpt.abstractRule, currentSegmentIdx),
        hasPendingPhrases(
            translationOpt,
            baseHyp,
            false,
            currentSegmentIdx + 1 == getNumberSegments(translationOpt.abstractRule)),
        currentSegmentIdx);
    assert (actualTranslationOption == translationOpt.abstractRule);

    // Copy pending phrases from parent hypothesis, and move current pending
    // phrase from pendingPhrases to current partial hypothesis:
    pendingPhrases = new TreeSet<PendingPhrase<TK, FV>>();
    Set<PendingPhrase<TK, FV>> oldPhrases = ((DTUHypothesis<TK, FV>) baseHyp).pendingPhrases;
    for (PendingPhrase<TK, FV> oldPhrase : oldPhrases) {

      if (oldPhrase != currentPhrase) {
        // This is NOT the phrase selected as successor:
        pendingPhrases.add(new PendingPhrase<TK, FV>(oldPhrase));
      } else {
        DTURule<TK> dtuOpt = (DTURule<TK>) currentPhrase.concreteOpt.abstractRule;
        // This IS the phrase selected as successor:
        if (currentPhrase.segmentIdx + 2 >= dtuOpt.dtus.length)
          continue; // just appended the last pending phrase
        PendingPhrase<TK, FV> tmpPhrase = new PendingPhrase<TK, FV>(
            currentPhrase);
        tmpPhrase.segmentIdx = currentPhrase.segmentIdx + 1;
        tmpPhrase.firstPosition = this.length + MIN_GAP_SIZE;
        pendingPhrases.add(tmpPhrase);
      }
      if (oldPhrase.lastPosition < this.length)
        this.hasExpired = true;
    }
    // this.currentPendingPhrase = tmpPhrase;
    this.segmentIdx = currentSegmentIdx;
    assert (currentSegmentIdx > 0);

    // Too many pending phrases?:
    if (pendingPhrases.size() > MAX_PENDING_PHRASES)
      this.hasExpired = true;

    pendingPhrasesCost = costPendingPhrases();
    checkExpiration();
  }

  // Constructor used during nbest list generation:
  public DTUHypothesis(int sourceInputId,
      ConcreteRule<TK,FV> translationOpt, int insertionPosition,
      Derivation<TK, FV> baseHyp, Derivation<TK, FV> nextHyp,
      CombinedFeaturizer<TK, FV> featurizer, Scorer<FV> scorer,
      SearchHeuristic<TK, FV> heuristic, Set<Rule<TK>> seenOptions) {

    super(sourceInputId, translationOpt,
        getAbstractOption(nextHyp.featurizable), insertionPosition, baseHyp,
        featurizer, scorer, heuristic, getTranslation(nextHyp),
        !nextHyp.featurizable.done, nextHyp.featurizable.getSegmentIdx());

    if (nextHyp instanceof DTUHypothesis) {
      DTUHypothesis<TK, FV> dtuNextHyp = (DTUHypothesis<TK, FV>) nextHyp;
      this.pendingPhrases = dtuNextHyp.pendingPhrases;
      this.hasExpired = dtuNextHyp.hasExpired;
      this.segmentIdx = dtuNextHyp.segmentIdx;
    } else {
      this.pendingPhrases = new TreeSet<PendingPhrase<TK, FV>>();
      this.hasExpired = false;
      this.segmentIdx = 0;
    }

    if (!this.hasExpired && baseHyp instanceof DTUHypothesis) {
      DTUHypothesis<TK, FV> dtuBaseHyp = (DTUHypothesis<TK, FV>) baseHyp;
      if (dtuBaseHyp.hasExpired && (nextHyp.untranslatedTokens != 0))
        this.hasExpired = true;
    }

    seenOptions.add(translationOpt.abstractRule);
    pendingPhrasesCost = costPendingPhrases();
    checkExpiration();
  }

  // Determine whether hypothesis is bound to expire:
  private void checkExpiration() {
    // Note: this code leads to translation failures in some rare cases.

    if (hasExpired)
      return; // already expired

    if (pendingPhrases == null || pendingPhrases.isEmpty())
      return; // can't expire since there are no pending phrases

    // Will any pending phrase be applicable at the next step of decoding?
    int nextPosition = this.length + 1;
    for (PendingPhrase<TK,FV> pp : this.pendingPhrases) {
      if (pp.firstPosition <= nextPosition && nextPosition <= pp.lastPosition) // Answer:
                                                                               // yes
        return;
      // System.err.printf("Fail: %d <= %d <= %d\n", pp.firstPosition,
      // nextPosition, pp.lastPosition);
    }
    // System.err.println("Hypothesis bound to expire: "+this);
    hasExpired = true; // Answer: no
  }

  private static <TK, FV> RawSequence<TK> getTranslation(Derivation<TK, FV> hyp) {

    if (hyp instanceof DTUHypothesis) {

      DTUHypothesis<TK, FV> dtuHyp = (DTUHypothesis<TK, FV>) hyp;
      Rule<TK> opt = hyp.rule.abstractRule;

      if (opt instanceof DTURule) {
        DTURule<TK> dtuOpt = (DTURule<TK>) opt;
        return dtuOpt.dtus[dtuHyp.segmentIdx];
      }
    }

    return new RawSequence<TK>(hyp.rule.abstractRule.target);
  }

  private static <TK, FV> Rule<TK> getAbstractOption(
      Featurizable<TK, FV> f) {
    return (f instanceof DTUFeaturizable) ? ((DTUFeaturizable<TK, FV>) f).abstractOption
        : null;
  }

  private static <TK> RawSequence<TK> getSegment(Rule<TK> option,
      int idx) {
    if (option instanceof DTURule)
      return ((DTURule<TK>) option).dtus[idx];
    return new RawSequence<TK>(option.target);
  }

  private static <TK> int getNumberSegments(Rule<TK> option) {
    if (option instanceof DTURule)
      return ((DTURule<TK>) option).dtus.length;
    return 1;
  }

  /**
   * Returns true if the current hypothesis shouldn't be considered done because
   * some pending phrases still need to be appended to translation.
   */
  private static <TK, FV> boolean hasPendingPhrases(
      ConcreteRule<TK,FV> translationOpt, Derivation<TK, FV> baseHyp,
      boolean firstSegmentInOpt, boolean lastSegmentInOpt) {
    boolean pendingPhrases = false;

    // Two cases:
    // (1) translationOpt contains a gap in the target, in which case we must
    // return no.
    if (translationOpt.abstractRule instanceof DTURule && firstSegmentInOpt)
      pendingPhrases = true;

    // (2) baseHyp contains some pending phrase (that is not about to be
    // deleted).
    else if (baseHyp instanceof DTUHypothesis) {
      DTUHypothesis<TK, FV> dtuHyp = (DTUHypothesis<TK, FV>) baseHyp;
      int nPendingPhrases = dtuHyp.pendingPhrases.size();
      pendingPhrases = nPendingPhrases > 1
          || (nPendingPhrases == 1 && !lastSegmentInOpt);
    }

    return pendingPhrases;
  }

  @Override
  public void debug() {
    System.err.println("###################");
    System.err.printf(
        "hypothesis [class=%s,id=%d,pos=%d,expired=%s,pending=%d]: %s\n",
        getClass(), System.identityHashCode(this),
        featurizable.targetPosition, hasExpired, pendingPhrases.size(),
        this);
    System.err.printf(
        "parent hypothesis [class=%s,id=%d,pos=%d,expired=%s]: %s\n",
        preceedingDerivation.getClass(), System.identityHashCode(preceedingDerivation),
        preceedingDerivation.featurizable.targetPosition,
        preceedingDerivation.hasExpired(), preceedingDerivation);
    System.err.println("pendingPhrasesCost: " + pendingPhrasesCost);

    DTUHypothesis<TK, FV> hyp = this;
    if (hyp.isDone() != hyp.featurizable.done) {
      System.err.println("Error in AbstractBeamInferer with: " + hyp);
      System.err.println("isDone(): " + hyp.isDone());
      System.err.println("pending phrases: " + hyp.pendingPhrases.size());
      System.err.println("f.done: " + hyp.featurizable.done);
      Derivation<TK, FV> curHyp = hyp;
      while (curHyp != null) {
        System.err.println("  " + curHyp.toString());
        curHyp = curHyp.preceedingDerivation;
      }
      throw new RuntimeException();
    }

  }
}
