package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.DTUOption;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;

/**
 * @author Michel Galley
 */
public class DTUHypothesis<TK,FV> extends Hypothesis<TK,FV> {

  static final double EXPIRATION_PENALTY = 1000.0;

  private final TranslationOption<TK> abstractOption;
  private final List<FloatingPhrase<TK,FV>> floatingPhrases;
  public final List<Sequence<TK>> sortedFloatingPhrases; // for recombination
  private final RawSequence<TK> latestTranslationPhrase;

  private static int maxTargetPhraseSpan;
  private static int maxFloatingPhrases = 5;

  private final double floatingPhraseH;
  public final boolean targetOnly;
  private boolean hasExpired;

  public static void setMaxTargetPhraseSpan(int m) {
    System.err.println("Setting max target phrase span: "+m);
    maxTargetPhraseSpan = m;
  }
  public static int getMaxTargetPhraseSpan() { return maxTargetPhraseSpan; }

  public static void setMaxFloatingPhrases(int m) {
    System.err.println("Setting max floating phrases: "+m);
    maxFloatingPhrases = m;
  }
  public static int getMaxFloatingPhrases() { return maxFloatingPhrases; }


  static class FloatingPhrase<TK,FV> implements Cloneable {

    final Deque<RawSequence<TK>> translationPhrases;
    final DTUOption<TK> abstractOption;

    int firstPosition;
    final int lastPosition;

    public FloatingPhrase(FloatingPhrase<TK,FV> old) {
      translationPhrases = new LinkedList<RawSequence<TK>>(old.translationPhrases);
      this.firstPosition = old.firstPosition;
      this.lastPosition = old.lastPosition;
      this.abstractOption = old.abstractOption;
      assert(abstractOption != null);
    }

    public FloatingPhrase(DTUOption<TK> dtuOpt, RawSequence<TK>[] seqs, int startIdx, int endIdx, int firstPosition, int lastPosition) {
      translationPhrases = new LinkedList<RawSequence<TK>>();
      for(int i=startIdx; i<=endIdx; ++i)
        translationPhrases.add(seqs[i]);
      this.firstPosition = firstPosition;
      this.lastPosition = lastPosition;
      this.abstractOption = dtuOpt;
      assert(abstractOption != null);
    }

    /*
    @Override
    public int compareTo(FloatingPhrase<TK,FV> o) {

      Iterator<RawSequence<TK>> i1 = translationPhrases.iterator();
      Iterator<RawSequence<TK>> i2 = o.translationPhrases.iterator();

      while (i1.hasNext() || i2.hasNext()) {
        RawSequence<TK> s1 = i1.hasNext() ? i1.next() : null;
        RawSequence<TK> s2 = i2.hasNext() ? i2.next() : null;
        if(s1 == null) {
          return -1;
        } else if(s2 == null) {
          return 1;
        } else {
          int cmp = s1.compareTo(s2);
          if(cmp != 0)
            return cmp;
        }
      }
      return 0;
    }
    */
  }

  private static <TK,FV> boolean hasFloatingPhrases(ConcreteTranslationOption<TK> translationOpt, boolean firstSegmentInOpt, Hypothesis<TK,FV> baseHyp) {
    if(firstSegmentInOpt) {
      if(translationOpt.abstractOption instanceof DTUOption)
        return true;
      if(baseHyp instanceof DTUHypothesis) {
        DTUHypothesis<TK,FV> dtuHyp = (DTUHypothesis<TK,FV>) baseHyp;
        return dtuHyp.sizeFloatingPhrases() > 0;
      }
    } else {
      if(baseHyp instanceof DTUHypothesis) {
        DTUHypothesis<TK,FV> dtuHyp = (DTUHypothesis<TK,FV>) baseHyp;
        return dtuHyp.sizeFloatingPhrases() > 1;
      }
    }
    return false;
  }
  
