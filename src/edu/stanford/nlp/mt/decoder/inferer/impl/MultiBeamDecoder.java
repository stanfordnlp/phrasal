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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.inferer.AbstractBeamInferer;
import edu.stanford.nlp.mt.decoder.inferer.AbstractBeamInfererBuilder;
import edu.stanford.nlp.mt.decoder.inferer.Inferer;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.ConstrainedOutputSpace;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.stats.ClassicCounter;

/**
 * Stack-based, left-to-right phrase-based inference implemented as a beam search.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class MultiBeamDecoder<TK, FV> extends AbstractBeamInferer<TK, FV> {
  // class level constants
  public static final String DEBUG_PROPERTY = "MultiBeamDecoderDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  private static final String OPTIONS_PROPERTY = "PrintTranslationOptions";
  public static final boolean OPTIONS_DUMP = Boolean.parseBoolean(System
      .getProperty(OPTIONS_PROPERTY, "false"));
  public static final String DETAILED_DEBUG_PROPERTY = "MultiBeamDecoderDetailedDebug";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System
      .getProperty(DETAILED_DEBUG_PROPERTY, "false"));
  public static final int DEFAULT_BEAM_SIZE = 200;
  public static final BeamFactory.BeamType DEFAULT_BEAM_TYPE = BeamFactory.BeamType.treebeam;
  public static final int DEFAULT_MAX_DISTORTION = -1;
  public static final boolean DEFAULT_USE_ITG_CONSTRAINTS = false;

  public final boolean useITGConstraints;
  final int maxDistortion;

  public static final boolean DO_PARSE = true;

  static public <TK, FV> MultiBeamDecoderBuilder<TK, FV> builder() {
    return new MultiBeamDecoderBuilder<TK, FV>();
  }

  protected MultiBeamDecoder(MultiBeamDecoderBuilder<TK, FV> builder) {
    super(builder);
    maxDistortion = builder.maxDistortion;
    useITGConstraints = builder.useITGConstraints;
    
    if (DEBUG) {
      if (useITGConstraints) { System.err.printf("Using ITG Constraints\n"); }
      else { System.err.printf("Not using ITG Constraints\n"); }
    }
    
    if (maxDistortion != -1) {
      System.err.printf("Multi-beam decoder. Distortion limit: %d\n",
          maxDistortion);
    } else {
      System.err.printf("Multi-beam decoder. No hard distortion limit.\n");
    }    
  }

  public static class MultiBeamDecoderBuilder<TK, FV> extends
      AbstractBeamInfererBuilder<TK, FV> {
    int maxDistortion = DEFAULT_MAX_DISTORTION;
    boolean useITGConstraints = DEFAULT_USE_ITG_CONSTRAINTS;

    @Override
    public AbstractBeamInfererBuilder<TK, FV> setMaxDistortion(int maxDistortion) {
      this.maxDistortion = maxDistortion;
      return this;
    }

    @Override
    public AbstractBeamInfererBuilder<TK, FV> useITGConstraints(
        boolean useITGConstraints) {
      this.useITGConstraints = useITGConstraints;
      return this;
    }

    public MultiBeamDecoderBuilder() {
      super(DEFAULT_BEAM_SIZE, DEFAULT_BEAM_TYPE);
    }

    @Override
    public Inferer<TK, FV> build() {
      return new MultiBeamDecoder<TK, FV>(this);
    }
  }
  
  private void displayBeams(Beam<Derivation<TK, FV>>[] beams) {
    System.err.print("Stack sizes: ");
    for (int si = 0; si < beams.length; si++) {
      if (si != 0)
        System.err.print(",");
      System.err.printf(" %d", beams[si].size());
    }
    System.err.println();
  }

  @Override
  protected Beam<Derivation<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory,
      ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int nbest) {
    final int sourceSz = source.size();

    // create beams, where there is a bijection between the beam and the cardinality of
    // the coverage set
    if (DEBUG) System.err.println("Creating beams");
    Beam<Derivation<TK, FV>>[] beams = createBeamsForCoverageCounts(source
        .size() + 1, beamCapacity, filter, recombinationHistory);

    // TM (phrase table) query for applicable rules
    if (DEBUG) System.err.println("Generating Translation Options");
    List<ConcreteRule<TK,FV>> ruleList = phraseGenerator
        .getRules(source, targets, sourceInputId, scorer);

    if (OPTIONS_DUMP && DETAILED_DEBUG) {
      int sentId = sourceInputId;
      synchronized (System.err) {
        System.err.print(">> Translation Options <<\n");
        for (ConcreteRule<TK,FV> option : ruleList)
          System.err.printf("%s ||| %s ||| %s ||| %s ||| %s\n", sentId,
              option.abstractRule.source, option.abstractRule.target,
              option.isolationScore, option.sourceCoverage);
        System.err.println(">> End translation options <<");
      }
    } else {
      System.err.printf("Translation options: %d\n", ruleList.size());
    }

    // Force decoding---if it is enabled, then filter the rule set according
    // to the references
    if (constrainedOutputSpace != null) {
      ruleList = constrainedOutputSpace.filter(ruleList);
      System.err
          .printf(
              "Translation options after reduction by output space constraint: %d\n",
              ruleList.size());
    }

    // Create rule lookup chart. Rules can be fetched by span.
    RuleGrid<TK,FV> ruleGrid = new RuleGrid<TK,FV>(ruleList, source);

    // Generate null/start hypothesis
    List<List<ConcreteRule<TK,FV>>> allOptions = new ArrayList<List<ConcreteRule<TK,FV>>>();
    allOptions.add(ruleList);
    Derivation<TK, FV> nullHyp = new Derivation<TK, FV>(sourceInputId, source,
        heuristic, scorer, annotators, allOptions);
    beams[0].put(nullHyp);
    int totalHypothesesGenerated = 1;
    if (DEBUG) {
      System.err.printf("Estimated Future Cost: %e\n", nullHyp.h);
      System.err.println("MultiBeamDecorder translating loop");
    }

    // Initialize feature extractors
    featurizer.initialize(sourceInputId, ruleList, source);

    // main translation loop---beam expansion
    long startTime = System.nanoTime();
    for (int i = 0; i < beams.length; i++) {
      expandBeam(beams, i, sourceSz, ruleGrid, 
          constrainedOutputSpace, sourceInputId);
      
      if (DEBUG) {
        displayBeams(beams);
        System.err.println("--------------------------------");
      }
    }
    System.err.printf("Decoding loop time: %.3f s%n", (System.nanoTime() - startTime) / 1e9);

    if (DEBUG) {
      int recombined = 0;
      int preinsertionDiscarded = 0;
      int pruned = 0;
      for (Beam<Derivation<TK, FV>> beam : beams) {
        recombined += beam.recombined();
        preinsertionDiscarded += beam.preinsertionDiscarded();
        pruned += beam.pruned();
      }
      System.err.printf("Stats:\n");
      System.err.printf("\ttotal hypotheses generated: %d\n",
          totalHypothesesGenerated);
      System.err.printf("\tcount recombined : %d\n", recombined);
      System.err.printf("\tnumber pruned: %d\n", pruned);
      System.err.printf("\tpre-insertion discarded: %d\n",
          preinsertionDiscarded);

      int beamIdx = beams.length - 1;
      for (; beamIdx >= 0; beamIdx--) {
        if (beams[beamIdx].size() != 0)
          break;
      }
      Derivation<TK, FV> bestHyp = beams[beamIdx].iterator().next();
      dump(bestHyp);
    }

    // Select the beam to return. beams[beams.length-1] should have
    // the goal hypotheses, but if it is empty, then backoff to the first previous
    // beam that has a (partial) hypothesis.
    // TODO(spenceg) Should raise a big warning message here if the parse does
    // actually fail.
    for (int i = beams.length - 1; i >= 0; i--) {
      if (beams[i].size() != 0
          && (constrainedOutputSpace == null || constrainedOutputSpace
              .allowableFinal(beams[i].iterator().next().featurizable))) {
        Derivation<TK, FV> bestHyp = beams[i].iterator().next();
        System.err.printf("Annotator output for best hypothesis (%d vs %d)\n", bestHyp.annotators.size(), annotators.size());
        System.err.println("===========================================");
        for (Annotator<TK,FV> annotator: bestHyp.annotators) {
        	System.err.println(annotator);
        }
        if (DEBUG)
          System.err.println("Returning beam of size: " + beams[i].size());
        return beams[i];
      }
    }

    // Decoder failure
    return null;
  }

  @Override
  public void dump(Derivation<TK, FV> bestHyp) {

    List<Derivation<TK, FV>> trace = new ArrayList<Derivation<TK, FV>>();
    for (Derivation<TK, FV> hyp = bestHyp; hyp != null; hyp = hyp.preceedingDerivation) {
      trace.add(hyp);
    }
    Collections.reverse(trace);

    ClassicCounter<String> finalFeatureVector = new ClassicCounter<String>();
    if (bestHyp != null && bestHyp.featurizable != null) {
      System.err.printf("hyp: %s\n", bestHyp.featurizable.targetPrefix);
      System.err.printf("score: %e\n", bestHyp.score());
      System.err.printf("Trace:\n");
      System.err.printf("--------------\n");
      List<FeatureValue<FV>> allfeatures = new ArrayList<FeatureValue<FV>>();
      for (Derivation<TK, FV> hyp : trace) {
        System.err.printf("%d:\n", hyp.id);
        if (hyp.rule != null) {
          System.err.printf("\tPhrase: %s(%d) => %s(%d)",
              hyp.rule.abstractRule.source,
              hyp.featurizable.sourcePosition,
              hyp.rule.abstractRule.target,
              hyp.featurizable.targetPosition);
        }
        System.err.printf("\tCoverage: %s\n", hyp.sourceCoverage);
        System.err.printf("\tFeatures: %s\n", hyp.localFeatures);
        if (hyp.localFeatures != null) {
          for (FeatureValue<FV> featureValue : hyp.localFeatures) {
            finalFeatureVector.incrementCount(featureValue.name.toString(),
                featureValue.value);
            allfeatures.add(featureValue);
          }
        }
      }

      System.err.printf("\n\nFeatures: %s\n", finalFeatureVector);
      System.err.println();
      System.err.printf("Best hyp score: %.4f\n", bestHyp.finalScoreEstimate());
      System.err.printf("true score: %.3f h: %.3f\n", bestHyp.score, bestHyp.h);
      System.err.println();

      if (scorer != null) {
        double score = scorer.getIncrementalScore(allfeatures);
        System.err.printf("Recalculated score: %.3f\n", score);
      }
    } else {
      System.err.printf("Only null hypothesis was produced.\n");
    }
  }

  /**
   * Sloppy beam search from Pharoah / early version of Moses. This algorithm
   * creates many hypotheses that will eventually be discarded.
   * 
   * @param beams
   * @param beamId
   * @param sourceSz
   * @param optionGrid
   * @param constrainedOutputSpace
   * @param sourceInputId
   * @return number of generated hypotheses
   */
  private int expandBeam(Beam<Derivation<TK, FV>>[] beams, int beamId,
      int sourceSz, RuleGrid<TK,FV> optionGrid,
      ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      int sourceInputId) {
    int optionsApplied = 0;
    int hypPos = -1;
    int totalHypothesesGenerated = 0;

    for (Derivation<TK, FV> hyp : beams[beamId]) {
      hypPos++;
      if (hyp == null)
        continue;
      int localOptionsApplied = 0;
      int firstCoverageGap = hyp.sourceCoverage.nextClearBit(0);
      int priorStartPos = (hyp.featurizable == null ? 0
          : hyp.featurizable.sourcePosition);
      int priorEndPos = (hyp.featurizable == null ? 0
          : hyp.featurizable.sourcePosition
          + hyp.featurizable.sourcePhrase.size());

      // Loop over coverage gaps
      // Left edge
      for (int startPos = firstCoverageGap; startPos < sourceSz; startPos++) {
        int endPosMax = hyp.sourceCoverage.nextSetBit(startPos);
        if (DETAILED_DEBUG)
          System.err.printf("Current startPos: %d, endPosMax: %d\n", startPos, endPosMax);

        // Re-ordering constraint checks
        // Moses-style hard distortion limit
        if (endPosMax < 0) {
          if (maxDistortion >= 0 && startPos != firstCoverageGap) {
            endPosMax = Math.min(firstCoverageGap + maxDistortion + 1,
                sourceSz);
          } else {
            endPosMax = sourceSz;
          }
          if (DETAILED_DEBUG)
            System.err.printf("after checking distortion limit, endPosMax: %d\n", endPosMax);
        }
        // ITG constraints
        if (useITGConstraints) {
          boolean ITGOK = true;
          if (startPos > priorStartPos) {
            for (int pos = priorEndPos + 1; pos < startPos; pos++) {
              if (hyp.sourceCoverage.get(pos)
                  && !hyp.sourceCoverage.get(pos - 1)) {
                ITGOK = false;
                break;
              }
            }
          } else {
            for (int pos = startPos; pos < priorStartPos; pos++) {
              if (hyp.sourceCoverage.get(pos)
                  && !hyp.sourceCoverage.get(pos + 1)) {
                ITGOK = false;
                break;
              }
            }
          }
          if (DETAILED_DEBUG)
            System.err.printf("after ITG constraints check, ITGOK=%b\n", ITGOK);
          // Constraint-check failed...don't expand this hypothesis
          if (!ITGOK)
            continue;
        }
        
        // Right edge
        for (int endPos = startPos; endPos < endPosMax; endPos++) {
          List<ConcreteRule<TK,FV>> applicableOptions = optionGrid
              .get(startPos, endPos);
          if (applicableOptions == null)
            continue;

          for (ConcreteRule<TK,FV> option : applicableOptions) {
            // assert(!hyp.foreignCoverage.intersects(option.foreignCoverage));
            // // TODO: put back

            // Force decoding check
            if (constrainedOutputSpace != null
                && !constrainedOutputSpace.allowableContinuation(
                    hyp.featurizable, option)) {
              continue;
            }

            Derivation<TK, FV> newHyp = new Derivation<TK, FV>(sourceInputId,
                option, hyp.length, hyp, featurizer, scorer, heuristic);

            if (DETAILED_DEBUG) {
              System.err.printf("creating hypothesis %d from %d\n",
                  newHyp.id, hyp.id);
              System.err.printf("hyp: %s\n",
                  newHyp.featurizable.targetPrefix);
              System.err.printf("coverage: %s\n", newHyp.sourceCoverage);
              if (hyp.featurizable != null) {
                System.err.printf("par: %s\n",
                    hyp.featurizable.targetPrefix);
                System.err.printf("coverage: %s\n", hyp.sourceCoverage);
              }
              System.err.printf("\tbase score: %.3f\n", hyp.score);
              System.err.printf("\tcovering: %s\n",
                  newHyp.rule.sourceCoverage);
              System.err.printf("\tforeign: %s\n",
                  newHyp.rule.abstractRule.source);
              System.err.printf("\ttranslated as: %s\n",
                  newHyp.rule.abstractRule.target);
              System.err.printf("\tscore: %.3f + future cost %.3f = %.3f\n",
                  newHyp.score, newHyp.h, newHyp.score());

            }
            totalHypothesesGenerated++;

            if (newHyp.featurizable.untranslatedTokens == 0
                && constrainedOutputSpace != null
                && !constrainedOutputSpace
                  .allowableFinal(newHyp.featurizable)) {
                continue;
            }

            if (newHyp.score == Double.NEGATIVE_INFINITY
                || newHyp.score == Double.POSITIVE_INFINITY
                || newHyp.score != newHyp.score) {
              // should we give a warning here?
              //
              // this normally happens when there's something brain dead about
              // the user's baseline model/featurizers,
              // like log(p) values that equal -inf for some featurizers.
              continue;
            }

            // Insert new hypothesis into sloppy beam.
            int sourceWordsCovered = newHyp.sourceCoverage.cardinality();
            beams[sourceWordsCovered].put(newHyp);

            optionsApplied++;
            localOptionsApplied++;
          }
        }
      }
      if (DETAILED_DEBUG) {
        System.err.printf("local options applied(%d): %d%n", hypPos,
            localOptionsApplied);
      }
    }

    if (DEBUG) {
      System.err.printf("Options applied: %d%n", optionsApplied);
    }
    return totalHypothesesGenerated;
  }
}
