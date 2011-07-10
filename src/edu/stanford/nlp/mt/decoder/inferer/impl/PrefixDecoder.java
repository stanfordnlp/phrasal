package edu.stanford.nlp.mt.decoder.inferer.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.mt.decoder.inferer.AbstractInferer;
import edu.stanford.nlp.mt.decoder.util.ConstrainedOutputSpace;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.OptionGrid;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * Prefix decoder 
 * 
 * @author daniel
 *
 * @param <TK>
 * @param <FV>
 */
public class PrefixDecoder<TK, FV> extends AbstractInferer<TK, FV> {
  public static boolean DEBUG = true;
  
  int maxDistortion = -1;
  public PrefixDecoder(AbstractInferer<TK, FV> inferer) {
    super(inferer);
    if (inferer instanceof MultiBeamDecoder) {
      MultiBeamDecoder<TK, FV> mbd = (MultiBeamDecoder<TK, FV>)inferer;
      maxDistortion = mbd.maxDistortion;
    } else if (inferer instanceof DTUDecoder) {
      throw new UnsupportedOperationException();
    }
  }
  
  public PhraseGenerator<TK> getPhraseGenerator() {
    return phraseGenerator;
  }
  
  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> foreign,
      int translationId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets) {
    throw new UnsupportedOperationException();
  }


  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer,
      Sequence<TK> foreign, int translationId,
      ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets) {
    throw new UnsupportedOperationException();
  }

  
  @SuppressWarnings({ "rawtypes", "unchecked" })
