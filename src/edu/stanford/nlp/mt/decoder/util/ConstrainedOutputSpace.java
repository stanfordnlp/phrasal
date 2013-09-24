package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Constrains the output (translation) space for conventional
 * force decoding to references.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public class ConstrainedOutputSpace<TK, FV> implements
    OutputSpace<TK, FV> {
  public static final String DEBUG_PROPERTY = "EnumeratedConstrainedOutputSpaceDebug";
  public static final int DEBUG = Integer.parseInt(System.getProperty(
      DEBUG_PROPERTY, "0"));
  public static final int DEBUG_LEVEL_RESULTS = 1;
  public static final int DEBUG_LEVEL_COMPUTATION = 2;
  
  private final int longestSourcePhrase;
  private final int longestTargetPhrase;
  private final List<Sequence<TK>> allowableSequences;
 
  /**
   * Constructor.
   * 
   * @param allowableSequences
   * @param longestSourcePhrase
   * @param longestTargetPhrase 
   */
  public ConstrainedOutputSpace(
      List<Sequence<TK>> allowableSequences, int longestSourcePhrase, int longestTargetPhrase) {
    this.allowableSequences = allowableSequences;
    this.longestSourcePhrase = longestSourcePhrase;
    this.longestTargetPhrase = longestTargetPhrase;
  }
  
  @SuppressWarnings("rawtypes")
  @Override 
  public boolean equals(Object o) {
	  if (this == o) {
	    return true;
	  } else if ( !(o instanceof ConstrainedOutputSpace)) {
	    return false;
	  } else {
		  ConstrainedOutputSpace ecos = (ConstrainedOutputSpace)o;
		  return ecos.allowableSequences.equals(allowableSequences);
	  }
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Allowable sequences:\n");
    for (Sequence<TK> seq : allowableSequences) {
      sb.append("\t").append(seq);
    }
    return sb.toString();
  }

  @Override
  public List<Sequence<TK>> getAllowableSequences() {
    return allowableSequences;
  }

  @Override
  public boolean allowableFinal(Featurizable<TK, FV> featurizable) {
    if (featurizable != null) {
      Sequence<TK> translation = featurizable.targetPrefix;
      for (Sequence<TK> allowableSequence : allowableSequences) {
        if (allowableSequence.equals(translation)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteRule<TK,FV> rule) {
    final Sequence<TK> nextPhrase = rule.abstractRule.target;
    
    // First rule in a derivation
    if (featurizable == null) {
      for (Sequence<TK> allowableSequence : allowableSequences) {
        if (allowableSequence.startsWith(nextPhrase)) {
          return true;
        }
      }
      return false;
    }

    // Next rule in a derivation
    final Sequence<TK> partialTranslation = featurizable.targetPrefix;

    asfor: for (Sequence<TK> allowableSequence : allowableSequences) {
      if (allowableSequence.startsWith(partialTranslation)) {
        int phraseSz = nextPhrase.size();
        int refPos = partialTranslation.size();
        if (refPos + phraseSz > allowableSequence.size()) {
          continue;
        }
        for (int phrasePos = 0; phrasePos < phraseSz; phrasePos++) {
          if (!allowableSequence.get(refPos + phrasePos).equals(
              nextPhrase.get(phrasePos))) {
            continue asfor;
          }
        }
        int tMissing = allowableSequence.size()
            - (partialTranslation.size() + nextPhrase.size());
        int fMissing = featurizable.numUntranslatedSourceTokens
            - rule.abstractRule.source.size();
        if ((fMissing == 0 && tMissing != 0)
            || (fMissing != 0 && tMissing == 0)) {
          continue;
        }
        /*
         * int priorTM = allowableSequence.size() - (partialTranslation.size());
         * int priorFM = featurizable.untranslatedTokens;
         * System.err.printf("Prior tMissing: %d Prior fMissing: %d\n", priorTM,
         * priorFM); System.err.printf("Prior ratio: t/f: %f f/t: %f\n",
         * (priorTM*1.0/priorFM), (priorFM*1.0/priorTM));
         * System.err.printf("Sizes - tp: %d fp: %d\n",
         * option.abstractOption.translation.size(),
         * option.abstractOption.foreign.size());
         * System.err.printf("tMissing: %d fMissing: %d\n", tMissing, fMissing);
         * 
         * System.out.printf("ratio: t/f: %f f/t: %f\n",
         * (tMissing*1.0/fMissing), (fMissing*1.0/tMissing));
         * System.out.printf("foreign size: %d\n",
         * featurizable.foreignSentence.size());
         */
        
        // TODO(spenceg): Usage of longestPhrase for source and target is
        // wrong. What is passed in from PhraseGenerator is the longest
        // source phrase. Need to access the longest target phrase.
        if (fMissing != 0 && tMissing / (double) fMissing > longestSourcePhrase) {
          continue;
        }
        if (tMissing != 0 && fMissing / (double) tMissing > longestTargetPhrase) {
          continue;
        }

        return true;
      }
    }

    return false;
  }

  @Override
  public List<ConcreteRule<TK,FV>> filter(List<ConcreteRule<TK,FV>> ruleList) {
    List<ConcreteRule<TK,FV>> filteredOptions = new ArrayList<ConcreteRule<TK,FV>>(
        ruleList.size());

    for (ConcreteRule<TK,FV> option : ruleList) {
      if (DEBUG >= DEBUG_LEVEL_COMPUTATION) {
        System.err.printf("Examining: %s %s\n",
            option.abstractRule.target, option.sourceCoverage);
      }
      for (Sequence<TK> allowableSequence : allowableSequences) {
        if (allowableSequence.contains(option.abstractRule.target)) {
          filteredOptions.add(option);
          if (DEBUG >= DEBUG_LEVEL_COMPUTATION) {
            System.err.printf("\tAccepted!\n");
          }
          break;
        }
      }
    }

    if (DEBUG >= DEBUG_LEVEL_RESULTS) {
      System.err.println("Reference Set");
      System.err.println("--------------");
      for (Sequence<TK> allowableSequence : allowableSequences) {
        System.err.println(allowableSequence);
      }
      System.err.println("Filtered options");
      System.err.println("----------------");
      for (ConcreteRule<TK,FV> option : filteredOptions) {
        System.err.printf("\t%s %s\n", option.abstractRule.target,
            option.sourceCoverage);
      }
      System.err.println("--\n");
    }

    return filteredOptions;
  }
}
