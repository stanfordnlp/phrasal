package edu.stanford.nlp.mt.decoder.h;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class IsolatedPhraseForeignCoverageHeuristic<TK, FV> implements
    SearchHeuristic<TK, FV> {

  public static final String DEBUG_PROPERTY = "ipfcHeuristicDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  final RuleFeaturizer<TK, FV> phraseFeaturizer;

  protected SpanScores hSpanScores;

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  public IsolatedPhraseForeignCoverageHeuristic(
      RuleFeaturizer<TK, FV> phraseFeaturizer) {
    this.phraseFeaturizer = phraseFeaturizer;
  }

  @Override
  public double getHeuristicDelta(Derivation<TK, FV> newHypothesis,
      CoverageSet newCoverage) {
    double oldH = newHypothesis.preceedingDerivation.h;
    double newH = 0;
    CoverageSet coverage = newHypothesis.sourceCoverage;
    int startEdge = coverage.nextClearBit(0);

    // System.out.printf("getHeuristicDelta:\n");
    // System.out.printf("coverage: %s", newHypothesis.foreignCoverage);

    int foreignSize = newHypothesis.sourceSequence.size();
    for (int endEdge; startEdge < foreignSize; startEdge = coverage
        .nextClearBit(endEdge)) {
      endEdge = coverage.nextSetBit(startEdge);

      if (endEdge == -1) {
        endEdge = newHypothesis.sourceSequence.size();
      }
      double localH = hSpanScores.getScore(startEdge, endEdge - 1);

      // System.out.printf("retreiving score for %d:%d ==> %f", startEdge,
      // endEdge-1, localH);

      newH += localH;
    }
    return newH - oldH;
  }

  @Override
  public double getInitialHeuristic(Sequence<TK> sourceSequence, InputProperties sourceInputProperties,
      List<List<ConcreteRule<TK,FV>>> ruleList, Scorer<FV> scorer, int sourceInputId) {

    int foreignSequenceSize = sourceSequence.size();

    SpanScores viterbiSpanScores = new SpanScores(foreignSequenceSize);

    if (DEBUG) {
      System.err.println("IsolatedPhraseForeignCoverageHeuristic");
      System.err.printf("Foreign Sentence: %s\n", sourceSequence);

      System.err.println("Initial Spans from PhraseTable");
      System.err.println("------------------------------");
    }

    // initialize viterbiSpanScores
    assert (ruleList.size() == 1);
    for (ConcreteRule<TK,FV> rule : ruleList.get(0)) {
      // Don't run the featurizer again
//      Featurizable<TK, FV> f = new Featurizable<TK, FV>(sourceSequence, sourceInputProperties, 
//          option, sourceInputId);
//      List<FeatureValue<FV>> phraseFeatures = phraseFeaturizer
//          .ruleFeaturize(f);
//      double score = scorer.getIncrementalScore(phraseFeatures);
      double score = rule.isolationScore;
      int terminalPos = rule.sourcePosition
          + rule.abstractRule.source.size() - 1;
      if (score > viterbiSpanScores.getScore(rule.sourcePosition, terminalPos)) {
        viterbiSpanScores.setScore(rule.sourcePosition, terminalPos, score);
      }
    }

    if (DEBUG) {
      System.err.println("Initial Minimums");
      System.err.println("------------------------------");

      for (int startPos = 0; startPos < foreignSequenceSize; startPos++) {
        for (int endPos = 0; endPos < foreignSequenceSize; endPos++) {
          System.err.printf("\t%d:%d score: %f\n", startPos, endPos,
              viterbiSpanScores.getScore(startPos, endPos));
        }
      }
    }

    if (DEBUG) {
      System.err.println();
      System.err.println("Merging span scores");
      System.err.println("-------------------");
    }

    // Viterbi combination of spans
    for (int spanSize = 2; spanSize <= foreignSequenceSize; spanSize++) {
      if (DEBUG) {
        System.err.printf("\n* Merging span size: %d\n", spanSize);
      }
      for (int startPos = 0; startPos <= foreignSequenceSize - spanSize; startPos++) {
        int terminalPos = startPos + spanSize - 1;
        double bestScore = viterbiSpanScores.getScore(startPos, terminalPos);
        for (int centerEdge = startPos + 1; centerEdge <= terminalPos; centerEdge++) {
          double combinedScore = viterbiSpanScores.getScore(startPos,
              centerEdge - 1)
              + viterbiSpanScores.getScore(centerEdge, terminalPos);
          if (combinedScore > bestScore) {
            if (DEBUG) {
              System.err.printf("\t%d:%d updating to %.3f from %.3f\n",
                  startPos, terminalPos, combinedScore, bestScore);
            }
            bestScore = combinedScore;
          }
        }
        viterbiSpanScores.setScore(startPos, terminalPos, bestScore);
      }
    }

    if (DEBUG) {
      System.err.println();
      System.err.println("Final Scores");
      System.err.println("------------");
      for (int startEdge = 0; startEdge < foreignSequenceSize; startEdge++) {
        for (int terminalEdge = startEdge; terminalEdge < foreignSequenceSize; terminalEdge++) {
          System.err.printf("\t%d:%d score: %.3f\n", startEdge, terminalEdge,
              viterbiSpanScores.getScore(startEdge, terminalEdge));
        }
      }
    }

    hSpanScores = viterbiSpanScores;

    double hCompleteSequence = hSpanScores.getScore(0, foreignSequenceSize - 1);
    if (DEBUG) {
      System.err.println("Done IsolatedForeignCoverageHeuristic");
    }
    return hCompleteSequence;
  }

  private static class SpanScores {
    final double[] spanValues;
    final int terminalPositions;

    public SpanScores(int length) {
      terminalPositions = length + 1;
      spanValues = new double[terminalPositions * terminalPositions];
      Arrays.fill(spanValues, Double.NEGATIVE_INFINITY);
    }

    public double getScore(int startPosition, int endPosition) {
      return spanValues[startPosition * terminalPositions + endPosition];
    }

    public void setScore(int startPosition, int endPosition, double score) {
      spanValues[startPosition * terminalPositions + endPosition] = score;
    }
  }
}
