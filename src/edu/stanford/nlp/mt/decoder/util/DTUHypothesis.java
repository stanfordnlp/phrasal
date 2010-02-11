package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;

/**
 * Hypothesis that contains a list of "floating" discontinuous phrases,
 * i.e., target phrases that have already been generated, but that haven't yet
 * been appended to the translation prefix.
 *
 * @author Michel Galley
 */
public class DTUHypothesis<TK,FV> extends Hypothesis<TK,FV> {

  /**
   * This class represents a phrase with one or more discontinuities in its target side.
   * It holds this information: which abstract translation option is used to generate
   * the discontinuous phrase, and the index of the index of the current contignuous segment.
   */
  public static class DiscTargetPhrase<TK,FV> implements Cloneable, Comparable<DiscTargetPhrase<TK,FV>> {

    public final ConcreteTranslationOption<TK> concreteOpt; // e.g., ne ... pas

    public int segmentIdx; // Current segment of the translation option.
                            // For instance, segmentIdx=0 selects "ne" and segmentIdx=1 selects "pas"
    private int firstPosition, lastPosition; // the discontinuous phrases segmentIdx+1,segmentIdx+2,etc.
                                             // must be generated within the range
                                             // [firstPosition,lastPosition] of target words.

    // Copy constructor:
    public DiscTargetPhrase(DiscTargetPhrase<TK,FV> old) {
      this.concreteOpt = old.concreteOpt;
      this.segmentIdx = old.segmentIdx;
      this.firstPosition = old.firstPosition;
      this.lastPosition = old.lastPosition;
    }

    public DiscTargetPhrase(ConcreteTranslationOption<TK> concreteOpt, int segmentIdx, int firstPosition, int lastPosition) {
      this.segmentIdx = segmentIdx;
      this.firstPosition = firstPosition;
      this.lastPosition = lastPosition;
      this.concreteOpt = concreteOpt;
    }

    public int compareTo(DiscTargetPhrase<TK,FV> o) {
      int sig = Integer.signum(this.segmentIdx-o.segmentIdx);
      if (this.concreteOpt == o.concreteOpt)
        return sig;
      int h1 = System.identityHashCode(concreteOpt);
      int h2 = System.identityHashCode(o.concreteOpt);
      if (h1 == h2)
        // This case (i.e., concreteOpt != o.concreteOpt && h1 == h2) can happen with a 64-bit JVM, though it is very unlikely.
        return sig;
      return Integer.signum(h1-h2);
    }
  }

  private static final double EXPIRATION_PENALTY = 1000.0; // When a DTUHypothesis expires, it suffers this cost
  private static int maxTargetPhraseSpan = 10;
  private static int maxFloatingPhrases = 5;

  private boolean hasExpired=false;
  private final boolean targetOnly;
  public final Set<DiscTargetPhrase<TK,FV>> discTargetPhrases; // discontinuous phrases that are still "floating"
  private final DiscTargetPhrase<TK,FV> currentDiscTargetPhrase; // the current discontinuous phrase
  private final double floatingPhraseH; // future cost estimation, which currently only accounts for the LM
                                        // score of "floating" phrases

  public static void setMaxTargetPhraseSpan(int m) {
    System.err.println("Setting max target phrase span: "+m);
    maxTargetPhraseSpan = m;
  }
  public static int getMaxTargetPhraseSpan() { return maxTargetPhraseSpan; }

  public static void setMaxFloatingPhrases(int m) {
    System.err.println("Setting max floating phrases: "+m);
    maxFloatingPhrases = m;
  }

  public boolean targetOnly() {
    return targetOnly;
  }

  private static <TK,FV> RawSequence<TK> getTranslation(Hypothesis<TK,FV> nextHyp) {
    if(nextHyp instanceof DTUHypothesis) {
      TranslationOption<TK> opt = nextHyp.translationOpt.abstractOption;
      if(opt instanceof DTUOption) {
        DTUOption<TK> dtuOpt = (DTUOption<TK>) opt;
        DiscTargetPhrase curPhrase = ((DTUHypothesis)nextHyp).currentDiscTargetPhrase;
        int segmentIdx = (curPhrase != null) ? curPhrase.segmentIdx : dtuOpt.dtus.length-1;
        return dtuOpt.dtus[segmentIdx];
      }
    }
    return nextHyp.translationOpt.abstractOption.translation;
  }

