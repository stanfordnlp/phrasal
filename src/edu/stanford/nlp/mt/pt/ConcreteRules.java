package edu.stanford.nlp.mt.pt;

import java.util.List;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.util.Generics;


/**
 * Static methods for ConcreteRule objects.
 * 
 * @author Spence Green
 *
 */
public class ConcreteRules {

  private ConcreteRules() {
  }

  /**
   * 
   * @param <TK>
   * @param <FV>
   */
  static public <TK,FV> List<ConcreteRule<TK,FV>> filterOptions(
      CoverageSet coverage, int foreignLength,
      List<ConcreteRule<TK,FV>> options) {
    List<ConcreteRule<TK,FV>> applicableOptions = Generics.newArrayList(
        options.size());
    CoverageSet flippedCoverage = new CoverageSet(foreignLength);
    flippedCoverage.or(coverage);
    flippedCoverage.flip(0, foreignLength);
    for (ConcreteRule<TK,FV> option : options) {
      if (flippedCoverage.intersects(option.sourceCoverage)) {
        applicableOptions.add(option);
      }
    }
    return applicableOptions;
  }
}