  private static <TK> RawSequence<TK> firstSegment(TranslationOption<TK> option) {
    if (option instanceof DTUOption) {
      return ((DTUOption<TK>)option).dtus[0];
    }
    return option.translation;
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
    this.targetOnly = seenOptions.contains(translationOpt.abstractOption);
    this.abstractOption = translationOpt.abstractOption;
    assert(this.abstractOption != null);
    this.latestTranslationPhrase = getTranslation(nextHyp);
    if(nextHyp instanceof DTUHypothesis) {
      DTUHypothesis<TK,FV> dtuNextHyp = (DTUHypothesis<TK,FV>) nextHyp;
      this.floatingPhrases = dtuNextHyp.floatingPhrases;
      this.sortedFloatingPhrases = dtuNextHyp.sortedFloatingPhrases;
      this.hasExpired = dtuNextHyp.hasExpired;
    } else {
      this.floatingPhrases = new ArrayList<FloatingPhrase<TK,FV>>(0);
      this.sortedFloatingPhrases = new ArrayList<Sequence<TK>>(0);
      this.hasExpired = false;
    }
    if(!this.hasExpired && baseHyp instanceof DTUHypothesis) {
      DTUHypothesis<TK,FV> dtuBaseHyp = (DTUHypothesis<TK,FV>) baseHyp;
      if(dtuBaseHyp.hasExpired && (nextHyp.untranslatedTokens != 0))
        this.hasExpired = true;
    }
    seenOptions.add(translationOpt.abstractOption);
    floatingPhraseH = getFloatingPhraseH(featurizer, scorer, translationId);
  }

  private static <TK,FV> RawSequence<TK> getTranslation(Hypothesis<TK,FV> hyp) {
    if(hyp instanceof DTUHypothesis) {
      return ((DTUHypothesis<TK,FV>)hyp).latestTranslationPhrase;
    }
    return hyp.translationOpt.abstractOption.translation;
  }

  // Constructor used with first phrase:
  public DTUHypothesis(int translationId,
			ConcreteTranslationOption<TK> translationOpt,
			int insertionPosition,
			Hypothesis<TK,FV> baseHyp,
			CombinedFeaturizer<TK,FV> featurizer,
			Scorer<FV> scorer,
			SearchHeuristic<TK,FV> heuristic) {
    super(translationId, translationOpt, translationOpt.abstractOption, insertionPosition, baseHyp, featurizer, scorer, heuristic,
          firstSegment(translationOpt.abstractOption), hasFloatingPhrases(translationOpt, true, baseHyp), false);
    this.targetOnly = false;
    this.abstractOption = translationOpt.abstractOption;
    assert(this.abstractOption != null);
    this.latestTranslationPhrase = firstSegment(this.abstractOption);

    // Sanity check:
    assert(insertionPosition >= baseHyp.length);
    assert(featurizable.partialTranslation.size() == this.length);

    // Copy old floating phrases:
    boolean hasExpired = baseHyp.hasExpired();
    if(baseHyp instanceof DTUHypothesis) {
      List<FloatingPhrase<TK,FV>> oldPhrases = ((DTUHypothesis<TK,FV>)baseHyp).floatingPhrases;
      floatingPhrases = new ArrayList<FloatingPhrase<TK,FV>>(oldPhrases.size()+1);
      for(FloatingPhrase<TK,FV> oldPhrase : oldPhrases) {
        this.floatingPhrases.add(new FloatingPhrase<TK,FV>(oldPhrase));
        int lastPosition = oldPhrase.lastPosition;
        if(lastPosition < this.length)
          hasExpired = true;
      }
    } else {
      this.floatingPhrases = new ArrayList<FloatingPhrase<TK,FV>>(1);
    }
    this.hasExpired = hasExpired;

    // Add new floating phrases:
    if(translationOpt.abstractOption instanceof DTUOption) {
      DTUOption<TK> dtuOpt = (DTUOption<TK>)translationOpt.abstractOption;
      RawSequence<TK>[] newDtus = dtuOpt.dtus;
      floatingPhrases.add(new FloatingPhrase<TK,FV>
         (dtuOpt, newDtus, 1, newDtus.length-1, this.length+1, this.length + maxTargetPhraseSpan));
    }

    // Sort floating phrases:
    this.sortedFloatingPhrases = new LinkedList<Sequence<TK>>();
    for(FloatingPhrase<TK,FV> ph : floatingPhrases)
      for(Sequence<TK> seq : ph.translationPhrases)
        this.sortedFloatingPhrases.add(seq);
    Collections.sort(sortedFloatingPhrases);

    // Too many floating phrases?:
    if(floatingPhrases.size() > maxFloatingPhrases)
      this.hasExpired = true;

    // Estimate future cost for floating phrases:
    floatingPhraseH = getFloatingPhraseH(featurizer, scorer, translationId);
  }

