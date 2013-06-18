package edu.stanford.nlp.mt.decoder.inferer;

import java.util.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.recomb.*;
import edu.stanford.nlp.mt.decoder.util.*;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
abstract public class AbstractBeamInferer<TK, FV> extends
    AbstractInferer<TK, FV> {

  static public final String DEBUG_OPT = "AbstractBeamInfererDebug";
  static public final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_OPT, "false"));
  static public boolean DISTINCT_SURFACE_TRANSLATIONS = false;
  public final int beamCapacity;
  public final HypothesisBeamFactory.BeamType beamType;

  public static final int MAX_DUPLICATE_FACTOR = 10;
  public static final int SAFE_LIST = 500;

  protected AbstractBeamInferer(AbstractBeamInfererBuilder<TK, FV> builder) {
    super(builder);
    this.beamCapacity = builder.beamCapacity;
    this.beamType = builder.beamType;
  }
  
  @Override
  public boolean shutdown() {
    return true;
  }

  @Override
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source,
      int sourceInputId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int size) {
    return nbest(scorer, source, sourceInputId, constrainedOutputSpace,
        targets, size);
  }

  @Override
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int size) {
    /*
     * if (size > beamCapacity) { System.err .printf(
     * "Warning: Requested nbest list size, %d, exceeds beam capacity of %d\n",
     * size, beamCapacity); }
     */
    RecombinationHistory<Hypothesis<TK, FV>> recombinationHistory = new RecombinationHistory<Hypothesis<TK, FV>>();

    Beam<Hypothesis<TK, FV>> beam = decode(scorer, source, sourceInputId,
        recombinationHistory, constrainedOutputSpace, targets, size);
    if (beam == null)
      return null;
    List<RichTranslation<TK, FV>> translations = new LinkedList<RichTranslation<TK, FV>>();

    List<Hypothesis<TK, FV>> goalStates = new ArrayList<Hypothesis<TK, FV>>(
        beam.size());

    for (Hypothesis<TK, FV> hyp : beam) {
      goalStates.add(hyp);
    }

    long nbestStartTime = System.nanoTime();

    Set<Sequence<TK>> distinctSurfaceTranslations = new HashSet<Sequence<TK>>();
    if (DISTINCT_SURFACE_TRANSLATIONS)
      System.err.println("N-best list with distinct surface strings: " + DISTINCT_SURFACE_TRANSLATIONS);

    featurizer.rerankingMode(true);

    StateLatticeDecoder<Hypothesis<TK, FV>> latticeDecoder = new StateLatticeDecoder<Hypothesis<TK, FV>>(
        goalStates, recombinationHistory);

    int hypCount = 0, maxDuplicateCount = size * MAX_DUPLICATE_FACTOR;
    for (List<Hypothesis<TK, FV>> hypList : latticeDecoder) {

      boolean withDTUs = false;
      ++hypCount;
      Hypothesis<TK, FV> hyp = null;
      Set<TranslationOption<TK>> seenOptions = new HashSet<TranslationOption<TK>>();

      for (Hypothesis<TK, FV> nextHyp : hypList) {
        if (hyp == null) {
          hyp = nextHyp;
          continue;
        }
        if (nextHyp.translationOpt.abstractOption instanceof DTUOption)
          withDTUs = true;
        if (withDTUs) {
          hyp = new DTUHypothesis<TK, FV>(sourceInputId,
              nextHyp.translationOpt, hyp.length, hyp, nextHyp, featurizer,
              scorer, heuristic, seenOptions);
        } else {
          hyp = new Hypothesis<TK, FV>(sourceInputId, nextHyp.translationOpt,
              hyp.length, hyp, featurizer, scorer, heuristic);
        }
      }

      if (withDTUs) {
        DTUHypothesis<TK, FV> dtuHyp = (DTUHypothesis<TK, FV>) hyp;
        if (!dtuHyp.isDone() || dtuHyp.hasExpired())
          System.err.printf("Warning: option not complete(%d,%s): %s\n",
              translations.size(), dtuHyp.hasExpired(), hyp);
      }

      if (DISTINCT_SURFACE_TRANSLATIONS) {

        // Get surface string:
        assert (hyp != null);
        AbstractSequence<TK> seq = (AbstractSequence<TK>) hyp.featurizable.targetPrefix;

        // If seen this string before and not among the top-k, skip it:
        if (hypCount > SAFE_LIST && distinctSurfaceTranslations.contains(seq)) {
          continue;
        }

        // Add current hypothesis to nbest list and set of uniq strings:
        Hypothesis<TK, FV> beamGoalHyp = hypList.get(hypList.size() - 1);
        translations.add(new RichTranslation<TK, FV>(hyp.featurizable,
            hyp.score, FeatureValues.combine(hyp), collectAlignments(hyp),
            beamGoalHyp.id));
        distinctSurfaceTranslations.add(seq);
        if (distinctSurfaceTranslations.size() >= size
            || hypCount >= maxDuplicateCount)
          break;

      } else {

        Hypothesis<TK, FV> beamGoalHyp = hypList.get(hypList.size() - 1);
        assert (hyp != null);
        
        translations.add(new RichTranslation<TK, FV>(hyp.featurizable,
            hyp.score, FeatureValues.combine(hyp), collectAlignments(hyp),
            beamGoalHyp.id));
        if (translations.size() >= size)
          break;

      }
    }

    // If a non-admissible recombination heuristic is used, the hypothesis
    // scores predicted by the lattice may not actually correspond to their real
    // scores.
    // Since the n-best list should be sorted according to the true scores, we
    // re-sort things here just in case.
    Collections.sort(translations, new Comparator<RichTranslation<TK, FV>>() {
      @Override
      public int compare(RichTranslation<TK, FV> o1, RichTranslation<TK, FV> o2) {
        return (int) Math.signum(o2.score - o1.score);
      }
    });

    assert (!translations.isEmpty());
    Iterator<RichTranslation<TK, FV>> listIterator = translations.iterator();
    Featurizable<TK, FV> featurizable = listIterator.next().featurizable;
    if (featurizable != null && featurizable.done && featurizer != null)
      featurizer.dump(featurizable);
    else
      System.err.println("Warning: 1-best not complete!");

    if (DEBUG) {
      long nBestConstructionTime = System.nanoTime() - nbestStartTime;
      System.err.printf("N-best generation time: %.3f seconds\n",
          nBestConstructionTime / 1e9);
    }

    featurizer.rerankingMode(false);

    if (DISTINCT_SURFACE_TRANSLATIONS) {
      List<RichTranslation<TK, FV>> dtranslations = new LinkedList<RichTranslation<TK, FV>>();
      distinctSurfaceTranslations.clear();
      for (RichTranslation<TK, FV> rt : translations) {
        if (distinctSurfaceTranslations.contains(rt.translation)) {
          continue;
        }
        distinctSurfaceTranslations.add(rt.translation);
        dtranslations.add(rt);
      }
      return dtranslations;
    } else {
      return translations;
    }
  }

  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> source,
      int sourceInputId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets) {
    return translate(scorer, source, sourceInputId, constrainedOutputSpace,
        targets);
  }

  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets) {
    Beam<Hypothesis<TK, FV>> beam = decode(scorer, source, sourceInputId,
        null, constrainedOutputSpace, targets, 1);
    if (beam == null)
      return null;
    Hypothesis<TK, FV> hyp = beam.iterator().next();
    return new RichTranslation<TK, FV>(hyp.featurizable, hyp.score,
        FeatureValues.combine(hyp));
  }

  /**
	 * 
	 */
  abstract protected Beam<Hypothesis<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      RecombinationHistory<Hypothesis<TK, FV>> recombinationHistory,
      ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int nbest);

  /**
   *
   */
  @SuppressWarnings("unchecked")
  protected Beam<Hypothesis<TK, FV>>[] createBeamsForCoverageCounts(
      int beamCnt, int capacity,
      RecombinationFilter<Hypothesis<TK, FV>> filter,
      RecombinationHistory<Hypothesis<TK, FV>> recombinationHistory) {
    Beam<Hypothesis<TK, FV>>[] beams = new Beam[beamCnt];
    for (int i = 0; i < beams.length; i++) {
      beams[i] = HypothesisBeamFactory.factory(beamType, filter, capacity,
          recombinationHistory);
    }
    return beams;
  }

  /**
   *
   */
  protected Beam<Hypothesis<TK, FV>>[] createBeamsForCoverageCounts(
      int beamCnt, int capacity, RecombinationFilter<Hypothesis<TK, FV>> filter) {
    @SuppressWarnings("unchecked")
    Beam<Hypothesis<TK, FV>>[] beams = new Beam[beamCnt];
    for (int i = 0; i < beams.length; i++) {
      beams[i] = HypothesisBeamFactory.factory(beamType, filter, capacity);
    }
    return beams;
  }

  /**
   * 
   * @author danielcer
   */
  public class CoverageBeams {
    final private Map<CoverageSet, Beam<Hypothesis<TK, FV>>> beams = new HashMap<CoverageSet, Beam<Hypothesis<TK, FV>>>();
    final private Set<CoverageSet>[] coverageCountToCoverageSets;
    final private RecombinationHistory<Hypothesis<TK, FV>> recombinationHistory;

    @SuppressWarnings("unchecked")
    public CoverageBeams(int sourceSize,
        RecombinationHistory<Hypothesis<TK, FV>> recombinationHistory) {
      coverageCountToCoverageSets = new Set[sourceSize + 1];
      for (int i = 0; i < sourceSize + 1; i++) {
        coverageCountToCoverageSets[i] = new HashSet<CoverageSet>();
      }
      this.recombinationHistory = recombinationHistory;
    }

    public void put(Hypothesis<TK, FV> hypothesis) {
      get(hypothesis.sourceCoverage).put(hypothesis);
    }

    private Beam<Hypothesis<TK, FV>> get(CoverageSet coverage) {
      Beam<Hypothesis<TK, FV>> beam = beams.get(coverage);
      if (beam == null) {
        beam = HypothesisBeamFactory.factory(beamType, filter, beamCapacity,
            recombinationHistory);
        beams.put(coverage, beam);
        int coverageCount = coverage.cardinality();
        coverageCountToCoverageSets[coverageCount].add(coverage);
      }
      return beam;
    }

    public List<Hypothesis<TK, FV>> getHypotheses(int coverageCount) {
      List<Hypothesis<TK, FV>> hypothesisList = new LinkedList<Hypothesis<TK, FV>>();

      for (CoverageSet coverage : coverageCountToCoverageSets[coverageCount]) {
        Beam<Hypothesis<TK, FV>> hypothesisBeam = get(coverage);
        for (Hypothesis<TK, FV> hypothesis : hypothesisBeam) {
          hypothesisList.add(hypothesis);
        }
      }

      return hypothesisList;
    }
  }

  abstract public void dump(Hypothesis<TK, FV> hyp);
}
