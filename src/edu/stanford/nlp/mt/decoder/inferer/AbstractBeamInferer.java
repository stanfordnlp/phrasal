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
  static public boolean DISTINCT_NBEST = Boolean.parseBoolean(System.getProperty(DISTINCT_NBEST_OPT, "false"));
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
			int translationId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace, List<Sequence<TK>> targets,
			int size) {
  		return nbest(scorer, foreign, translationId, constrainedOutputSpace, targets, size);  	
  }
  
  
  @Override
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer, Sequence<TK> foreign,
			int translationId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace, List<Sequence<TK>> targets,
			int size) {
		if (size > beamCapacity) {
			System.err
					.printf(
							"Warning: Requested nbest list size, %d, exceeds beam capacity of %d\n",
							size, beamCapacity);
		}
		RecombinationHistory<Hypothesis<TK, FV>> recombinationHistory = new RecombinationHistory<Hypothesis<TK, FV>>();

		Beam<Hypothesis<TK, FV>> beam = decode(scorer, foreign, translationId,
				recombinationHistory, constrainedOutputSpace, targets, size);
		if (beam == null)
			return null;
		List<RichTranslation<TK, FV>> translations = new LinkedList<RichTranslation<TK, FV>>();

		List<Hypothesis<TK, FV>> goalStates = new ArrayList<Hypothesis<TK, FV>>(
				beam.size());
		for (Hypothesis<TK, FV> hyp : beam) {
			goalStates.add(hyp);
		}

		long nbestStartTime = System.currentTimeMillis();

    Set<Sequence<TK>> distinctTranslations = DISTINCT_NBEST ? new HashSet<Sequence<TK>>() : null;

    featurizer.rerankingMode(true);

    StateLatticeDecoder<Hypothesis<TK, FV>> latticeDecoder = new StateLatticeDecoder<Hypothesis<TK, FV>>(
				goalStates, recombinationHistory, size);

    int hypCount = 0, duplicateCount = 0, maxDuplicateCount = size*MAX_DUPLICATE_FACTOR;

    for (List<Hypothesis<TK, FV>> hypList : latticeDecoder) {
      boolean withDTUs = false;
      ++hypCount;
      Hypothesis<TK, FV> hyp = null;
      Set<TranslationOption> seenOptions = new HashSet<TranslationOption>();
      for (Hypothesis<TK, FV> nextHyp : hypList) {
        if (hyp == null) {
					hyp = nextHyp;
					continue;
				}
        if(nextHyp.translationOpt.abstractOption instanceof DTUOption)
          withDTUs = true;
        if(withDTUs) {
          hyp = new DTUHypothesis<TK, FV>(translationId, nextHyp.translationOpt,
              hyp.length, hyp, nextHyp, featurizer, scorer, heuristic, seenOptions);
        } else {
          hyp = new Hypothesis<TK, FV>(translationId, nextHyp.translationOpt,
              hyp.length, hyp, featurizer, scorer, heuristic);
        }
      }

      if(withDTUs) {
        DTUHypothesis dtuHyp = (DTUHypothesis<TK,FV>)hyp;
        if(!dtuHyp.isDone() || dtuHyp.hasExpired())
          System.err.printf ("WARNING: option not complete(%d,%s): %s\n",translations.size(), dtuHyp.hasExpired(), hyp);
      }

      if(hyp != null && hyp.isDone() != hyp.featurizable.done) {
        System.err.println("ERROR in AbstractBeamInferer with: "+hyp);
        System.err.println("isDone(): "+hyp.isDone());
        System.err.println("f.done: "+hyp.featurizable.done);
        Hypothesis<TK,FV> curHyp = hyp;
        while (curHyp != null) {
          System.err.println("  "+curHyp.toString());
          curHyp = curHyp.preceedingHyp;
        }
        //throw new RuntimeException();
      }

      /////////////////////////////////////////////////////////////////////////////////////////////////////
      // code below is needed for generating nbest lists with no duplicates for GALE -- please do not delete
      /////////////////////////////////////////////////////////////////////////////////////////////////////

      if(distinctTranslations != null) {

        // Avoid spending too much time generating the nbest list (when can take dozens of minutes for very
        // long inputs):
        if(hypCount > SAFE_LIST && (hypCount % 100 == 0)) {
          long curTime = System.currentTimeMillis();
          if(++duplicateCount >= maxDuplicateCount || curTime-nbestStartTime > MAX_TIME_NBEST) {
            System.err.printf("\nNbest list construction taking too long (hyps=%d, uniq-hyps=%d, nbest=%d, time=%fs); giving up.\n", 
              hypCount, duplicateCount, translations.size(), (curTime-nbestStartTime)/1000.0);
            break;
          }
        }
        // Get surface string:
        AbstractSequence<TK> seq = (AbstractSequence<TK>) hyp.featurizable.partialTranslation;
        // If seen this string before and not among the top-k, skip it:
        if(hypCount > SAFE_LIST && distinctTranslations.contains(seq)) 
          continue;
        // Add current hypothesis to nbest list and set of uniq strings:
        Hypothesis<TK, FV> beamGoalHyp = hypList.get(hypList.size() - 1);
        translations.add(new RichTranslation<TK, FV>(hyp.featurizable, hyp.score,
            collectFeatureValues(hyp), beamGoalHyp.id));
        distinctTranslations.add(seq);
        if (distinctTranslations.size() >= size) 
          break;

      } else {

        /////////////////////////////////////////////////////////////////////////////////////////////////////
        // code above is needed for generating nbest lists with no duplicates for GALE -- please do not delete
        /////////////////////////////////////////////////////////////////////////////////////////////////////

        // System.err.printf("Translations size: %d (/%d)\n", translations.size(),
        // size);
        Hypothesis<TK, FV> beamGoalHyp = hypList.get(hypList.size() - 1);
        translations.add(new RichTranslation<TK, FV>(hyp.featurizable, hyp.score,
            collectFeatureValues(hyp), beamGoalHyp.id));
        if (translations.size() >= size)
          break;

      }
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

    Iterator<RichTranslation<TK, FV>> listIterator = translations.iterator();
    featurizer.debugBest(listIterator.next().featurizable);

    if (DEBUG) {
			long nBestConstructionTime = System.currentTimeMillis() - nbestStartTime;
			System.err.printf("N-best generation time: %.3f seconds\n",
					nBestConstructionTime / 1000.0);
		}

    featurizer.rerankingMode(false);

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    // code below is needed for generating nbest lists with no duplicates for GALE -- please do not delete
    /////////////////////////////////////////////////////////////////////////////////////////////////////

    if(distinctTranslations != null) {
      List<RichTranslation<TK, FV>> dtranslations = new LinkedList<RichTranslation<TK, FV>>();
      distinctTranslations.clear();
      for(RichTranslation<TK,FV> rt : translations) {
				if(distinctTranslations.contains(rt.translation)) {
					continue;
				}
				distinctTranslations.add(rt.translation);
				dtranslations.add(rt);
			}
			return dtranslations;
    } else {
      return translations;
    }
  }

  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets) {
  	return translate(scorer, foreign, translationId, constrainedOutputSpace, targets);
  }
  
  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets) {
    Beam<Hypothesis<TK,FV>> beam = decode(scorer, foreign, translationId, null, constrainedOutputSpace, targets, 1);
    if (beam == null) return null;
    Hypothesis<TK,FV> hyp = beam.iterator().next();
    return new RichTranslation<TK,FV>(hyp.featurizable, hyp.score, collectFeatureValues(hyp));
  }

  /**
	 * 
	 */
  abstract protected Beam<Hypothesis<TK,FV>> decode(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory,
                                                    ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets, int nbest);

  /**
   *
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