  private double getFloatingPhraseH(CombinedFeaturizer<TK,FV> featurizer, Scorer<FV> scorer, int translationId) {
    double score = 0.0;
    for (FloatingPhrase<TK,FV> floatingPhrase : floatingPhrases) {
      for (RawSequence<TK> seq : floatingPhrase.translationPhrases) {
        Featurizable<TK, FV> f = new DTUFeaturizable<TK, FV>(this, null, translationId, 0, seq, true, true);
        List<FeatureValue<FV>> phraseFeatures = featurizer.phraseListFeaturize(f);
        score += scorer.getIncrementalScore(phraseFeatures);
        //System.err.printf("ff: %s %f [%s]\n", seq, score, phraseFeatures);
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
			SearchHeuristic<TK,FV> heuristic, int floatingPhraseIdx,
      RawSequence<TK> firstFloatingPhrase,
      TranslationOption<TK> actualTranslationOption) {
    
    super(translationId, translationOpt, actualTranslationOption, insertionPosition, baseHyp, featurizer, scorer, heuristic,
          firstFloatingPhrase, hasFloatingPhrases(translationOpt, false, baseHyp), true);

    this.targetOnly = true;
    this.abstractOption = actualTranslationOption;
    assert(this.abstractOption != null);
    this.latestTranslationPhrase = firstFloatingPhrase;

    // Sanity check:
    assert(insertionPosition == baseHyp.length);
    assert(featurizable.partialTranslation.size() == this.length);

    assert(baseHyp instanceof DTUHypothesis);

    List<FloatingPhrase<TK,FV>> oldPhrases = ((DTUHypothesis<TK,FV>)baseHyp).floatingPhrases;
    floatingPhrases = new ArrayList<FloatingPhrase<TK,FV>>(oldPhrases.size());

    // Copy floating phrases from baseHyp, and move one floating phrase (identified by floatingPhraseIdx)
    // from floatingPhrases to current partial hypothesis:
    boolean hasExpired = baseHyp.hasExpired();
    for(int i=0; i<oldPhrases.size(); ++i) {
      FloatingPhrase<TK,FV> oldPhrase = oldPhrases.get(i);
      assert(oldPhrase.translationPhrases.size() >= 1);
      if(i != floatingPhraseIdx) {
        // This is NOT the phrase selected as successor:
        floatingPhrases.add(new FloatingPhrase<TK,FV>(oldPhrase));
      } else {
        // This IS the phrase selected as successor:
        if(oldPhrase.translationPhrases.size() == 1)
          continue;
        FloatingPhrase<TK,FV> newPhrase = new FloatingPhrase<TK,FV>(oldPhrase);
        newPhrase.translationPhrases.removeFirst();
        newPhrase.firstPosition = this.length+1;
        floatingPhrases.add(newPhrase);
      }
      int lastPosition = oldPhrase.lastPosition;
      if(lastPosition < this.length) {
        hasExpired = true;
      }
    }

    // Sort floating phrases:
    this.sortedFloatingPhrases = new LinkedList<Sequence<TK>>();
    for(FloatingPhrase<TK,FV> ph : floatingPhrases)
      for(Sequence<TK> seq : ph.translationPhrases)
        this.sortedFloatingPhrases.add(seq);
    Collections.sort(sortedFloatingPhrases);

    // Too many floating phrases?:
    this.hasExpired = hasExpired;
    if(floatingPhrases.size() > maxFloatingPhrases)
      this.hasExpired = true;

    floatingPhraseH = getFloatingPhraseH(featurizer, scorer, translationId);
    //debug(this, false);
  }

  static <TK,FV> void debug(DTUHypothesis<TK,FV> hyp, boolean first) {
    System.err.println("###################");
    System.err.printf("hypothesis (stage=%s) (bad=%s) (floating=%d) (class=%s id=%d): %s\n", first, hyp.hasExpired, hyp.sizeFloatingPhrases(), hyp.getClass().toString(), System.identityHashCode(hyp), hyp);
    System.err.printf("parent hypothesis (class=%s id=%d): %s\n", hyp.preceedingHyp.getClass().toString(), System.identityHashCode(hyp.abstractOption), hyp.preceedingHyp);
    System.err.println("###################");
    System.err.println("translation position: "+hyp.featurizable.translationPosition);
    System.err.println("score: "+hyp.score);
    System.err.println("h: "+hyp.h);
    System.err.println("floatingPhraseH: "+hyp.floatingPhraseH);
    System.err.println("###################");
    //try { throw new Exception(); } catch(Exception e) { e.printStackTrace(); }
  }

  public List<DTUHypothesis<TK,FV>> collapseFloatingPhrases(int translationId, CombinedFeaturizer<TK,FV> featurizer,
                                      Scorer<FV> scorer, SearchHeuristic<TK,FV> heuristic) {
    List<DTUHypothesis<TK,FV>> nextHyps = new LinkedList<DTUHypothesis<TK,FV>>();
    if(hasExpired)
      return new LinkedList<DTUHypothesis<TK,FV>>();
    for (FloatingPhrase<TK, FV> floatingPhrase : floatingPhrases) {
      if (floatingPhrase.lastPosition < length) {
        hasExpired = true;
        break;
      }
    }
    if(!hasExpired)
      for(int i=0; i<floatingPhrases.size(); ++i) {
        FloatingPhrase<TK,FV> floatingPhrase = floatingPhrases.get(i);
        if(floatingPhrase.firstPosition <= length) {
          nextHyps.add(new DTUHypothesis<TK,FV>
             (translationId, translationOpt, length, this, featurizer, scorer, heuristic, i,
                  floatingPhrase.translationPhrases.getFirst(), floatingPhrase.abstractOption));
        }
      }
    return nextHyps;
  }

  @Override
  public boolean hasExpired() {
    return hasExpired;
  }

  public int sizeFloatingPhrases() {
    int sz=0;
    for (FloatingPhrase<TK,FV> f : floatingPhrases) {
      for (Sequence<TK> s : f.translationPhrases) {
        ++sz;
      }
    }
    return sz;
  }

  public int nbFloatingPhrases() {
    return floatingPhrases.size();
  }

  public Deque<? extends Sequence<TK>> getFloatingPhrase(int i) {
    return floatingPhrases.get(i).translationPhrases;
  }

  public TranslationOption<TK> getAbstractOption() {
    assert(this.abstractOption != null);
    return abstractOption;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    sb.append(" id=").append(System.identityHashCode(this));
    sb.append(" tOpt=").append(System.identityHashCode(translationOpt));
    sb.append(" aOpt=").append(System.identityHashCode(abstractOption));
    if(floatingPhrases != null) {
      sb.append(" | floating:");
      for (final FloatingPhrase<TK,FV> floatingPhrase : floatingPhrases) {
        sb.append(String.format(" %s ([%d,%d])",
             Arrays.toString(floatingPhrase.translationPhrases.toArray()),
             floatingPhrase.firstPosition, floatingPhrase.lastPosition));
      }
    }
    return sb.toString();
  }

  @Override
  public boolean isDone() {
    int floatingPhrasesSize = floatingPhrases == null ? 0 : floatingPhrases.size();
    return untranslatedTokens == 0 && floatingPhrasesSize == 0;
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
    //assert(EXPIRATION_PENALTY <= 0.0);
		return score + (hasExpired ? -EXPIRATION_PENALTY : 0.0);
  }

}
