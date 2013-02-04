package edu.stanford.nlp.mt.base;

import java.util.*;

public class ConcreteTranslationOptions {

  private ConcreteTranslationOptions() {
  }

  /**
   * 
   * @param <TK>
   * @param <FV>
   */
  static public <TK,FV> List<ConcreteTranslationOption<TK,FV>> filterOptions(
      CoverageSet coverage, int foreignLength,
      List<ConcreteTranslationOption<TK,FV>> options) {
    List<ConcreteTranslationOption<TK,FV>> applicableOptions = new ArrayList<ConcreteTranslationOption<TK,FV>>(
        options.size());
    CoverageSet flippedCoverage = new CoverageSet(foreignLength);
    flippedCoverage.or(coverage);
    flippedCoverage.flip(0, foreignLength);
    for (ConcreteTranslationOption<TK,FV> option : options) {
      if (flippedCoverage.intersects(option.foreignCoverage)) {
        applicableOptions.add(option);
      }
    }
    return applicableOptions;
  }
}
