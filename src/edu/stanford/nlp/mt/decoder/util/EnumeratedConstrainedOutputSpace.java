package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class EnumeratedConstrainedOutputSpace<TK, FV> implements
    ConstrainedOutputSpace<TK, FV> {
  public static final String DEBUG_PROPERTY = "EnumeratedConstrainedOutputSpaceDebug";
  public static final int DEBUG = Integer.parseInt(System.getProperty(
      DEBUG_PROPERTY, "0"));
  public static final int DEBUG_LEVEL_RESULTS = 1;
  public static final int DEBUG_LEVEL_COMPUTATION = 2;
  public final int longestPhrase;

  private final List<Sequence<TK>> allowableSequences;

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

  /**
	 * 
	 */
  public EnumeratedConstrainedOutputSpace(
      Collection<Sequence<TK>> allowableSequences, int longestPhrase) {
    this.allowableSequences = new ArrayList<Sequence<TK>>(allowableSequences);
    this.longestPhrase = longestPhrase;
  }

  @Override
  public boolean allowableFinal(Featurizable<TK, FV> featurizable) {
    if (featurizable == null)
      return false;

    Sequence<TK> translation = featurizable.partialTranslation;

    for (Sequence<TK> allowableSequence : allowableSequences) {
      if (allowableSequence.equals(translation)) {
        return true;
      }
      System.err.printf("%s\n%s\n", allowableSequence, translation);
      System.err.printf("left %d\n",
          allowableSequence.size() - translation.size());
    }

    return false;
  }

  @Override
  public boolean allowablePartial(Featurizable<TK, FV> featurizable) {
    return true;
  }

  @Override
  public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteTranslationOption<TK> option) {

    Sequence<TK> nextPhrase = option.abstractOption.translation;

    if (featurizable == null) {
      for (Sequence<TK> allowableSequence : allowableSequences) {
        if (allowableSequence.startsWith(nextPhrase)) {
          return true;
        }
      }
      return false;
    }

    Sequence<TK> partialTranslation = featurizable.partialTranslation;

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
        int fMissing = featurizable.untranslatedTokens
            - option.abstractOption.foreign.size();
        if ((fMissing == 0 && tMissing != 0)
            || (fMissing != 0 && tMissing == 0))
          continue;
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
        if (fMissing != 0 && tMissing / (double) fMissing > longestPhrase)
          continue;
        if (tMissing != 0 && fMissing / (double) tMissing > longestPhrase)
          continue;

        return true;
      }
    }

    return false;
  }

  @Override
  public List<ConcreteTranslationOption<TK>> filterOptions(
      List<ConcreteTranslationOption<TK>> optionList) {
    List<ConcreteTranslationOption<TK>> filteredOptions = new ArrayList<ConcreteTranslationOption<TK>>(
        optionList.size());

    for (ConcreteTranslationOption<TK> option : optionList) {
      if (DEBUG >= DEBUG_LEVEL_COMPUTATION) {
        System.err.printf("Examining: %s %s\n",
            option.abstractOption.translation, option.foreignCoverage);
      }
      for (Sequence<TK> allowableSequence : allowableSequences) {
        if (allowableSequence.contains(option.abstractOption.translation)) {
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
      for (ConcreteTranslationOption<TK> option : filteredOptions) {
        System.err.printf("\t%s %s\n", option.abstractOption.translation,
            option.foreignCoverage);
      }
      System.err.println("--\n");
    }

    return filteredOptions;
  }
}
