package edu.stanford.nlp.mt.decoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
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
import edu.stanford.nlp.mt.decoder.util.SparseScorer;
import edu.stanford.nlp.mt.lm.JointNNLM;
import edu.stanford.nlp.mt.lm.NNLM;
import edu.stanford.nlp.mt.lm.PseudoNNLM;
import edu.stanford.nlp.mt.lm.TargetNNLM;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.NNLMUtil;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Similar to CubePrunningDecoder but having the NNLM reranking component.
 * 
 * @author Thang Luong
 * @param <TK>
 * @param <FV>
 */
public class CubePruningNNLMDecoder<TK,FV> extends CubePruningDecoder<TK, FV> {
  private int DEBUG_OPT = 0; // 0 -- no output, 1 -- print final translations, 2 -- lots of output!
  private boolean nnlmRerank = true;
  private NNLM nnlm;
  
  static public <TK, FV> CubePruningNNLMDecoderBuilder<TK, FV> builder() {
    return new CubePruningNNLMDecoderBuilder<TK, FV>();
  }

  protected CubePruningNNLMDecoder(CubePruningNNLMDecoderBuilder<TK, FV> builder, NNLM nnlm) {
    super(builder);
    this.nnlm = nnlm;
    
    if (maxDistortion != -1) {
      System.err.printf("Cube pruning NNLM decoder %d, NNLM. Distortion limit: %d\n", builder.decoderId,  maxDistortion);
    } else {
      System.err.printf("Cube pruning NNLM decoder %d, NNLM. No hard distortion limit\n", builder.decoderId);
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
        } else if (nnlmType.equalsIgnoreCase("pseudo")) { // Pseudo
          jointNNLM = new PseudoNNLM(nnlmFile);
        } else {
          throw new RuntimeException("! Unknown nnlmType: " + nnlmType);
        }
      } catch (IOException e) {
        System.err.println("! Error loading nnlmFile in CubePruningNNLMDecoder: " + nnlmFile);
        e.printStackTrace();
      }
    }
    
    @Override
    public Inferer<TK, FV> newInferer() {
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

    // create beams. We don't need to store all of them, since the translation
    // lattice is implicitly defined by the hypotheses
    final List<BundleBeam<TK,FV>> beams = Generics.newLinkedList();

    // TM (phrase table) query for applicable rules
    Pair<Sequence<TK>, List<ConcreteRule<TK,FV>>> sourceRulePair = 
        getRules(source, sourceInputProperties, targets, sourceInputId, scorer);
    source = sourceRulePair.first();
    if (source == null || source.size() == 0) return null;
    final int sourceLength = source.size();
    List<ConcreteRule<TK,FV>> ruleList = sourceRulePair.second();

    // Force decoding---if it is enabled, then filter the rule set according
    // to the references
    final int originalLength = ruleList.size();
    ruleList = outputSpace.filter(ruleList);
    logger.info(String.format("input %d: Rule list after pruning by output constraint: %d/%d",
        sourceInputId, ruleList.size(), originalLength));
    
    // Create rule lookup chart. Rules can be fetched by span.
    final RuleGrid<TK,FV> ruleGrid = new RuleGrid<TK,FV>(ruleList, source, true);
    if (ruleGrid.isCoverageComplete()) {
      logger.warning(String.format("Incomplete coverage for source input %d", sourceInputId));
    }
    
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
    
    if(DEBUG_OPT>0) { System.err.println("# CubePruningNNLMDecoder, decoding: " + source + ", sourceLength=" + sourceLength); }
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
        if(DEBUG_OPT>0) { System.err.println("# NNLM reranking beam " + i); }
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
          
          if(DEBUG_OPT>0){ 
            System.err.println("# Feature weights: " + ((SparseScorer) scorer).getWeightVector());
            for (Derivation<TK, FV> derivation : beam) { 
              System.err.println("# Final derivation: " + ((DerivationNNLM<TK, FV>) derivation).toString()); 
            }
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
      
      if (DEBUG_OPT>2){
        System.err.println("# Extract ngrams for derivation: " + derivation);
        System.err.println("  src=" + f.sourceSentence);
        System.err.println("  tgt=" + tgtSent);
        System.err.println("  srcPosition=" + f.sourcePosition);
        System.err.println("  tgtPosition=" + (f.targetPosition+1));
        for (int[] ngram : ngrams) { System.err.println("  ngram=" + nnlm.toIString(ngram)); }
      }
      
      accumCountList.add(numTotalNgrams);
    }
    
    /** compute NNLM scores **/
    if (DEBUG_OPT>1){ System.err.println("# Computing nnlm scores for " + numTotalNgrams + " ngrams"); }
    double[] scores;
    scores = nnlm.scoreNgrams(NNLMUtil.convertNgramList(allNgrams));
  
    /** update derivations' neural scores **/
    beamIter = beam.iterator();
    int start = 0;
    int derivationId = 0;
    double lmWeight = ((SparseScorer) scorer).getWeight(NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME);
    
    while(beamIter.hasNext()){
      DerivationNNLM<TK,FV> derivation = (DerivationNNLM<TK,FV>) beamIter.next();
      int end = accumCountList.get(derivationId);
      
      if (DEBUG_OPT>1) { //  && derivation.targetSequence.toString().startsWith("余下 servant 违抗 依纪 严处 putting") 
        System.err.println("\n# Derivation before:" + derivation); 
      }
      
      double incNNLMScore = 0;
      for (int j = start; j < end; j++) { incNNLMScore += scores[j]; }
      
      // replace the traditional lm score by the nnlm score
      double localLMScore =  derivation.getLocalLMScore(); // get local lm score
      derivation.updateNNLMScore(incNNLMScore, localLMScore, lmWeight);
      
      if (DEBUG_OPT>0) { //  && derivation.targetSequence.toString().startsWith("余下 servant 违抗 依纪 严处 putting") 
        for (int j = start; j < end; j++) {
          derivation.updateDebugMsg(nnlm.toIString(allNgrams.get(j)) + " " + Arrays.toString(allNgrams.get(j)) + " " + scores[j]);
          
          if(DEBUG_OPT>1){
            System.err.println("  " + nnlm.toIString(allNgrams.get(j)) + "\t" + scores[j]);
          }
        }
        if(DEBUG_OPT>1){
          System.err.println("  incNNLMScore=" + incNNLMScore + ", localLMScore=" + localLMScore + ", lmWeight=" + lmWeight);
          System.err.println("# Derivation after:" + derivation);
        }
      }
      
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
      
      if(DEBUG_OPT>2) { System.err.println("# Generate from antecedent=" + ((antecedent==null)? "\"\"" : antecedent.antecedent) + " -> " + derivation); }
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
  
  /**
   * Extract ngrams that we want to score after adding a phrase pair. 
   * 
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return list of ngrams, each of which consists of NPLM ids.
   */
  public List<Sequence<IString>> extractNgrams(Sequence<IString> srcSent, Sequence<IString> tgtSent, 
      PhraseAlignment alignment, int srcStartPos, int tgtStartPos, int targetOrder){
    int tgtLen = tgtSent.size();
    List<Sequence<IString>> ngrams = new LinkedList<Sequence<IString>>();
    
    for (int pos = tgtStartPos; pos < tgtLen; pos++) {
      ngrams.add(extractNgram(pos, srcSent, tgtSent, alignment, srcStartPos, tgtStartPos, targetOrder));
    }
    
    return ngrams;
  }
  
  /**
   * Extract an ngram (for debug purpose). 
   * 
   * @param pos -- tgt position of the last word in the ngram to be extracted (should be >= tgtStartPos, < tgtSent.size())
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return list of ngrams, each of which consists of NPLM ids.
   */
  private Sequence<IString> extractNgram(int pos, Sequence<IString> srcSent, Sequence<IString> tgtSent, 
      PhraseAlignment alignment, int srcStartPos, int tgtStartPos, int tgtOrder){
    /* we don't use srcSent, alignment, srcStartPos */
    
    int tgtLen = tgtSent.size();
    assert(pos>=tgtStartPos && pos<tgtLen);
    
    IString istring;
    List<IString> istringList = new LinkedList<IString>();
    
    // extract tgt subsequence
    int tgtSeqStart = pos - tgtOrder + 1;
    for (int tgtPos = tgtSeqStart; tgtPos <= pos; tgtPos++) {        
      if(tgtPos<0) { istring = TokenUtils.START_TOKEN; } // start
      else { // within range 
        istring = tgtSent.get(tgtPos);
      }
      istringList.add(istring);
    }
    
    return new SimpleSequence<IString>(istringList);
  }
}