  private static <TK> RawSequence<TK> firstSegment(TranslationOption<TK> option) {
    if (option instanceof DTUOption) {
      return ((DTUOption<TK>)option).dtus[0];
    }
    return option.translation;
  }

  /**
   * Returns true if the current hypothesis shouldn't be considered done because
   * some floating target phrases still need to be appended to translation.
   */
  private static <TK,FV> boolean hasRemainingFloatingPhrases(ConcreteTranslationOption<TK> translationOpt, boolean firstSegmentInOpt, Hypothesis<TK,FV> baseHyp) {
    boolean floating = false;
    // Two cases:
    // (1) translationOpt contains a gap in the target, in which case we must return no.
    if (translationOpt.abstractOption instanceof DTUOption && firstSegmentInOpt)
      floating = true;
    // (2) baseHyp contains some floating phrase.
    else if (baseHyp instanceof DTUHypothesis) {
      DTUHypothesis<TK,FV> dtuHyp = (DTUHypothesis<TK,FV>) baseHyp;
      floating = dtuHyp.discTargetPhrases.size() > 0;
    }
    return floating;
  }

  /**
   * Constructor used for first segment of a discontinuous phrase.
   */
  public DTUHypothesis(int translationId,
			ConcreteTranslationOption<TK> translationOpt,
			int insertionPosition,
			Hypothesis<TK,FV> baseHyp,
			CombinedFeaturizer<TK,FV> featurizer,
			Scorer<FV> scorer,
			SearchHeuristic<TK,FV> heuristic) {
    super(translationId, translationOpt, translationOpt.abstractOption, insertionPosition, baseHyp, featurizer, scorer, heuristic,
          firstSegment(translationOpt.abstractOption), hasRemainingFloatingPhrases(translationOpt, true, baseHyp), false);

    // Copy old floating phrases:
    boolean hasExpired = baseHyp.hasExpired();
    if(baseHyp instanceof DTUHypothesis) {
      Set<DiscTargetPhrase<TK,FV>> oldPhrases = ((DTUHypothesis<TK,FV>)baseHyp).discTargetPhrases;
      discTargetPhrases = new TreeSet<DiscTargetPhrase<TK,FV>>();
      for(DiscTargetPhrase<TK,FV> oldPhrase : oldPhrases) {
        this.discTargetPhrases.add(new DiscTargetPhrase<TK,FV>(oldPhrase));
        int lastPosition = oldPhrase.lastPosition;
        if(lastPosition < this.length)
          hasExpired = true;
      }
    } else {
      this.discTargetPhrases = new TreeSet<DiscTargetPhrase<TK,FV>>();
    }
    this.hasExpired = hasExpired;
    this.targetOnly = false;

    // Add new floating phrases:
    if(translationOpt.abstractOption instanceof DTUOption) {
      currentDiscTargetPhrase = new DiscTargetPhrase<TK,FV>
         (translationOpt, 0, this.length+1, this.length + maxTargetPhraseSpan);
      discTargetPhrases.add(currentDiscTargetPhrase);
    } else {
      currentDiscTargetPhrase = null;
    }
    
    // Too many floating phrases?:
    if(discTargetPhrases.size() > maxFloatingPhrases)
      this.hasExpired = true;

    // Estimate future cost for floating phrases:
    floatingPhraseH = getFloatingPhraseH(featurizer, scorer, translationId);
    //debug(this, true);
  }

  private double getFloatingPhraseH(CombinedFeaturizer<TK,FV> featurizer, Scorer<FV> scorer, int translationId) {
    double score = 0.0;
    for (DiscTargetPhrase<TK,FV> discTargetPhrase : discTargetPhrases) {
      int nextSegmentIdx = discTargetPhrase.segmentIdx+1;
      ConcreteTranslationOption<TK> opt = discTargetPhrase.concreteOpt;
      if (opt.abstractOption instanceof DTUOption) {
        DTUOption<TK> dtuOpt = (DTUOption<TK>)opt.abstractOption;
        for (int i=nextSegmentIdx; i<dtuOpt.dtus.length; ++i) {
          Featurizable<TK, FV> f = new DTUFeaturizable<TK, FV>(this, null, translationId, 0, dtuOpt.dtus[i], true, true);
          List<FeatureValue<FV>> phraseFeatures = featurizer.phraseListFeaturize(f);
          score += scorer.getIncrementalScore(phraseFeatures);
          //System.err.printf("floatingPhraseH: %s %f [%s]\n", seq, score, phraseFeatures);
        }
      } else {
        assert(nextSegmentIdx == 1);
      }
    }
    return score;
  }

