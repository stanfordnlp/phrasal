package edu.stanford.nlp.mt.decoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.InputProperties;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.BundleBeam;
import edu.stanford.nlp.mt.decoder.util.DerivationNNLM;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.HyperedgeBundle;
import edu.stanford.nlp.mt.decoder.util.HyperedgeBundle.Consequent;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.lm.JointNNLM;
import edu.stanford.nlp.mt.lm.NNLM;
import edu.stanford.nlp.mt.lm.TargetNNLM;
import edu.stanford.nlp.mt.util.NNLMUtil;
import edu.stanford.nlp.util.Generics;

/**
 * Cube pruning as described by Chiang and Huang (2007) with NNLM reranking
 * 
 * @author Thang Luong, 
 * @param <TK>
 * @param <FV>
 */
public class CubePruningNNLMDecoder<TK,FV> extends CubePruningDecoder<TK, FV> {
  // Thang Apr14
  private final boolean DEBUG = false;
  private boolean nnlmRerank = true;
  private final NNLM nnlm;
  
  static public <TK, FV> CubePruningNNLMDecoderBuilder<TK, FV> builder() {
    return new CubePruningNNLMDecoderBuilder<TK, FV>();
  }

  protected CubePruningNNLMDecoder(CubePruningNNLMDecoderBuilder<TK, FV> builder, NNLM nnlm) {
    super(builder);
    this.nnlm = nnlm;
    
    if (maxDistortion != -1) {
      System.err.printf("Cube pruning NNLM decoder %d. Distortion limit: %d%n", builder.decoderId, 
          maxDistortion);
    } else {
      System.err.printf("Cube pruning NNLM decoder %d. No hard distortion limit%n", builder.decoderId);
    }    
  }

  public static class CubePruningNNLMDecoderBuilder<TK, FV> extends CubePruningDecoderBuilder<TK, FV> {
    private NNLM jointNNLM;
    
    public CubePruningNNLMDecoderBuilder() {
      super();
    }

    public void loadNNLM(String nnlmFile, String nnlmType, int cacheSize, int miniBatchSize){
      try {
        System.err.println("# CubePruningNNLMDecoderBuilder loads NNLM: " + nnlmFile + 
            ", nnlmType=" + nnlmType + ", cacheSize=" + cacheSize + ", miniBatchSize=" + miniBatchSize);
        if (nnlmType.equalsIgnoreCase("joint")) { // Joint
          jointNNLM = new JointNNLM(nnlmFile, cacheSize, miniBatchSize);
        } else if (nnlmType.equalsIgnoreCase("target")) { // Target
          jointNNLM = new TargetNNLM(nnlmFile, cacheSize, miniBatchSize);
        } else {
          throw new RuntimeException("! Unknown nnlmType: " + nnlmType);
        }
      } catch (IOException e) {
        System.err.println("! Error loading nnlmFile in CubePruningNNLMDecoder: " + nnlmFile);
        e.printStackTrace();
      }
    }
    
    @Override
    public Inferer<TK, FV> build() {
      decoderId++;
      return new CubePruningNNLMDecoder<TK, FV>(this, jointNNLM);
    }
  }
  
