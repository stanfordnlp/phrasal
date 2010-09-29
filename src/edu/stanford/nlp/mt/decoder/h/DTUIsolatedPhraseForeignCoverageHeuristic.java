package edu.stanford.nlp.mt.decoder.h;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.DTUOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Pair;

/**
 * @author danielcer
 * @author Michel Galley
 */
public class DTUIsolatedPhraseForeignCoverageHeuristic<TK, FV> implements
    SearchHeuristic<TK, FV> {

  protected static final double MINUS_INF = -10000.0;

  protected static final String DEBUG_PROPERTY = "ipfcHeuristicDebug";
  protected static final boolean DEBUG = Boolean.parseBoolean(System
      .getProperty(DEBUG_PROPERTY, "false"));

  protected static final String IGNORE_TGT_PROPERTY = "fcIgnoreTargetGaps";
  protected static final boolean IGNORE_TGT = Boolean.parseBoolean(System
      .getProperty(IGNORE_TGT_PROPERTY, "true"));

  protected final IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer;
  protected final Scorer<FV> scorer;
  protected SpanScores hSpanScores;

  static {
    System.err.println("Ignoring target gaps in future cost computation: "
        + IGNORE_TGT);
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  /**
	 * 
	 */
  public DTUIsolatedPhraseForeignCoverageHeuristic(
      IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer) {
    this.phraseFeaturizer = phraseFeaturizer;
    this.scorer = scorer;
    System.err.println("Heuristic: " + getClass());
  }

  /**
   * For a given coverage set C of a given hypothesis, this finds all the gaps
   * in the coverage set (e.g., if C = {1,5,10}, then gaps are 2-4 and 6-9), and
   * sums the future costs associated with all gaps (e.g., future-cost(2-4) +
   * future-cost(6-9)).
   * 
   * This is the same as in Moses, though this may sometimes over estimate
   * future cost with discontinuous phrases.
   */
  @Override
  public double getHeuristicDelta(Hypothesis<TK, FV> hyp,
      CoverageSet newCoverage) {

    double oldH = hyp.preceedingHyp.h;
    double newH = 0.0;

    CoverageSet coverage = hyp.foreignCoverage;
    int startEdge = coverage.nextClearBit(0);

    if (Double.isNaN(oldH)) {
      System.err.printf("getHeuristicDelta:\n");
      System.err.printf("coverage: %s\n", hyp.foreignCoverage);
      System.err.println("old H: " + oldH);
      throw new RuntimeException();
    }

    int foreignSize = hyp.foreignSequence.size();
    for (int endEdge; startEdge < foreignSize; startEdge = coverage
        .nextClearBit(endEdge)) {
      endEdge = coverage.nextSetBit(startEdge);

      if (endEdge == -1) {
        endEdge = hyp.foreignSequence.size();
      }

      double localH = hSpanScores.getScore(startEdge, endEdge - 1);

      if (Double.isNaN(localH)) {
        System.err.printf("Bad retrieved score for %d:%d ==> %f\n", startEdge,
            endEdge - 1, localH);
        throw new RuntimeException();
      }

      newH += localH;
      if (Double.isNaN(newH)) {
        System.err.printf(
            "Bad total retrieved score for %d:%d ==> %f (localH=%f)\n",
            startEdge, endEdge - 1, newH, localH);
        throw new RuntimeException();
      }
    }
    if ((Double.isInfinite(newH) || newH == MINUS_INF)
        && (Double.isInfinite(oldH) || oldH == MINUS_INF))
      return 0.0;
    double delta = newH - oldH;
    ErasureUtils.noop(delta);
    return delta;
  }

  @Override
  public double getInitialHeuristic(Sequence<TK> foreignSequence,
      List<List<ConcreteTranslationOption<TK>>> options, int translationId) {
    return getInitialHeuristic(foreignSequence, options, translationId, DEBUG);
  }

  @SuppressWarnings("unchecked")
  public double getInitialHeuristic(Sequence<TK> foreignSequence,
      List<List<ConcreteTranslationOption<TK>>> options, int translationId, boolean debug) {

    int foreignSequenceSize = foreignSequence.size();

    SpanScores viterbiSpanScores = new SpanScores(foreignSequenceSize);

    if (debug) {
      System.err.println("IsolatedPhraseForeignCoverageHeuristic");
      System.err.printf("Foreign Sentence: %s\n", foreignSequence);

      System.err.println("Initial Spans from PhraseTable");
      System.err.println("------------------------------");
    }

    // initialize viterbiSpanScores
    System.err.println("Lists of options: " + options.size());
    assert (options.size() == 1 || options.size() == 2); // options[0]: phrases
                                                         // without gaps;
                                                         // options[1]: phrases
                                                         // with gaps
    System.err.println("size: " + options.size());

    List<Pair<ConcreteTranslationOption<TK>, Double>>[][] dtuLists = new LinkedList[foreignSequenceSize][foreignSequenceSize];

    for (int i = 0; i < options.size(); ++i) {
      for (ConcreteTranslationOption<TK> option : options.get(i)) {
        if (IGNORE_TGT && option.abstractOption instanceof DTUOption)
          continue;
        Featurizable<TK, FV> f = new Featurizable<TK, FV>(foreignSequence,
            option, translationId);
        List<FeatureValue<FV>> phraseFeatures = phraseFeaturizer
            .phraseListFeaturize(f);
        double score = scorer.getIncrementalScore(phraseFeatures), childScore = 0.0;
        final int terminalPos;
        if (i == 0) {
          terminalPos = option.foreignPos
              + option.abstractOption.foreign.size() - 1;
          if (score > viterbiSpanScores
              .getScore(option.foreignPos, terminalPos)) {
            viterbiSpanScores.setScore(option.foreignPos, terminalPos, score);
            if (Double.isNaN(score)) {
              System.err.printf("Bad Viterbi score: score[%d,%d]=%.3f\n",
                  option.foreignPos, terminalPos, score);
              throw new RuntimeException();
            }
          }
          if (debug) {
            System.err.printf("\t%d:%d:%d %s->%s score: %.3f %.3f\n",
                option.foreignPos, terminalPos, i,
                option.abstractOption.foreign,
                option.abstractOption.translation, score, childScore);
            System.err.printf("\t\tFeatures: %s\n", phraseFeatures);
          }
        } else {
          // Discontinuous phrase: save it for later:
          int startPos = option.foreignCoverage.nextSetBit(0);
          terminalPos = option.foreignCoverage.length() - 1;
          if (dtuLists[startPos][terminalPos] == null)
            dtuLists[startPos][terminalPos] = new LinkedList<Pair<ConcreteTranslationOption<TK>, Double>>();
          dtuLists[startPos][terminalPos]
              .add(new Pair<ConcreteTranslationOption<TK>, Double>(option,
                  score));
        }
      }
    }
    dumpScores(viterbiSpanScores, foreignSequence, "InitialMinimums", debug);

    if (debug) {
      System.err.println();
      System.err.println("Merging span scores");
      System.err.println("-------------------");
    }

    // Viterbi combination of spans
    for (int spanSize = 2; spanSize <= foreignSequenceSize; spanSize++) {
      if (debug) {
        System.err.printf("\n* Merging span size: %d\n", spanSize);
      }
      for (int startPos = 0; startPos <= foreignSequenceSize - spanSize; startPos++) {
        int terminalPos = startPos + spanSize - 1;
        {
          // Merge two continuous phrases:
          double bestScore = viterbiSpanScores.getScore(startPos, terminalPos);
          for (int centerEdge = startPos + 1; centerEdge <= terminalPos; centerEdge++) {
            double combinedScore = viterbiSpanScores.getScore(startPos,
                centerEdge - 1)
                + viterbiSpanScores.getScore(centerEdge, terminalPos);
            if (combinedScore > bestScore) {
              if (debug) {
                System.err.printf("\t%d:%d updating to %.3f from %.3f\n",
                    startPos, terminalPos, combinedScore, bestScore);
              }
              bestScore = combinedScore;
            }
          }
          viterbiSpanScores.setScore(startPos, terminalPos, bestScore);
        }
        // Merge discontinuous phrase with other phrases:
        if (dtuLists[startPos][terminalPos] != null) {
          for (Pair<ConcreteTranslationOption<TK>, Double> dtu : dtuLists[startPos][terminalPos]) {
            ConcreteTranslationOption<TK> option = dtu.first;
            assert (option.foreignPos == startPos);
            double dtuScore = dtu.second;
            CoverageSet cs = option.foreignCoverage;
            int startIdx, endIdx = 0;
            double childScore = 0.0;
            while (true) {
              startIdx = cs.nextClearBit(cs.nextSetBit(endIdx));
              endIdx = cs.nextSetBit(startIdx) - 1;
              if (endIdx < 0)
                break;
              childScore += viterbiSpanScores.getScore(startIdx, endIdx);
            }
            double totalScore = dtuScore + childScore;
            double oldScore = viterbiSpanScores.getScore(option.foreignPos,
                terminalPos);
            if (totalScore > oldScore) {
              if (Double.isNaN(totalScore)) {
                System.err.printf(
                    "Bad Viterbi score[%d,%d]: score=%.3f childScore=%.3f\n",
                    option.foreignPos, terminalPos, dtuScore, childScore);
                throw new RuntimeException();
              }
              viterbiSpanScores.setScore(option.foreignPos, terminalPos,
                  totalScore);
              if (debug)
                System.err
                    .printf(
                        "Improved score with a DTU %s at [%d,%d]: %.3f -> %.3f (childScore=%.3f)\n",
                        option.foreignCoverage, startPos, terminalPos,
                        oldScore, totalScore, childScore);
            }
          }
        }
      }
    }
    dumpScores(viterbiSpanScores, foreignSequence, "Final Scores", debug);

    hSpanScores = viterbiSpanScores;

    double hCompleteSequence = hSpanScores.getScore(0, foreignSequenceSize - 1);

    if (Double.isInfinite(hCompleteSequence) || Double.isNaN(hCompleteSequence)) {
      //hCompleteSequence = MINUS_INF;
      getInitialHeuristic(foreignSequence, options, translationId, true);
      throw new RuntimeException("Error: h is either NaN or infinite: " + hCompleteSequence);
    }

    if (debug) System.err.println("Done IsolatedForeignCoverageHeuristic");
    return hCompleteSequence;
  }

  void dumpScores(SpanScores viterbiSpanScores, Sequence<TK> foreignSequence,
      String infoString, boolean debug) {
    if (debug) {
      int foreignSequenceSize = foreignSequence.size();
      System.err.println();
      System.err.println(infoString);
      System.err.println("------------");
      System.err.print("          last = ");
      for (int endPos = 0; endPos < foreignSequenceSize; endPos++)
        System.err.printf("%9d ", endPos);
      System.err.println();
      for (int startPos = 0; startPos < foreignSequenceSize; startPos++) {
        System.err.printf("\t%d-last scores: ", startPos);
        for (int endPos = 0; endPos < foreignSequenceSize; endPos++) {
          if (startPos > endPos) {
            System.err.printf("          ");
          } else {
            System.err.printf("%9.3f ",
                viterbiSpanScores.getScore(startPos, endPos));
          }
        }
        System.err.printf("\n");
      }
    }
  }

  protected static class SpanScores {
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