  // Constructor used with successors:
  public DTUHypothesis(int translationId,
			ConcreteTranslationOption<TK> translationOpt,
			int insertionPosition,
			Hypothesis<TK,FV> baseHyp,
			CombinedFeaturizer<TK,FV> featurizer,
			Scorer<FV> scorer,
			SearchHeuristic<TK,FV> heuristic, DiscTargetPhrase<TK,FV> currentPhrase,
      RawSequence<TK> currentSegment,
      TranslationOption<TK> actualTranslationOption) {

    super(translationId, translationOpt, actualTranslationOption, insertionPosition, baseHyp, featurizer, scorer, heuristic,
          currentSegment, hasRemainingFloatingPhrases(translationOpt, false, baseHyp), true);

    Set<DiscTargetPhrase<TK,FV>> oldPhrases = ((DTUHypothesis<TK,FV>)baseHyp).discTargetPhrases;
    discTargetPhrases = new TreeSet<DiscTargetPhrase<TK,FV>>();

    // Copy floating phrases from baseHyp, and move one floating phrase (identified by floatingPhraseIdx)
    // from discTargetPhrases to current partial hypothesis:
    boolean hasExpired = baseHyp.hasExpired();
    DiscTargetPhrase<TK,FV> newPhrase = null;
    for (DiscTargetPhrase<TK,FV> oldPhrase : oldPhrases) {
      if(oldPhrase != currentPhrase) {
        // This is NOT the phrase selected as successor:
        discTargetPhrases.add(new DiscTargetPhrase<TK,FV>(oldPhrase));
      } else {
        DTUOption<TK> dtuOpt = (DTUOption<TK>) currentPhrase.concreteOpt.abstractOption;
        // This IS the phrase selected as successor:
        if(currentPhrase.segmentIdx+2 >= dtuOpt.dtus.length)
          continue; // just appended the last floating phrase
        newPhrase = new DiscTargetPhrase<TK,FV>(currentPhrase);
        newPhrase.segmentIdx = currentPhrase.segmentIdx+1;
        newPhrase.firstPosition = this.length+1;
        discTargetPhrases.add(newPhrase);
      }
      int lastPosition = oldPhrase.lastPosition;
      if (lastPosition < this.length) {
        hasExpired = true;
      }
    }
    currentDiscTargetPhrase = newPhrase;
    assert(currentDiscTargetPhrase == null);
    targetOnly = true;

    // Too many floating phrases?:
    this.hasExpired = hasExpired;
    if(discTargetPhrases.size() > maxFloatingPhrases)
      this.hasExpired = true;

    floatingPhraseH = getFloatingPhraseH(featurizer, scorer, translationId);
    //debug(this, false);
  }

  // Constructor used during nbest list generation:
  public DTUHypothesis(int translationId,
      ConcreteTranslationOption<TK> translationOpt,
			int insertionPosition,
			Hypothesis<TK,FV> baseHyp,
      Hypothesis<TK,FV> nextHyp,
      CombinedFeaturizer<TK,FV> featurizer,
			Scorer<FV> scorer,
			SearchHeuristic<TK,FV> heuristic, Set<TranslationOption> seenOptions) {
    super(translationId, translationOpt,
         (nextHyp.featurizable instanceof DTUFeaturizable) ? ((DTUFeaturizable<TK,FV>)nextHyp.featurizable).abstractOption : null,
         insertionPosition, baseHyp, featurizer, scorer, heuristic, getTranslation(nextHyp),
         !nextHyp.featurizable.done, seenOptions.contains(translationOpt.abstractOption));
    if(nextHyp instanceof DTUHypothesis) {
      DTUHypothesis<TK,FV> dtuNextHyp = (DTUHypothesis<TK,FV>) nextHyp;
      this.discTargetPhrases = dtuNextHyp.discTargetPhrases;
      this.currentDiscTargetPhrase = dtuNextHyp.currentDiscTargetPhrase;
      this.hasExpired = dtuNextHyp.hasExpired;
      this.targetOnly = dtuNextHyp.targetOnly;
    } else {
      this.discTargetPhrases = new TreeSet<DiscTargetPhrase<TK,FV>>();
      this.currentDiscTargetPhrase = null;
      this.hasExpired = false;
      this.targetOnly = false;
    }
    if(!this.hasExpired && baseHyp instanceof DTUHypothesis) {
      DTUHypothesis<TK,FV> dtuBaseHyp = (DTUHypothesis<TK,FV>) baseHyp;
      if(dtuBaseHyp.hasExpired && (nextHyp.untranslatedTokens != 0))
        this.hasExpired = true;
    }
    seenOptions.add(translationOpt.abstractOption);
    floatingPhraseH = getFloatingPhraseH(featurizer, scorer, translationId);
  }

