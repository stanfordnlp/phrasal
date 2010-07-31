// Phrasal -- A Statistical Machine Translation Toolkit
// for Exploring New Model Features.
// Copyright (c) 2007-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-user@lists.stanford.edu
//    http://nlp.stanford.edu/software/phrasal

package edu.stanford.nlp.mt.decoder.inferer.impl;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.inferer.*;
import edu.stanford.nlp.mt.decoder.recomb.*;
import edu.stanford.nlp.mt.decoder.util.*;
import edu.stanford.nlp.mt.Phrasal;

import edu.stanford.nlp.stats.ClassicCounter;

/**
 * Extension of MultiBeamDecoder that allows phrases with discontinuities in them (source and target).
 *
 * @author Michel Galley
 *
 * @param <TK>
 * @param <FV>
 */
public class DTUDecoder<TK, FV> extends AbstractBeamInferer<TK, FV> {
	// class level constants
	public static final String DEBUG_PROPERTY = "DTUDecoderDebug";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	private static final String OPTIONS_PROPERTY = "PrintTranslationOptions";
	public static final boolean OPTIONS_DUMP = Boolean.parseBoolean(System.getProperty(OPTIONS_PROPERTY, "false"));
  public static final String DETAILED_DEBUG_PROPERTY = "DTUDecoderDetailedDebug";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System.getProperty(DETAILED_DEBUG_PROPERTY, "false"));
  public static final String  ALIGNMENT_DUMP = System.getProperty("a");
	public static final int DEFAULT_BEAM_SIZE = 200;
	public static final HypothesisBeamFactory.BeamType DEFAULT_BEAM_TYPE = HypothesisBeamFactory.BeamType.treebeam; 
	public static final int DEFAULT_MAX_DISTORTION = -1;

  final int maxDistortion;
	final int numProcs;
  public static boolean gapsInFutureCost = true;

  static {
		if (ALIGNMENT_DUMP != null) {
      if ((new File(ALIGNMENT_DUMP)).delete()) {}
		}
	}
	
	
	ExecutorService threadPool;

	static public <TK,FV> DTUDecoderBuilder<TK,FV> builder() {
		return new DTUDecoderBuilder<TK,FV>();
	}
	
	protected DTUDecoder(DTUDecoderBuilder<TK, FV> builder) {
		super(builder);
		maxDistortion = builder.maxDistortion;

		if (builder.internalMultiThread) {
			numProcs = (System.getProperty("numProcs") != null ?
                  Integer.parseInt(System.getProperty("numProcs")) :
                  Runtime.getRuntime().availableProcessors());
		} else {
			numProcs = 1;
		}
 		threadPool = Executors.newFixedThreadPool(numProcs); 

		if (maxDistortion != -1) {
			System.err.printf("Discontinuous phrase-based decoder. Using distortion limit: %d\n", maxDistortion);
		} else {
			System.err.printf("Discontinuous phrase-based decoder. No hard distortion limit.\n");
		}
	}
	
	public static class DTUDecoderBuilder<TK,FV> extends AbstractBeamInfererBuilder<TK,FV> {
		int maxDistortion = DEFAULT_MAX_DISTORTION;
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
      assert(!useITGConstraints);
      return this;
		}
		
		public DTUDecoderBuilder() {
			super(DEFAULT_BEAM_SIZE, DEFAULT_BEAM_TYPE);
		}

		@Override
		public Inferer<TK, FV> build() {
			return new DTUDecoder<TK,FV>(this);
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

  private static boolean isContiguous(BitSet bitset) {
    int i = bitset.nextSetBit(0);
    int j = bitset.nextClearBit(i+1);
    return (bitset.nextSetBit(j+1) == -1);
  }

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
		Beam<Hypothesis<TK,FV>>[] beams = createBeamsForCoverageCounts(foreign.size()+2, beamCapacity, filter, recombinationHistory);
		
		// retrieve translation options
		if (DEBUG) System.err.println("Generating Translation Options");

    List<ConcreteTranslationOption<TK>> options =
					phraseGenerator.translationOptions(foreign, targets, translationId);

    // Remove all options with gaps in the source, since they cause problems with future cost estimation:
    List<ConcreteTranslationOption<TK>>
      optionsWithoutGaps = new ArrayList<ConcreteTranslationOption<TK>>(),
      optionsWithGaps = new ArrayList<ConcreteTranslationOption<TK>>();
    
    for(ConcreteTranslationOption<TK> opt : options) {
      if(isContiguous(opt.foreignCoverage))
         optionsWithoutGaps.add(opt);
      else if(gapsInFutureCost)
        optionsWithGaps.add(opt);
    }

    System.err.printf("Translation options: %d\n", options.size());
    System.err.printf("Translation options (no gaps): %d\n",  optionsWithoutGaps.size());
    System.err.printf("Translation options (with gaps): %d\n",  optionsWithGaps.size());

    List<List<ConcreteTranslationOption<TK>>> allOptions = new ArrayList<List<ConcreteTranslationOption<TK>>>();
    allOptions.add(optionsWithoutGaps);
    if(gapsInFutureCost)
      allOptions.add(optionsWithGaps);
    //System.err.printf("Translation options (use for future cost estimation): %d\n", allOptions.size());

    if (OPTIONS_DUMP || DETAILED_DEBUG) {
      int sentId = translationId + ((Phrasal.local_procs > 1) ? 2:0);
      synchronized (System.err) {
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
		
		DTUOptionGrid<TK> optionGrid = new DTUOptionGrid<TK>(options, foreign);
		
		// insert initial hypothesis
    Hypothesis<TK,FV> nullHyp = new Hypothesis<TK,FV>(translationId, foreign, heuristic, allOptions);
		beams[0].put(nullHyp);
		if (DEBUG) {
			System.err.printf("Estimated Future Cost: %e\n", nullHyp.h);
		}
		
		if (DEBUG) System.err.println("DTUDecorder translating loop");
		
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
      dump(bestHyp);
		}

		for (int i = beams.length -1; i >= 0; i--) {
			if (beams[i].size() != 0 && (constrainedOutputSpace == null || constrainedOutputSpace.allowableFinal(beams[i].iterator().next().featurizable))) {
					Hypothesis<TK, FV> bestHyp = beams[i].iterator().next();
					try { writeAlignments(alignmentDump, bestHyp); } catch (Exception e) { /* okay */ }
					try { alignmentDump.close(); } catch (Exception e) { /* okay */ }
          if(DEBUG) System.err.println("Returning beam of size: "+beams[i].size());
          return beams[i];
			}
		} 
		
		try{
      alignmentDump.append("<<< decoder failure >>>\n\n");
      alignmentDump.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
		
		return null;
	}

  public void dump(Hypothesis<TK, FV> bestHyp) {

    List<Hypothesis<TK,FV>> trace = new ArrayList<Hypothesis<TK,FV>>();
    for (Hypothesis<TK, FV> hyp = bestHyp; hyp != null; hyp = hyp.preceedingHyp) {
      trace.add(hyp);
    }
    Collections.reverse(trace);

    ClassicCounter<String> finalFeatureVector = new ClassicCounter<String>();
    if (bestHyp != null && bestHyp.featurizable != null) {
      System.err.printf("hyp: %s\n",  bestHyp.featurizable.partialTranslation);
      System.err.printf("score: %e\n", bestHyp.score());
      System.err.printf("Trace:\n");
      System.err.printf("--------------\n");
      List<FeatureValue<FV>> allfeatures = new ArrayList<FeatureValue<FV>>();
      for (Hypothesis<TK,FV> hyp : trace) {
        System.err.printf("%d:\n", hyp.id);
        TranslationOption abstractOption = (hyp.translationOpt != null) ? hyp.translationOpt.abstractOption : null;
        if (hyp.translationOpt != null) {
          //final String translation;
          boolean noSource = false;
          if (hyp instanceof DTUHypothesis) {
            abstractOption = ((DTUHypothesis)hyp).getAbstractOption();
            noSource = ((DTUHypothesis<TK,FV>)hyp).targetOnly();
          }
          if(noSource) {
            System.err.printf("\tPhrase: nil => %s(%d)",
                 hyp.featurizable.translatedPhrase,
                 hyp.featurizable.translationPosition);
          } else {
            System.err.printf("\tPhrase: %s(%d) => %s(%d)",
                hyp.translationOpt.abstractOption.foreign,
                hyp.featurizable.foreignPosition,
                hyp.featurizable.translatedPhrase,
                hyp.featurizable.translationPosition);
          }
        }
        System.err.printf("\tCoverage: %s\n", hyp.foreignCoverage);
        if (hyp.translationOpt != null) {
          System.err.printf("\tConcrete option:coverage: %s\n", hyp.translationOpt.foreignCoverage);
          System.err.printf("\tConcrete option:pos: %s\n", hyp.translationOpt.foreignPos);
        }
        if(abstractOption != null)
          System.err.printf("\tAbstract option: %s", abstractOption);
        System.err.printf("\tFeatures: %s\n", hyp.localFeatures);
        System.err.printf("\tHypothesis: %s\n", hyp);
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

      //if (featurizer instanceof RichIncrementalFeaturizer)
      //  ((RichIncrementalFeaturizer)featurizer).dump(bestHyp.featurizable);

      double score = scorer.getIncrementalScore(allfeatures);
      System.err.printf("Recalculated score: %.3f\n", score);
    } else {
      System.err.printf("Only null hypothesis was produced.\n");
    }
  }

  class BeamExpander implements Runnable {
  	Beam<Hypothesis<TK,FV>>[] beams; int beamId; int foreignSz; DTUOptionGrid<TK> optionGrid; ConstrainedOutputSpace<TK,FV> constrainedOutputSpace; int translationId; int threadId; int threadCount;
  	CountDownLatch cdl;
  	
  public  BeamExpander(Beam<Hypothesis<TK,FV>>[] beams, int beamId, int foreignSz, DTUOptionGrid<TK> optionGrid, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int translationId, int threadId, int threadCount, CountDownLatch cdl) {
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
	public int expandBeam(Beam<Hypothesis<TK,FV>>[] beams, int beamId, int foreignSz, DTUOptionGrid<TK> optionGrid, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int translationId, int threadId, int threadCount, CountDownLatch cdl) {
		int optionsApplied = 0;
    int hypPos = -1;
		int totalHypothesesGenerated = 0;
    //System.err.printf("\nBeam id: %d\n", beamId);

		List<Hypothesis<TK, FV>> hyps;
    //Hypothesis<TK, FV>[] hyps;
		synchronized (beams[beamId]) {
		  hyps = new LinkedList<Hypothesis<TK,FV>>();
      //hyps = new Hypothesis[beams[beamId].size()];
      //int i = -1;
		  for (Hypothesis<TK, FV> hyp : beams[beamId]) {
        hyps.add(hyp);
        //i++;
				//hyps[i] = hyp;
			}
		}

    List<Hypothesis<TK,FV>> newHyps = new LinkedList<Hypothesis<TK,FV>>();
    for (Hypothesis<TK, FV> hyp : hyps) {
      if(hyp instanceof DTUHypothesis) {
        DTUHypothesis<TK,FV> dtuHyp = (DTUHypothesis<TK,FV>) hyp;
        Deque<DTUHypothesis<TK,FV>> currentHyps = new LinkedList<DTUHypothesis<TK,FV>>();
        currentHyps.add(dtuHyp);
        while(!currentHyps.isEmpty()) {
          final DTUHypothesis<TK,FV> currentHyp = currentHyps.poll();
          assert (currentHyp != null);
          if (!currentHyp.hasExpired())
            newHyps.add(currentHyp);
          for(DTUHypothesis<TK,FV> nextHyp : currentHyp.collapseFloatingPhrases(translationId, featurizer, scorer, heuristic)) {
            if(!nextHyp.hasExpired())
              currentHyps.add(nextHyp);
          }
        }
      }
    }
    hyps.addAll(newHyps);

    for (Hypothesis<TK, FV> hyp : hyps) {
		  hypPos++;
			if (hypPos % threadCount != threadId) continue;
			if (hyp == null) continue;
			//System.err.printf("\nExpanding hyp: %s\n", hyp);
      //System.err.printf("\nCoverage: %s\n", hyp.foreignCoverage);
			int localOptionsApplied = 0;
		  //System.err.printf("Start position: %d\n", hyp.foreignCoverage.nextClearBit(0));
      //System.err.printf("foreignSz: %d\n", foreignSz);
			int firstCoverageGap = hyp.foreignCoverage.nextClearBit(0);

			for (int startPos = firstCoverageGap; startPos < foreignSz; startPos++) {
				int endPosMax = -1; //hyp.foreignCoverage.nextSetBit(startPos);

        //System.err.printf("  s=%s endPosMax = %d -> ", startPos, endPosMax);
        // check distortion limit
				if (endPosMax < 0) {
					if (maxDistortion >= 0 && startPos != firstCoverageGap) {
						endPosMax = Math.min(firstCoverageGap + maxDistortion+1, foreignSz);
					} else {
						endPosMax = foreignSz;
					}
				}
				for (int endPos = startPos; endPos < endPosMax; endPos++) {

          // Combine each hypothesis with each applicable option:
          List<ConcreteTranslationOption<TK>> applicableOptions = optionGrid.get(startPos, endPos);
          if (applicableOptions == null) continue;
          //System.err.printf("    startPos=%d endPos=%d options=%s\n", startPos, endPos, applicableOptions == null ? "0" : applicableOptions.size());
          //System.err.printf("options for (%d to %d): %d\n", startPos, endPos, applicableOptions.size());
          
          for (ConcreteTranslationOption<TK> option : applicableOptions) {
            //System.err.printf("option: %s\n", option.abstractOption.foreign);
            if(hyp.foreignCoverage.intersects(option.foreignCoverage)) {
              continue;
            }

            if (constrainedOutputSpace != null && !constrainedOutputSpace.allowableContinuation(hyp.featurizable, option)) {
              continue;
            }

            Hypothesis<TK,FV> newHyp = (option.abstractOption instanceof DTUOption || hyp instanceof DTUHypothesis) ?
              new DTUHypothesis<TK, FV>(translationId, option, hyp.length, hyp, featurizer, scorer, heuristic) :
              new Hypothesis<TK, FV>(translationId, option, hyp.length, hyp, featurizer, scorer, heuristic);
            {
              if(newHyp.hasExpired())
                continue;
              if(newHyp.isDone() != newHyp.featurizable.done) {
                System.err.println("ERROR in DTUDecoder with: "+newHyp);
                System.err.println("isDone(): "+newHyp.isDone());
                System.err.println("f.done: "+newHyp.featurizable.done);
                //throw new RuntimeException();
              }

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

              if(!hyp.hasExpired()) {
                int beamIdx = newHyp.foreignCoverage.cardinality();
                if(0 == newHyp.untranslatedTokens && newHyp.isDone()) {
                  ++beamIdx;
                }
                beams[beamIdx].put(newHyp);

                optionsApplied++;
                localOptionsApplied++;

              }
            }
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

  public class DTUOptionGrid<TK> {
    @SuppressWarnings("unchecked")
    private List[] grid;
    private int foreignSz;

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public DTUOptionGrid(List<ConcreteTranslationOption<TK>> options, Sequence<TK> foreign) {
      foreignSz = foreign.size();
      grid = new List[foreignSz*foreignSz];
      for (int startIdx = 0; startIdx < foreignSz; startIdx++) {
        for (int endIdx = startIdx; endIdx < foreignSz; endIdx++) {
          grid[getIndex(startIdx, endIdx)] = new LinkedList();
        }
      }
      for (ConcreteTranslationOption<TK> opt : options) {
        int startPos = opt.foreignPos;
        int endPos = opt.foreignCoverage.length() - 1;
        //int endPos = opt.foreignCoverage.nextClearBit(opt.foreignPos) - 1;
        grid[getIndex(startPos, endPos)].add(opt);
      }
    }

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public List<ConcreteTranslationOption<TK>> get(int startPos, int endPos) {
      return grid[getIndex(startPos, endPos)];
    }

    /**
     *
     */
    private int getIndex(int startPos, int endPos) {
      return startPos*foreignSz + endPos;
    }
  }
}
