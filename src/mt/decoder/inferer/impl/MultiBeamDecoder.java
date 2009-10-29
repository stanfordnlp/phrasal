package mt.decoder.inferer.impl;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mt.base.*;
import mt.decoder.inferer.*;
import mt.decoder.recomb.*;
import mt.decoder.util.*;
import mt.decoder.feat.RichIncrementalFeaturizer;
import mt.PseudoMoses;

import edu.stanford.nlp.stats.ClassicCounter;


/**
 * 
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class MultiBeamDecoder<TK, FV> extends AbstractBeamInferer<TK, FV> {
	// class level constants
	public static final String DEBUG_PROPERTY = "MultiBeamDecoderDebug";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	private static final String OPTIONS_PROPERTY = "PrintTranslationOptions";
	public static final boolean OPTIONS_DUMP = Boolean.parseBoolean(System.getProperty(OPTIONS_PROPERTY, "false"));
  public static final String DETAILED_DEBUG_PROPERTY = "MultiBeamDecoderDetailedDebug";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System.getProperty(DETAILED_DEBUG_PROPERTY, "false"));
  public static final String  ALIGNMENT_DUMP = System.getProperty("a");
	public static final int DEFAULT_BEAM_SIZE = 200;
	public static final HypothesisBeamFactory.BeamType DEFAULT_BEAM_TYPE = HypothesisBeamFactory.BeamType.treebeam; 
	public static final int DEFAULT_MAX_DISTORTION = -1;
	public static final boolean DEFAULT_USE_ITG_CONSTRAINTS = false;
	
	public final boolean useITGConstraints;
	final int maxDistortion;
	final int numProcs; 
	
	static {
		if (ALIGNMENT_DUMP != null) {
			(new File(ALIGNMENT_DUMP)).delete();
		}
	}
	
	
	ExecutorService threadPool;

	static public <TK,FV> MultiBeamDecoderBuilder<TK,FV> builder() {
		return new MultiBeamDecoderBuilder<TK,FV>();
	}
	
	protected MultiBeamDecoder(MultiBeamDecoderBuilder<TK, FV> builder) {
		super(builder);
		maxDistortion = builder.maxDistortion;
		useITGConstraints = builder.useITGConstraints;

		if (builder.internalMultiThread) {
			numProcs = (System.getProperty("numProcs") != null ?
                  Integer.parseInt(System.getProperty("numProcs")) :
                  Runtime.getRuntime().availableProcessors());
		} else {
			numProcs = 1;
		}
 		threadPool = Executors.newFixedThreadPool(numProcs); 

		if (useITGConstraints) {
			System.err.printf("Using ITG Constraints\n");
		} else {
			System.err.printf("Not using ITG Constraints\n");
		}
		if (maxDistortion != -1) {
			System.err.printf("Using distortion limit: %d\n", maxDistortion);
		} else {
			System.err.printf("No hard distortion limit\n");
		}
	}
	
	public static class MultiBeamDecoderBuilder<TK,FV> extends AbstractBeamInfererBuilder<TK,FV> {
		int maxDistortion = DEFAULT_MAX_DISTORTION;
		boolean useITGConstraints = DEFAULT_USE_ITG_CONSTRAINTS;
		boolean internalMultiThread;

    @Override
		public AbstractBeamInfererBuilder<TK,FV> setInternalMultiThread(boolean internalMultiThread) {
			this.internalMultiThread = internalMultiThread;
			return this;
    }

    @Override
    public AbstractBeamInfererBuilder<TK,FV> setMaxDistortion(int maxDistortion) {
			this.maxDistortion = maxDistortion;
			return this;
		}

    @Override
    public AbstractBeamInfererBuilder<TK,FV> useITGConstraints(boolean useITGConstraints) {
			this.useITGConstraints = useITGConstraints;
			return this;
		}
		
		public MultiBeamDecoderBuilder() {
			super(DEFAULT_BEAM_SIZE, DEFAULT_BEAM_TYPE);
		}

		@Override
		public Inferer<TK, FV> build() {
			return new MultiBeamDecoder<TK,FV>(this);
		}
	}
	
	private void displayBeams(Beam<Hypothesis<TK,FV>>[] beams) {
		System.err.print("Stack sizes: ");
		for (int si = 0; si < beams.length; si++) {
			if (si != 0) System.err.print(",");
			System.err.printf(" %d", beams[si].size());
		}
		System.err.println();	
	}
	
	private Runtime rt = Runtime.getRuntime();
	
	@Override
	@SuppressWarnings("unchecked")
	protected Beam<Hypothesis<TK,FV>> decode(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory,
			ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets, int nbest) {
		featurizer.reset();
		int foreignSz = foreign.size(); 
		BufferedWriter alignmentDump = null;
		
		if (ALIGNMENT_DUMP != null) {
			try {
			alignmentDump = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ALIGNMENT_DUMP, true)));
			} catch(Exception e) {
				alignmentDump = null;
			}
		}
		
		// create beams
		if (DEBUG) System.err.println("Creating beams");
		Beam<Hypothesis<TK,FV>>[] beams = createBeamsForCoverageCounts(foreign.size()+1, beamCapacity, filter, recombinationHistory);
		
		// retrieve translation options
		if (DEBUG) System.err.println("Generating Translation Options");
		
		List<ConcreteTranslationOption<TK>> options =  
					phraseGenerator.translationOptions(foreign, targets, translationId);	
		
		System.err.printf("Translation options: %d\n", options.size());
		
		if (OPTIONS_DUMP || DETAILED_DEBUG) {
      int sentId = translationId + ((PseudoMoses.local_procs > 1) ? 2:0);
      synchronized(System.err) {
        System.err.print(">> Translation Options <<\n");
        for (ConcreteTranslationOption<TK> option : options)
          System.err.printf("%s ||| %s ||| %s ||| %s ||| %s\n",
             sentId, option.abstractOption.foreign, option.abstractOption.translation,
             option.isolationScore, option.foreignCoverage);
        System.err.println(">> End translation options <<");
      }
    }
		
		if (constrainedOutputSpace != null) {
			options = constrainedOutputSpace.filterOptions(options);
			System.err.printf("Translation options after reduction by output space constraint: %d\n", options.size());
		}
		
		OptionGrid<TK> optionGrid = new OptionGrid<TK>(options, foreign);
		
		// insert initial hypothesis
		Hypothesis<TK,FV> nullHyp = new Hypothesis<TK,FV>(translationId, foreign, heuristic, options);
		beams[0].put(nullHyp);
		if (DEBUG) {
			System.err.printf("Estimated Future Cost: %e\n", nullHyp.h);
		}
		
		if (DEBUG) System.err.println("MultiBeamDecorder translating loop");
		
		int totalHypothesesGenerated = 1;

		featurizer.initialize(options, foreign);
		
		// main translation loop
		System.err.printf("Decoding with %d threads\n", numProcs);
		
		long decodeLoopTime = -System.currentTimeMillis();
		for (int i = 0; i < beams.length; i++) {

			//List<ConcreteTranslationOption<TK>> applicableOptions = ConcreteTranslationOptions.filterOptions(HypothesisBeams.coverageIntersection(beams[i]), foreign.size(), options);			
			if (DEBUG) { 
				System.err.printf("--\nDoing Beam %d Entries: %d\n", i, beams[i].size());
				System.err.printf("Total Memory Usage: %d MiB", (rt.totalMemory() - rt.freeMemory())/(1024*1024));
			}
			/*
			System.err.printf("Hypotheses:\n---------------\n");
			for (Hypothesis<TK, FV> hyp : beams[i]) {
				System.err.printf("%s\n", hyp);
			}
			System.err.println("done---------------\n");
			*/
			if (DEBUG) System.err.println();
			
			CountDownLatch cdl = new CountDownLatch(numProcs);
			for (int threadId = 0; threadId < numProcs; threadId++) {
				BeamExpander beamExpander = new BeamExpander(beams, i, foreignSz, optionGrid, constrainedOutputSpace, translationId, threadId, numProcs, cdl);
				threadPool.execute(beamExpander);
			}
			try { cdl.await(); } catch (Exception e) { throw new RuntimeException(e); }
			
			if (DEBUG) {
				displayBeams(beams);
				System.err.printf("--------------------------------\n");
			}												
		}
		decodeLoopTime += System.currentTimeMillis();
		System.err.printf("Decoding loop time: %f s\n", decodeLoopTime/1000.0);
		
		if (DEBUG) {
			int recombined = 0;
			int preinsertionDiscarded = 0;
			int pruned = 0;
			for (Beam<Hypothesis<TK,FV>> beam : beams) {
				recombined += beam.recombined();
				preinsertionDiscarded += beam.preinsertionDiscarded();
				pruned += beam.pruned();
			}
			System.err.printf("Stats:\n");
			System.err.printf("\ttotal hypotheses generated: %d\n", totalHypothesesGenerated);
			System.err.printf("\tcount recombined : %d\n", recombined);
			System.err.printf("\tnumber pruned: %d\n", pruned);
			System.err.printf("\tpre-insertion discarded: %d\n", preinsertionDiscarded);
			
		
			
			int beamIdx = beams.length-1;
			for ( ; beamIdx >= 0; beamIdx--) {
				if (beams[beamIdx].size() != 0) break;
			}
			Hypothesis<TK, FV> bestHyp = beams[beamIdx].iterator().next();
			
			List<Hypothesis<TK,FV>> trace = new ArrayList<Hypothesis<TK,FV>>();
			for (Hypothesis<TK, FV> hyp = bestHyp; hyp != null; hyp = hyp.preceedingHyp) {
				trace.add(hyp);
			}
			Collections.reverse(trace);
			
			ClassicCounter finalFeatureVector = new ClassicCounter();
			if (bestHyp.featurizable != null) {
				System.err.printf("hyp: %s\n",  bestHyp.featurizable.partialTranslation);
				System.err.printf("score: %e\n", bestHyp.score());
				System.err.printf("Trace:\n");
				System.err.printf("--------------\n");
				List<FeatureValue<FV>> allfeatures = new ArrayList<FeatureValue<FV>>();
				for (Hypothesis<TK,FV> hyp : trace) {
					System.err.printf("%d:\n", hyp.id);
					if (hyp.translationOpt != null) {
						System.err.printf("\tPhrase: %s(%d) => %s(%d)",
								hyp.translationOpt.abstractOption.foreign, 
								hyp.featurizable.foreignPosition, 
								hyp.translationOpt.abstractOption.translation,
								hyp.featurizable.translationPosition);
					}
					System.err.printf("\tCoverage: %s\n", hyp.foreignCoverage);
					System.err.printf("\tFeatures: %s\n", hyp.localFeatures);
					if (hyp.localFeatures != null) {
						for (FeatureValue<FV> featureValue : hyp.localFeatures) {
							finalFeatureVector.incrementCount(featureValue.name.toString(), featureValue.value);
							allfeatures.add(featureValue);
						}
					}
				}
	
				System.err.printf("\n\nFeatures: %s\n", finalFeatureVector);
				System.err.println();
				System.err.printf("Best hyp score: %.4f\n", bestHyp.finalScoreEstimate());
				System.err.printf("true score: %.3f h: %.3f\n", bestHyp.score, bestHyp.h);
				System.err.println();
				
        if(featurizer instanceof RichIncrementalFeaturizer)
          ((RichIncrementalFeaturizer)featurizer).debugBest(bestHyp.featurizable);
        
        double score = scorer.getIncrementalScore(allfeatures);
				System.err.printf("Recalculated score: %.3f\n", score);
			} else {
				System.err.printf("Only null hypothesis was produced.\n");
			}
		}
		
		
		for (int i = beams.length -1; i >= 0; i--) {
			if (beams[i].size() != 0 && (constrainedOutputSpace == null || constrainedOutputSpace.allowableFinal(beams[i].iterator().next().featurizable))) {
					Hypothesis<TK, FV> bestHyp = beams[i].iterator().next();
					try { writeAlignments(alignmentDump, bestHyp); } catch (Exception e) { }
					try { alignmentDump.close(); } catch (Exception e) { }
          if(DEBUG) System.err.println("Returning beam of size: "+beams[i].size());
          return beams[i];
			}
		} 
		
		try{
		alignmentDump.append("<<< decoder failure >>>\n\n");
		alignmentDump.close(); } catch (Exception e) { }
		
		return null;
	}
	
  class BeamExpander implements Runnable{
  	Beam<Hypothesis<TK,FV>>[] beams; int beamId; int foreignSz; OptionGrid<TK> optionGrid; ConstrainedOutputSpace<TK,FV> constrainedOutputSpace; int translationId; int threadId; int threadCount;
  	CountDownLatch cdl;
  	
  public  BeamExpander(Beam<Hypothesis<TK,FV>>[] beams, int beamId, int foreignSz, OptionGrid<TK> optionGrid, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int translationId, int threadId, int threadCount, CountDownLatch cdl) {
  	this.beams = beams;
  	this.beamId = beamId;
  	this.foreignSz = foreignSz;
  	this.optionGrid = optionGrid;
  	this.constrainedOutputSpace = constrainedOutputSpace;
  	this.translationId = translationId;
  	this.threadId = threadId;
  	this.threadCount = threadCount;
  	this.cdl = cdl;
  }
  
  @Override
	public void run() {
		if(DETAILED_DEBUG) System.err.printf("starting beam expander: %s thread pool: %s\n", this, threadPool);
    expandBeam(beams, beamId, foreignSz, optionGrid, constrainedOutputSpace, translationId, threadId, threadCount, cdl);
    if(DETAILED_DEBUG) System.err.printf("ending beam expander: %s thread pool: %s\n", this, threadPool);
	}
  
  @SuppressWarnings("unchecked")
	public int expandBeam(Beam<Hypothesis<TK,FV>>[] beams, int beamId, int foreignSz, OptionGrid<TK> optionGrid, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int translationId, int threadId, int threadCount, CountDownLatch cdl) {
		int optionsApplied = 0;
		int hypPos = -1;
		int totalHypothesesGenerated = 0;
	
		Hypothesis<TK, FV>[] hyps;
		synchronized(beams[beamId]) {
		  hyps = new Hypothesis[beams[beamId].size()]; 	
			int i = -1;
		  for (Hypothesis<TK, FV> hyp : beams[beamId]) { i++;
				hyps[i] = hyp;
			}
		}


		for (Hypothesis<TK, FV> hyp : hyps) {
		  hypPos++;
			if (hypPos % threadCount != threadId) continue;
			if (hyp == null) continue;
			//System.err.printf("\nExpanding hyp: %s\n", hyp);
			int localOptionsApplied = 0;
		    //	System.err.printf("Start position: %d\n", hyp.foreignCoverage.nextClearBit(0));
			int firstCoverageGap = hyp.foreignCoverage.nextClearBit(0);
			int priorStartPos = (hyp.featurizable == null ? 0 : hyp.featurizable.foreignPosition);
			int priorEndPos   = (hyp.featurizable == null ? 0 : hyp.featurizable.foreignPosition + hyp.featurizable.foreignPhrase.size());
			
			for (int startPos = firstCoverageGap; startPos < foreignSz; startPos++) {
				int endPosMax = hyp.foreignCoverage.nextSetBit(startPos);
				
				// check distortion limit					
				if (endPosMax < 0) {
					if (maxDistortion >= 0 && startPos != firstCoverageGap) {
						endPosMax = Math.min(firstCoverageGap + maxDistortion+1, foreignSz);
					} else {
						endPosMax = foreignSz;
					}
				}
				if (useITGConstraints) {
					boolean ITGOK = true;						
					if (startPos > priorStartPos) {
						// System.err.printf("startPos(%d) > priorStartPos(%d)\n", startPos, priorStartPos);
						for (int pos = priorEndPos+1; pos < startPos; pos++) {
							if (hyp.foreignCoverage.get(pos) && !hyp.foreignCoverage.get(pos-1)) {
								//System.err.printf("not okay-1 %d & %d - %s\n" ,pos, pos-1, hyp.foreignCoverage);
								ITGOK = false;
								break;
							}
						}
					} else {
						//System.err.printf("startPos(%d) < priorStartPos(%d)\n", startPos, priorStartPos);
						for (int pos = startPos; pos < priorStartPos; pos++) {
							if (hyp.foreignCoverage.get(pos) && !hyp.foreignCoverage.get(pos+1)) {
							//	System.err.printf("not okay-2 %d & %d - %s\n" ,pos, pos+1, hyp.foreignCoverage);
								ITGOK = false;
								break;
							}
						}
					}
					if (!ITGOK) continue;
					//System.err.printf("okay\n");
				}
				for (int endPos = startPos; endPos < endPosMax; endPos++) {
					List<ConcreteTranslationOption<TK>> applicableOptions = optionGrid.get(startPos, endPos);
					if (applicableOptions == null) continue;
					// System.err.printf("options for (%d to %d): %d\n", startPos, endPos, applicableOptions.size());
			
					for (ConcreteTranslationOption<TK> option : applicableOptions) {
						assert(!hyp.foreignCoverage.intersects(option.foreignCoverage));

						if (constrainedOutputSpace != null && !constrainedOutputSpace.allowableContinuation(hyp.featurizable, option)) {
							continue;
						}
						
					
						/* if (option.abstractOption.phraseScoreNames[0] == UnknownWordFeaturizer.UNKNOWN_PHRASE_TAG && 
							hypNextForeignPos != startPos) { 
								continue; // lets not allow untranslated foreign phrases to float around randomly
						} */
						
						Hypothesis<TK, FV> newHyp = new Hypothesis<TK, FV>(translationId,
								option, hyp.length, hyp, featurizer,
								scorer, heuristic);
						
						if (DETAILED_DEBUG) {
							System.err.printf("creating hypothesis %d from %d\n", newHyp.id, hyp.id);
							System.err.printf("hyp: %s\n", newHyp.featurizable.partialTranslation);
							System.err.printf("coverage: %s\n", newHyp.foreignCoverage);
							if (hyp.featurizable != null) { 
								System.err.printf("par: %s\n", hyp.featurizable.partialTranslation);
								System.err.printf("coverage: %s\n", hyp.foreignCoverage);
							}
							System.err.printf("\tbase score: %.3f\n", hyp.score);
							System.err.printf("\tcovering: %s\n", newHyp.translationOpt.foreignCoverage);
							System.err.printf("\tforeign: %s\n", newHyp.translationOpt.abstractOption.foreign);
							System.err.printf("\ttranslated as: %s\n", newHyp.translationOpt.abstractOption.translation);
							System.err.printf("\tscore: %.3f + future cost %.3f = %.3f\n", newHyp.score, newHyp.h, newHyp.score());
							
						}
						totalHypothesesGenerated++;
						
						
						
						if (newHyp.featurizable.untranslatedTokens != 0) {
							if (constrainedOutputSpace != null && !constrainedOutputSpace.allowablePartial(newHyp.featurizable)) {
								continue;
							}
						} else {
							// System.err.printf("checking final %s\n", newHyp.featurizable.partialTranslation);
							if (constrainedOutputSpace != null && !constrainedOutputSpace.allowableFinal(newHyp.featurizable)) {
								// System.err.printf("bad final: %s\n", newHyp.featurizable.partialTranslation);
								continue;
							}
						}
						
						
						
						if (newHyp.score == Double.NEGATIVE_INFINITY || newHyp.score == Double.POSITIVE_INFINITY || 
								newHyp.score != newHyp.score) {
							// should we give a warning here? 
							// 
							// this normally happens when there's something brain dead about the user's baseline model/featurizers,
							// like log(p) values that equal -inf for some featurizers. 
							continue;
						}
						
						int foreignWordsCovered = newHyp.foreignCoverage.cardinality();
						beams[foreignWordsCovered].put(newHyp);

						optionsApplied++;
						localOptionsApplied++;
					}
					
				
				}					
			}
			if (DETAILED_DEBUG) {
				System.err.printf("local options applied(%d): %d\n", hypPos, localOptionsApplied);
			}
		}
		
		if (DEBUG) {
			System.err.printf("Options applied: %d\n", optionsApplied);
		}


		cdl.countDown();
		return totalHypothesesGenerated;	
	}

	
  }
  
	void writeAlignments(BufferedWriter alignmentDump, Hypothesis<TK, FV> bestHyp) throws IOException {
		alignmentDump.append(bestHyp.featurizable.partialTranslation.toString()).append("\n");
		alignmentDump.append(bestHyp.featurizable.foreignSentence.toString()).append("\n");
		for (Hypothesis<TK, FV> hyp = bestHyp; hyp.featurizable != null; hyp = hyp.preceedingHyp) {
			alignmentDump.append(String.format("%d:%d => %d:%d # %s => %s\n", hyp.featurizable.foreignPosition, hyp.featurizable.foreignPosition+hyp.featurizable.foreignPhrase.size()-1,
					                                            hyp.featurizable.translationPosition, hyp.featurizable.translationPosition + hyp.featurizable.translatedPhrase.size()-1,
					                                            hyp.featurizable.foreignPhrase, hyp.featurizable.translatedPhrase));
		}
		alignmentDump.append("\n");
	}
}