  static <TK,FV> void debug(DTUHypothesis<TK,FV> hyp, boolean first) {
    System.err.println("###################");
    System.err.printf("hypothesis (first=%s) (bad=%s) (floating=%d) (class=%s id=%d): %s\n", first, hyp.hasExpired, hyp.discTargetPhrases.size(), hyp.getClass().toString(), System.identityHashCode(hyp), hyp);
    System.err.printf("parent hypothesis (class=%s): %s\n", hyp.preceedingHyp.getClass().toString(), hyp.preceedingHyp);
    System.err.println("###################");
    System.err.println("translation position: "+hyp.featurizable.translationPosition);
    System.err.println("score: "+hyp.score);
    System.err.println("h: "+hyp.h);
    System.err.println("floatingPhraseH: "+hyp.floatingPhraseH);
    System.err.println("###################");
  }

  public List<DTUHypothesis<TK,FV>> collapseFloatingPhrases(int translationId, CombinedFeaturizer<TK,FV> featurizer,
                                      Scorer<FV> scorer, SearchHeuristic<TK,FV> heuristic) {
    List<DTUHypothesis<TK,FV>> nextHyps = new LinkedList<DTUHypothesis<TK,FV>>();
    if (hasExpired)
      return new LinkedList<DTUHypothesis<TK,FV>>();
    for (DiscTargetPhrase<TK, FV> discTargetPhrase : discTargetPhrases) {
      if (discTargetPhrase.lastPosition < length) {
        hasExpired = true;
        break;
      }
    }
    if (!hasExpired)
      for(DiscTargetPhrase<TK,FV> currentPhrase : discTargetPhrases) {
        DTUOption<TK> dtuOpt = (DTUOption<TK>) currentPhrase.concreteOpt.abstractOption;
        if (currentPhrase.segmentIdx+1 < dtuOpt.dtus.length) {
          RawSequence<TK> currentSegment = dtuOpt.dtus[currentPhrase.segmentIdx+1];
          if(currentPhrase.firstPosition <= length) {
            nextHyps.add(new DTUHypothesis<TK,FV>
               (translationId, currentPhrase.concreteOpt, length, this, featurizer, scorer, heuristic, currentPhrase,
                    currentSegment, currentPhrase.concreteOpt.abstractOption));
          }
        }
      }
    return nextHyps;
  }

  @Override
  public boolean hasExpired() {
    return hasExpired;
  }

  public TranslationOption<TK> getAbstractOption() {
    assert (translationOpt.abstractOption != null);
    return translationOpt.abstractOption;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    sb.append(" id=").append(System.identityHashCode(this));
    sb.append(" tOpt=").append(System.identityHashCode(translationOpt));
    if(discTargetPhrases != null) {
      sb.append(" | expired:").append(hasExpired);
      sb.append(" | floating:");
      for (final DiscTargetPhrase<TK,FV> discTargetPhrase : discTargetPhrases) {
        DTUOption<TK> opt = (DTUOption<TK>) discTargetPhrase.concreteOpt.abstractOption;
        sb.append(" {");
        for (int i = discTargetPhrase.segmentIdx+1; i < opt.dtus.length; ++i) {
          sb.append(String.format(" %s ([%d,%d])",
               opt.dtus[i].toString(" "),
               discTargetPhrase.firstPosition, discTargetPhrase.lastPosition));
        }
        sb.append("} ");
      }
    }
    return sb.toString();
  }

  @Override
  public boolean isDone() {
    int floatingPhrasesSize = discTargetPhrases == null ? 0 : discTargetPhrases.size();
    return untranslatedTokens == 0 && floatingPhrasesSize == 0 && !hasExpired;
  }

  @Override
  public double finalScoreEstimate() {
    if(floatingPhraseH <= 0.0)
      return partialScore() + floatingPhraseH + h;
    return partialScore() + h;
  }

  @Override
  public double score() {
    return finalScoreEstimate();
  }

  @Override
	public double partialScore() {
		return score + (hasExpired ? -EXPIRATION_PENALTY : 0.0);
  }
}