@Override
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer,
      Sequence<TK> foreign, int translationId,
      ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int size) {
    
    PriorityQueue<Hypothesis<TK, FV>> agenda = new PriorityQueue<Hypothesis<TK,FV>>();
    List<ConcreteTranslationOption<TK>> options = phraseGenerator.translationOptions(foreign, targets, translationId);
    List<ConcreteTranslationOption<TK>> filteredOptions = constrainedOutputSpace.filterOptions(options);
    float[] autoInsertScores = new float[options.get(0).abstractOption.scores.length];
    String[] scoreNames = options.get(0).abstractOption.phraseScoreNames;
    
    Arrays.fill(autoInsertScores, -100);
    OptionGrid<TK> optionGrid = new OptionGrid<TK>(options, foreign);
    OptionGrid<TK> filteredOptionGrid = new OptionGrid<TK>(filteredOptions, foreign);
    
    @SuppressWarnings("unchecked")  
    
    // use *UNFILTERED* options for heuristic calculation
    Hypothesis<TK, FV> nullHyp = new Hypothesis<TK, FV>(translationId, foreign, heuristic, Arrays.asList(options));
    featurizer.initialize(options, foreign);
    if (DEBUG) {
    	System.err.printf("Adding initial hypothesis: %s\n", nullHyp);
    }
    agenda.add(nullHyp);
    List<Hypothesis<TK, FV>> completePrefixes = new ArrayList<Hypothesis<TK,FV>>();
    int foreignSz = foreign.size();
    do {
      Hypothesis<TK, FV> hyp = agenda.remove();
      if (DEBUG) {
    	  System.err.printf("Expanding hypothesis: %s\n", hyp);
      }
      int firstCoverageGap = hyp.foreignCoverage.nextClearBit(0);
      for (int startPos = firstCoverageGap; startPos < foreignSz; startPos++) {
        int endPosMax = hyp.foreignCoverage.nextSetBit(startPos);

        // check distortion limit
        if (endPosMax < 0) {
          if (maxDistortion >= 0 && startPos != firstCoverageGap) {
            endPosMax = Math.min(firstCoverageGap + maxDistortion + 1,
                foreignSz);
          } else {
            endPosMax = foreignSz;
          }
        }
        
        int expansions = 0;
        for (int endPos = startPos; endPos < endPosMax; endPos++) {
       // use *FILTERED* options for prefix hypothesis expansion
          List<ConcreteTranslationOption<TK>> applicableOptions = filteredOptionGrid
              .get(startPos, endPos);
          for (ConcreteTranslationOption<TK> option : applicableOptions) {
            if (constrainedOutputSpace != null
                && !constrainedOutputSpace.allowableContinuation(
                    hyp.featurizable, option)) {
              continue;
            }
            Hypothesis<TK, FV> newHyp = new Hypothesis<TK, FV>(translationId,
                option, hyp.length, hyp, featurizer, scorer, heuristic);
            if (DEBUG) {
            	System.out.printf("Contructed new hypothesis: %s\n", newHyp);
            }
            if (newHyp.featurizable.untranslatedTokens != 0) {
              if (constrainedOutputSpace != null
                  && !constrainedOutputSpace
                      .allowablePartial(newHyp.featurizable)) {
                 if (DEBUG) {
                    System.out.printf(" - hypothesis is not allowed by contrained output space\n");	
                 }
            	 continue;
              }
            }
            
            expansions++;    
            
            if (constrainedOutputSpace != null
                && constrainedOutputSpace
                    .allowableFinal(newHyp.featurizable)) {
              if (DEBUG) {
            	  System.out.printf(" - allowable fina;\n");
              }
              completePrefixes.add(newHyp);
              continue;
            }
            
            if (DEBUG) {
            	System.out.printf(" - adding hyp to agenda\n");
            }
            agenda.add(newHyp);
          }
        }
        if (expansions == 0) {
        	List<Sequence<TK>> allowableSequences = constrainedOutputSpace.getAllowableSequences();
        	for (Sequence<TK> allowableSequence : allowableSequences) {
        		if (hyp.featurizable != null &&
        		    !allowableSequence.startsWith(hyp.featurizable.partialTranslation)) {
        			continue;
        		}
        		int hypSz;
        		if (hyp.featurizable == null) {
        		  hypSz = 0;
        		} else {
        		  hypSz = hyp.featurizable.partialTranslation.size();	
        		}
        		if (hypSz >= allowableSequence.size()) {
        			continue;
        		}
        		if (DEBUG) {
        		   System.err.printf("uncompletable hypothesis of size: %d\n", hypSz);
        		   System.err.printf("attempting to complete with allowableSequnece %s\n", allowableSequence);
        		}
        		IString nextWord = (IString)allowableSequence.get(hypSz);
	        	TranslationOption<IString> abstractOption = new TranslationOption<IString>(autoInsertScores, scoreNames, 
	        	   new RawSequence<IString>(new IString[]{nextWord}), new RawSequence<IString>(new IString[0]), null);
	        	
	        	ConcreteTranslationOption option = new ConcreteTranslationOption(
	        			abstractOption, new CoverageSet(), 0, "autogenerated", -100);
	        	Hypothesis<TK, FV> newHyp = new Hypothesis<TK, FV>(translationId,
	                    option, hyp.length, hyp, featurizer, scorer, heuristic);
	        	if (DEBUG) {
	        		System.err.printf("Autogenerated hyp: %s\n", newHyp);
	        	}
	        	
	        	if (constrainedOutputSpace != null
	                    && constrainedOutputSpace
	                        .allowableFinal(newHyp.featurizable)) {
	                  if (DEBUG) {
	                	  System.out.printf(" - allowable fina;\n");
	                  }
	                  completePrefixes.add(newHyp);
	                  continue;
	            }
	        	
	        	agenda.add(newHyp);
        	}
        }
        
      }
    } while (completePrefixes.size() < PREFIX_ALIGNMENTS && agenda.size() > 0);
    agenda.clear();
    if (DEBUG) {
      System.err.printf("Doing prediction stage with prefix hypotheses: %s\n", completePrefixes.size());
    }
    agenda.addAll(completePrefixes);
    List<Hypothesis<TK, FV>> predictions = new ArrayList<Hypothesis<TK,FV>>(PREDICTIONS);
    do {
      Hypothesis<TK, FV> hyp = agenda.remove();
      if (DEBUG) {
        System.err.printf("Removing hyp from agenda: %s\n", hyp);
      }
      int firstCoverageGap = hyp.foreignCoverage.nextClearBit(0);     
      for (int startPos = firstCoverageGap; startPos < foreignSz; startPos++) {
        int endPosMax = hyp.foreignCoverage.nextSetBit(startPos);

        // check distortion limit
        if (endPosMax < 0) {
          if (maxDistortion >= 0 && startPos != firstCoverageGap) {
            endPosMax = Math.min(firstCoverageGap + maxDistortion + 1,
                foreignSz);
          } else {
            endPosMax = foreignSz;
          }
        }
        for (int endPos = startPos; endPos < endPosMax; endPos++) {
          // use *UNFILTERED* options for prefix hypothesis expansion predictions
          List<ConcreteTranslationOption<TK>> applicableOptions = optionGrid
              .get(startPos, endPos);
          for (ConcreteTranslationOption<TK> option : applicableOptions) {
            Hypothesis<TK, FV> newHyp = new Hypothesis<TK, FV>(translationId,
                option, hyp.length, hyp, featurizer, scorer, heuristic);
            if (DEBUG) {
            	System.out.printf("construction new hyp: \n", newHyp);
            }
            predictions.add(newHyp);                      
            agenda.add(newHyp);
          }
        }
      }      
    } while (predictions.size() < PREDICTIONS && agenda.size() > 0);
    
    List<RichTranslation<TK, FV>> nbest = new ArrayList<RichTranslation<TK,FV>>(predictions.size());
    for (Hypothesis<TK,FV> hyp : predictions) {
      nbest.add(new RichTranslation<TK, FV>(hyp.featurizable, hyp.finalScoreEstimate(), null));
    }
    return nbest;
  }

  static public final int PREFIX_ALIGNMENTS = 100;
  static public final int PREDICTIONS = 100;
  @Override
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> foreign,
      int translationId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int size) {
    return nbest(scorer, foreign, translationId, constrainedOutputSpace, targets, size);
  }
}
