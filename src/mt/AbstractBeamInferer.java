package mt;

import java.util.*;

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
  public final int beamCapacity;
  public final HypothesisBeamFactory.BeamType beamType;

  protected AbstractBeamInferer(AbstractBeamInfererBuilder<TK, FV> builder) {
    super(builder);
    this.beamCapacity = builder.beamCapacity;
    this.beamType = builder.beamType;
  }

  @Override
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int size) {
    /*if (size > beamCapacity) {
                        System.err.printf("Warning: Requested nbest list size, %d, exceeds beam capacity of %d\n", size, beamCapacity);
                }
                RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory = new RecombinationHistory<Hypothesis<TK,FV>>();

                Beam<Hypothesis<TK,FV>> beam = decode(foreign, translationId, recombinationHistory, constrainedOutputSpace, size);
                if (beam == null) return null;
                List<RichTranslation<TK,FV>> translations = new LinkedList<RichTranslation<TK,FV>>();

                List<Hypothesis<TK,FV>> goalStates = new ArrayList<Hypothesis<TK,FV>>(beam.size());
                for (Hypothesis<TK,FV> hyp : beam) {
                        goalStates.add(hyp);
                }

                long nbestStartTime = System.currentTimeMillis();

                StateLatticeDecoder<Hypothesis<TK,FV>> latticeDecoder = new StateLatticeDecoder<Hypothesis<TK,FV>>(goalStates, recombinationHistory, size);

                for (List<Hypothesis<TK,FV>> hypList : latticeDecoder) {
                        Hypothesis<TK,FV> hyp = null;
                        for (Hypothesis<TK,FV> nextHyp : hypList) {
                                if (hyp == null) {
                                        hyp = nextHyp;
                                        continue;
                                }
                                hyp = new Hypothesis<TK, FV>(translationId,
                                                nextHyp.translationOpt, hyp.length, hyp, featurizer,
                                                scorer, heuristic);
                        }
                        //System.err.printf("Translations size: %d (/%d)\n", translations.size(), size);
                        Hypothesis<TK,FV> beamGoalHyp = hypList.get(hypList.size()-1);
                        translations.add(new RichTranslation<TK,FV>(hyp.featurizable, hyp.score, collectFeatureValues(hyp), beamGoalHyp.id));
                        if (translations.size() >= size) break;
                }

                // if a non-admissible recombination heuristic was used, the hypothesis scores predicted by the
                // lattice may not actually correspond to their real scores.
                //
                // Since the n-best list should be sorted according to the true scores, we re-sort things here just in case.
                Collections.sort(translations, new Comparator<RichTranslation<TK,FV>>() {
                        @Override
                        public int compare(RichTranslation<TK, FV> o1,
                                        RichTranslation<TK, FV> o2) {
                                return (int)Math.signum(o2.score - o1.score);
                        } });
                if (DEBUG) {
                        long nBestConstructionTime = System.currentTimeMillis() - nbestStartTime;
                        System.err.printf("N-best generation time: %.3f seconds\n", nBestConstructionTime/1000.0);
                }
                return translations; */
    return nbestNBad(foreign, translationId, constrainedOutputSpace, size, 0).get(0);
  }

  public List<List<RichTranslation<TK, FV>>> nbestNBad(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int nbestSize, int nBadSize) {
    RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory = new RecombinationHistory<Hypothesis<TK,FV>>();

    Beam<Hypothesis<TK,FV>> beam = decode(foreign, translationId, recombinationHistory, constrainedOutputSpace, nbestSize);
    if (beam == null) return null;

    List<Hypothesis<TK,FV>> goalStates = new ArrayList<Hypothesis<TK,FV>>(beam.size());
    for (Hypothesis<TK,FV> hyp : beam) {
      // System.err.printf("Collecting goal state score: %e\n", hyp.score);
      goalStates.add(hyp);
    }

    long nbestStartTime = System.currentTimeMillis();
    List<List<RichTranslation<TK, FV>>> nbestNWorst = new ArrayList<List<RichTranslation<TK, FV>>>();

    for (int i = 0; i < 2; i++) {
      List<RichTranslation<TK,FV>> translations = new LinkedList<RichTranslation<TK,FV>>();

      int cnt = (i == 0 ? nbestSize : nBadSize);
      if (cnt == 0) {
        nbestNWorst.add(new LinkedList<RichTranslation<TK,FV>>());
        continue;
      }
      StateLatticeDecoder<Hypothesis<TK,FV>> latticeDecoder =
              new StateLatticeDecoder<Hypothesis<TK,FV>>(goalStates, recombinationHistory, cnt, i == 1); // XXX-

      //int c = -1;
      //double mod = 1;
      long basetime = System.currentTimeMillis();
      for (List<Hypothesis<TK,FV>> hypList : latticeDecoder) {
        Hypothesis<TK,FV> hyp = null;
        for (Hypothesis<TK,FV> nextHyp : hypList) {
          if (hyp == null) {
            hyp = nextHyp;
            continue;
          }
          hyp = new Hypothesis<TK, FV>(translationId,
                  nextHyp.translationOpt, hyp.length, hyp, featurizer,
                  scorer, heuristic);
        }
        /*
                                 if (mod != 0 && c % (int)mod != 0) {
                                         c++;
                                         //System.err.printf("skipping on %d mod %d\n", c, mod);
                                         continue; // XXX-
                                 } else {
                                         System.err.printf("collection on %d mod %f (%d)\n", c, mod, (int)mod);
                                         c++;
                                         mod = mod == 0 ? 1 : mod * 1.1;

                                 } */
        //System.err.printf("Translations size: %d (/%d)\n", translations.size(), size);
        Hypothesis<TK,FV> beamGoalHyp = hypList.get(hypList.size()-1);
        // System.err.printf("Adding to n-best list score: %e  (parent %e)\n", hyp.score, beamGoalHyp.score);
        // System.err.printf("translation: %s\n", hyp.featurizable.partialTranslation);
        // System.err.printf("parent: %s\n", beamGoalHyp.featurizable.partialTranslation);
        translations.add(new RichTranslation<TK,FV>(hyp.featurizable, hyp.score, collectFeatureValues(hyp), beamGoalHyp.id));
        // System.err.printf("n-best translations: %d %d (%d)\n", translations.size(), (System.currentTimeMillis()-basetime)/1000, latticeDecoder.agenda.size());
        if (translations.size() >= nbestSize) break;
      }

      // if a non-admissible recombination heuristic was used, the hypothesis scores predicted by the
      // lattice may not actually correspond to their real scores.
      //
      // Since the n-best list should be sorted according to the true scores, we re-sort things here just in case.
      Collections.sort(translations, new Comparator<RichTranslation<TK,FV>>() {

        public int compare(RichTranslation<TK, FV> o1,
                           RichTranslation<TK, FV> o2) {
          return (int)Math.signum(o2.score - o1.score);
        } });

      nbestNWorst.add(translations);
    }


    long nBestConstructionTime = System.currentTimeMillis() - nbestStartTime;
    System.err.printf("N-best generation time: %.3f seconds\n", nBestConstructionTime/1000.0);

    return nbestNWorst;
  }

  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace) {
    Beam<Hypothesis<TK,FV>> beam = decode(foreign, translationId, null, constrainedOutputSpace, 1);
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
  abstract protected Beam<Hypothesis<TK,FV>> decode(Sequence<TK> foreign, int translationId, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory,
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
    Beam<Hypothesis<TK,FV>>[] beams = (Beam<Hypothesis<TK,FV>>[])new Beam[beamCnt];
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
    Beam<Hypothesis<TK,FV>>[] beams = (Beam<Hypothesis<TK,FV>>[])new Beam[beamCnt];
    for (int i = 0; i < beams.length; i++) {
      beams[i] = HypothesisBeamFactory.factory(beamType, filter, capacity);
    }
    return beams;
  }

  /**
   *
   * @author danielcer
   */
  class CoverageBeams {
    final private Map<CoverageSet, Beam<Hypothesis<TK,FV>>> beams =  new HashMap<CoverageSet, Beam<Hypothesis<TK,FV>>>();
    final private Set<CoverageSet>[] coverageCountToCoverageSets;
    final private RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory;

    @SuppressWarnings("unchecked")
    public CoverageBeams(int foreignSize, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory) {
      coverageCountToCoverageSets =  (Set<CoverageSet>[])new Set[foreignSize+1];
      for (int i = 0; i < foreignSize+1; i++) {
        coverageCountToCoverageSets[i] = new HashSet<CoverageSet>();
      }
      this.recombinationHistory = recombinationHistory;
    }

    void put(Hypothesis<TK,FV> hypothesis) {
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

    List<Hypothesis<TK,FV>> getHypotheses(int coverageCount) {
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