  @Override
  protected Beam<Derivation<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory,
      OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets, int nbest) {
    final int sourceLength = source.size();

    // create beams. We don't need to store all of them, since the translation
    // lattice is implicitly defined by the hypotheses
    final List<BundleBeam<TK,FV>> beams = Generics.newLinkedList();

    // TM (phrase table) query for applicable rules
    List<ConcreteRule<TK,FV>> ruleList = phraseGenerator
        .getRules(source, sourceInputProperties, targets, sourceInputId, scorer);

    // Force decoding---if it is enabled, then filter the rule set according
    // to the references
    final int originalLength = ruleList.size();
    ruleList = outputSpace.filter(ruleList);
    logger.info(String.format("input %d: Rule list after pruning by output constraint: %d/%d",
        sourceInputId, ruleList.size(), originalLength));
    
    // Create rule lookup chart. Rules can be fetched by span.
    final RuleGrid<TK,FV> ruleGrid = new RuleGrid<TK,FV>(ruleList, source, true);

    // Fill Beam 0...only has one cube
    BundleBeam<TK,FV> nullBeam = new BundleBeam<TK,FV>(beamCapacity, filter, ruleGrid, 
        recombinationHistory, maxDistortion, 0);
    List<List<ConcreteRule<TK,FV>>> allOptions = Generics.newArrayList(1);
    allOptions.add(ruleList);
    DerivationNNLM<TK, FV> nullHypothesis = new DerivationNNLM<TK, FV>(sourceInputId, source, sourceInputProperties, 
        heuristic, scorer, allOptions);
    nullBeam.put(nullHypothesis);
    beams.add(nullBeam);

    // Initialize feature extractors
    featurizer.initialize(sourceInputId, ruleList, source);

    // main translation loop---beam expansion
    final int maxPhraseLength = phraseGenerator.longestSourcePhrase();
    int totalHypothesesGenerated = 1;
    int numRecombined = 0;
    int numPruned = 0;
    final long startTime = System.nanoTime();
    
    if(DEBUG) { System.err.println("# CubePruningNNLMDecoder, decoding: " + source + ", sourceLength=" + sourceLength); }
    for (int i = 1; i <= sourceLength; i++) {
      // Prune old beams
      int startBeam = Math.max(0, i-maxPhraseLength);
      if (startBeam > 0) beams.remove(0);

      // Initialize the priority queue
      Queue<Item<TK,FV>> pq = new PriorityQueue<Item<TK,FV>>(beamCapacity);
      for (BundleBeam<TK,FV> beam : beams) {
        for (HyperedgeBundle<TK,FV> bundle : beam.getBundlesForConsequentSize(i)) {
          List<Item<TK,FV>> consequents = generateConsequentsFrom(null, bundle, sourceInputId, outputSpace);
          pq.addAll(consequents);
          totalHypothesesGenerated += consequents.size();
        }
      }

      // Populate beam i by popping items and generating successors
      BundleBeam<TK,FV> newBeam = new BundleBeam<TK,FV>(beamCapacity, filter, ruleGrid, 
          recombinationHistory, maxDistortion, i);
      boolean outputConstraintsEnabled = false;
      int numPoppedItems = 0;
      while (numPoppedItems < beamCapacity && ! pq.isEmpty()) {
        Item<TK,FV> item = pq.poll();

        // Derivations can be null if the output space is constrained. This means that the derivation for this
        // item was not allowable and thus was not built. However, we need to maintain the consequent
        // so that we can generate successors.
        if (item.derivation == null) {
          ++numPruned;
        } else {
          newBeam.put(item.derivation);
        }
        outputConstraintsEnabled = outputConstraintsEnabled || item.derivation == null;
        
        List<Item<TK,FV>> consequents = generateConsequentsFrom(item.consequent, item.consequent.bundle, 
            sourceInputId, outputSpace);
        pq.addAll(consequents);
        totalHypothesesGenerated += consequents.size();
        
        if (outputConstraintsEnabled && numPoppedItems == beamCapacity-1 && newBeam.size() < sourceLength - i) {
          // Search until we build at least one derivation or the priority queue
          // is exhausted
          continue;
        } else {
          ++numPoppedItems;
        }
      }
      
      /************************************************/
      /* Thang Apr14: use NNLM to re-rank derivations */
      /************************************************/
      if(nnlmRerank){
        if(DEBUG) { System.err.println("# NNLM reranking beam " + i); }
        nnlmRerank(newBeam);
      }
      
      beams.add(newBeam);
      numRecombined += newBeam.recombined();
    }
    
    // Debug statistics
    final double elapsedTime = (System.nanoTime() - startTime) / 1e9;
    logger.info(String.format("input %d: Decoding time: %.3fsec", sourceInputId, elapsedTime));
    logger.info(String.format("input %d: #derivations generated: %d", sourceInputId, totalHypothesesGenerated));
    logger.info(String.format("input %d: #recombined: %d", sourceInputId, numRecombined));
    logger.info(String.format("input %d: #pruned by output constraint: %d", sourceInputId, numPruned));

    // Return the best beam, which should be the goal beam
    boolean isGoalBeam = true;
    Collections.reverse(beams);
    for (Beam<Derivation<TK,FV>> beam : beams) {
      if (beam.size() != 0) {
        Featurizable<TK,FV> bestHyp = beam.iterator().next().featurizable;
        if (outputSpace.allowableFinal(bestHyp)) {
          if ( ! isGoalBeam) {
            final int coveredTokens = sourceLength - bestHyp.numUntranslatedSourceTokens;
            logger.warning(String.format("input %d: DECODER FAILURE, but backed off to coverage %d/%d: ", sourceInputId,
                coveredTokens, sourceLength));
          }
          return beam;
        }
      }
      isGoalBeam = false;
    }

    logger.warning(String.format("input %d: DECODER FAILURE", sourceInputId));
    return null;
  }

  /**
   * Create the effect of re-ranking a beam by setting neural scores for NNLM derivations in the beam.
   * Later on, in BundleBeam.groupBundle(), a call to Collections.sort() will order derivations
   * according to their neural scores.
   * 
   * @param beam
   */
  private void nnlmRerank(BundleBeam<TK,FV> beam){
    Iterator<Derivation<TK,FV>> beamIter = beam.iterator();
    
    /** collect ngrams **/
    List<int[]> allNgrams = new LinkedList<int[]>();
    // to know which ngram belong to a derivation. 
    // accumCountList.get(i): total number of ngrams for derivations 0 -> i
    List<Integer> accumCountList = new ArrayList<Integer>();
    int numTotalNgrams = 0;
    while(beamIter.hasNext()){
      DerivationNNLM<TK,FV> derivation = (DerivationNNLM<TK,FV>) beamIter.next();
      @SuppressWarnings("unchecked")
      Featurizable<IString, String> f = (Featurizable<IString, String>) derivation.featurizable;
      Sequence<IString> tgtSent = f.done ?  
            Sequences.wrapStartEnd(f.targetPrefix, TokenUtils.START_TOKEN, TokenUtils.END_TOKEN) :
            Sequences.wrapStart(f.targetPrefix, TokenUtils.START_TOKEN);
      
      // extract ngrams for this derivation
      int[][] ngrams = nnlm.extractNgrams(f.sourceSentence, tgtSent, f.rule.abstractRule.alignment, 
          f.sourcePosition, f.targetPosition + 1);
      numTotalNgrams += ngrams.length;
      for (int[] ngram : ngrams) { allNgrams.add(ngram); }
      accumCountList.add(numTotalNgrams);
      
//      if (DEBUG){
//        System.err.println("# Derivation: " + derivation);
//        for (int[] ngram : ngramList) { System.err.println("  " + jointNNLM.toIString(ngram)); }
//      }
    }
    
    /** compute NNLM scores **/
    if (DEBUG){ System.err.println("# Computing nnlm scores for " + numTotalNgrams + " ngrams"); }
    double[] scores = nnlm.scoreNgrams(NNLMUtil.convertNgramList(allNgrams));
  
    /** update derivations' neural scores **/
    beamIter = beam.iterator();
    int start = 0;
    int derivationId = 0;
    while(beamIter.hasNext()){
      DerivationNNLM<TK,FV> derivation = (DerivationNNLM<TK,FV>) beamIter.next();
      int end = accumCountList.get(derivationId);
      
      double nnlmScore = 0.0;
      for (int j = start; j < end; j++) { nnlmScore += scores[j]; }
      derivation.setNNLMScore(derivation.getPrevNNLMScore() + nnlmScore);
//      if(DEBUG) { 
//        System.err.print("Derivation " + derivationId + ": " + derivation + ", incrementalNNLMScore=" + nnlmScore + " [");
//        for (int j = start; j < end; j++) { System.err.print(" " + scores[j]); }
//        System.err.println(" ]");
//      }
      
      start = end;
      derivationId++;
    }
  }

  /**
   * Searches for consequents, always returning at least one and at most two.
   * 
   * @param antecedent
   * @param bundle
   * @param sourceInputId
   * @param outputSpace
   * @return
   */
  private List<Item<TK, FV>> generateConsequentsFrom(Consequent<TK, FV> antecedent, 
      HyperedgeBundle<TK, FV> bundle, int sourceInputId, OutputSpace<TK, FV> outputSpace) {
    List<Item<TK,FV>> consequents = Generics.newArrayList(2);
    List<Consequent<TK,FV>> successors = bundle.nextSuccessors(antecedent);
    for (Consequent<TK,FV> successor : successors) {
      
      boolean buildDerivation = outputSpace.allowableContinuation(successor.antecedent.featurizable, successor.rule);
      // Derivation construction: this is the expensive part
      DerivationNNLM<TK, FV> derivation = buildDerivation ? new DerivationNNLM<TK, FV>(sourceInputId,
          successor.rule, successor.antecedent.length, (DerivationNNLM<TK, FV>) successor.antecedent, featurizer, scorer, heuristic) :
            null;
      consequents.add(new Item<TK,FV>(derivation, successor));
    }
    return consequents;
  }
  
  /**
   * Wrapper for class for the priority queue that organizes successors.
   * 
   * @author Spence Green
   *
   * @param <TK>
   * @param <FV>
   */
  protected static class Item<TK,FV> implements Comparable<Item<TK,FV>> {
    public final DerivationNNLM<TK, FV> derivation;
    public final Consequent<TK, FV> consequent;

    public Item(DerivationNNLM<TK,FV> derivation, Consequent<TK,FV> consequent) {
      this.derivation = derivation;
      this.consequent = consequent;
    }

    @Override
    public int compareTo(Item<TK,FV> o) {
      if (derivation == null && o.derivation == null) {
        return 0;
      } else if (derivation == null) {
        return -1;
      } else if (o.derivation == null) {
        return 1;
      }
      return this.derivation.compareTo(o.derivation);
    }
    
    @Override
    public String toString() {
      return derivation == null ? "<<NULL>>" : derivation.toString();
    }
  }
}
