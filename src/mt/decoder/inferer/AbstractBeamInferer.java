package mt.decoder.inferer;

import java.util.*;

import mt.base.*;
import mt.decoder.recomb.*;
import mt.decoder.util.*;

/**
 *
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
abstract public class AbstractBeamInferer<TK, FV> extends AbstractInferer<TK, FV>  {
  static public final String DEBUG_OPT = "AbstractBeamInfererDebug";
  static public final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_OPT, "false"));
  static public final String DISTINCT_NBEST_OPT = "UniqNBest";
  static public final boolean DISTINCT_NBEST = Boolean.parseBoolean(System.getProperty(DISTINCT_NBEST_OPT, "false"));
  static public final String MAX_TIME_NBEST_OPT = "MaxTimeNBest";
  static public final long MAX_TIME_NBEST = Integer.parseInt(System.getProperty(MAX_TIME_NBEST_OPT,"60"))*1000;
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
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> foreign,
			int translationId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
			int size) {
  		return nbest(scorer, foreign, translationId, constrainedOutputSpace, size);  	
  }
  
  @Override
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer, Sequence<TK> foreign,
			int translationId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
			int size) {
		if (size > beamCapacity) {
			System.err
					.printf(
							"Warning: Requested nbest list size, %d, exceeds beam capacity of %d\n",
							size, beamCapacity);
		}
		RecombinationHistory<Hypothesis<TK, FV>> recombinationHistory = new RecombinationHistory<Hypothesis<TK, FV>>();

		Beam<Hypothesis<TK, FV>> beam = decode(scorer, foreign, translationId,
				recombinationHistory, constrainedOutputSpace, size);
		if (beam == null)
			return null;
		List<RichTranslation<TK, FV>> translations = new LinkedList<RichTranslation<TK, FV>>();

		List<Hypothesis<TK, FV>> goalStates = new ArrayList<Hypothesis<TK, FV>>(
				beam.size());
		for (Hypothesis<TK, FV> hyp : beam) {
			goalStates.add(hyp);
		}

		long nbestStartTime = System.currentTimeMillis();

		StateLatticeDecoder<Hypothesis<TK, FV>> latticeDecoder = new StateLatticeDecoder<Hypothesis<TK, FV>>(
				goalStates, recombinationHistory, size);

		for (List<Hypothesis<TK, FV>> hypList : latticeDecoder) {
			Hypothesis<TK, FV> hyp = null;
			for (Hypothesis<TK, FV> nextHyp : hypList) {
				if (hyp == null) {
					hyp = nextHyp;
					continue;
				}
				hyp = new Hypothesis<TK, FV>(translationId, nextHyp.translationOpt,
						hyp.length, hyp, featurizer, scorer, heuristic);
			}
			// System.err.printf("Translations size: %d (/%d)\n", translations.size(),
			// size);
			Hypothesis<TK, FV> beamGoalHyp = hypList.get(hypList.size() - 1);
			translations.add(new RichTranslation<TK, FV>(hyp.featurizable, hyp.score,
					collectFeatureValues(hyp), beamGoalHyp.id));
			if (translations.size() >= size)
				break;
		}

		// if a non-admissible recombination heuristic was used, the hypothesis
		// scores predicted by the
		// lattice may not actually correspond to their real scores.
		//
		// Since the n-best list should be sorted according to the true scores, we
		// re-sort things here just in case.
		Collections.sort(translations, new Comparator<RichTranslation<TK, FV>>() {
			@Override
			public int compare(RichTranslation<TK, FV> o1, RichTranslation<TK, FV> o2) {
				return (int) Math.signum(o2.score - o1.score);
			}
		});
		if (DEBUG) {
			long nBestConstructionTime = System.currentTimeMillis() - nbestStartTime;
			System.err.printf("N-best generation time: %.3f seconds\n",
					nBestConstructionTime / 1000.0);
		}
		return translations;
	}

  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace) {
  	return translate(scorer, foreign, translationId, constrainedOutputSpace);
  }
  
  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace) {
    Beam<Hypothesis<TK,FV>> beam = decode(scorer, foreign, translationId, null, constrainedOutputSpace, 1);
    if (beam == null) return null;
    Hypothesis<TK,FV> hyp = beam.iterator().next();
    return new RichTranslation<TK,FV>(hyp.featurizable, hyp.score, collectFeatureValues(hyp));
  }

  /**
	 * 
	 * @param foreign
	 * @param recombinationHistory
	 * @param constrainedOutputSpace
	 * @param nbest
	 * @return
	 */
  abstract protected Beam<Hypothesis<TK,FV>> decode(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory,
                                                    ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int nbest);

  /**
   *
   * @param beamCnt
   * @param capacity
   * @param filter
   * @param recombinationHistory
   * @return
   */
  @SuppressWarnings("unchecked")
  protected Beam<Hypothesis<TK,FV>>[] createBeamsForCoverageCounts(int beamCnt, int capacity, RecombinationFilter<Hypothesis<TK,FV>> filter,
                                                                   RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory) {
    Beam<Hypothesis<TK,FV>>[] beams = new Beam[beamCnt];
    for (int i = 0; i < beams.length; i++) {
      beams[i] = HypothesisBeamFactory.factory(beamType, filter, capacity, recombinationHistory);
    }
    return beams;
  }

  /**
   *
   * @param beamCnt
   * @param capacity
   * @param filter
   * @return
   */
  @SuppressWarnings("unchecked")
  protected Beam<Hypothesis<TK,FV>>[] createBeamsForCoverageCounts(int beamCnt, int capacity, RecombinationFilter<Hypothesis<TK,FV>> filter) {
    Beam<Hypothesis<TK,FV>>[] beams = new Beam[beamCnt];
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
    final private Map<CoverageSet, Beam<Hypothesis<TK,FV>>> beams =  new HashMap<CoverageSet, Beam<Hypothesis<TK,FV>>>();
    final private Set<CoverageSet>[] coverageCountToCoverageSets;
    final private RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory;

    @SuppressWarnings("unchecked")
    public CoverageBeams(int foreignSize, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory) {
      coverageCountToCoverageSets =  new Set[foreignSize+1];
      for (int i = 0; i < foreignSize+1; i++) {
        coverageCountToCoverageSets[i] = new HashSet<CoverageSet>();
      }
      this.recombinationHistory = recombinationHistory;
    }

    public void put(Hypothesis<TK,FV> hypothesis) {
      get(hypothesis.foreignCoverage).put(hypothesis);
    }

    private Beam<Hypothesis<TK,FV>> get(CoverageSet coverage) {
      Beam<Hypothesis<TK,FV>> beam = beams.get(coverage);
      if (beam == null) {
        beam = HypothesisBeamFactory.factory(beamType, filter, beamCapacity, recombinationHistory);
        beams.put(coverage, beam);
        int coverageCount = coverage.cardinality();
        coverageCountToCoverageSets[coverageCount].add(coverage);
      }
      return beam;
    }

    public List<Hypothesis<TK,FV>> getHypotheses(int coverageCount) {
      List<Hypothesis<TK,FV>> hypothesisList = new LinkedList<Hypothesis<TK,FV>>();

      for (CoverageSet coverage : coverageCountToCoverageSets[coverageCount]) {
        Beam<Hypothesis<TK,FV>> hypothesisBeam = get(coverage);
        for (Hypothesis<TK,FV> hypothesis : hypothesisBeam) {
          hypothesisList.add(hypothesis);
        }
      }

      return hypothesisList;
    }
  }

}



