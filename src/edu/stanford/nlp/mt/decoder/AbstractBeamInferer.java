package edu.stanford.nlp.mt.decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;
import edu.stanford.nlp.mt.decoder.util.BundleBeam;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.StateLatticeDecoder;
import edu.stanford.nlp.mt.decoder.util.PrefixRuleGrid;
import edu.stanford.nlp.mt.decoder.util.SyntheticRules;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DTURule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.train.AlignmentSymmetrizer.SymmetrizationType;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.FeatureValues;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

/**
 * An abstract interface for beam-based inference algorithms.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public abstract class AbstractBeamInferer<TK, FV> extends AbstractInferer<TK, FV> {

  private static final Logger logger = LogManager.getLogger(AbstractBeamInferer.class.getName());
  
  /*
   * Hyperparameters
   */
  
  // Size of the diversity window at the end of a prefix
  private static final int PREFIX_DIVERSITY_SIZE = 1;
  
  // Maximum threshold that only applies when generating distinct n-best lists
  private static final int MAX_POPPED_ITEMS = Phrasal.MAX_NBEST_SIZE * 3;

  // TODO(spenceg) Relax this constraint once we consolidate LM scores
  private static final int MAX_HYPS_PER_BEAM = 100;

  // Members
  protected final int beamCapacity;
  protected final BeamFactory.BeamType beamType;
  private final Comparator<RichTranslation<TK,FV>> translationComparator;
  
  protected boolean prefixAlignCompounds = false;

  /**
   * Constructor.
   * 
   * @param builder
   */
  protected AbstractBeamInferer(AbstractBeamInfererBuilder<TK, FV> builder) {
    super(builder);
    this.beamCapacity = builder.beamSize;
    this.beamType = builder.beamType;
    this.translationComparator = new Comparator<RichTranslation<TK,FV>>() {
      @Override
      public int compare(RichTranslation<TK, FV> o1, RichTranslation<TK, FV> o2) {
        return (int) Math.signum(o2.score - o1.score);
      }
    };
  }

  @Override
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets, int size, boolean distinct) {
    return nbest(scorer, source, sourceInputId, sourceInputProperties,
        outputSpace, targets, size, distinct);
  }
  
  /**
   * Populate the beams given the prefix. Returns 0 if the prefix is of length 0.
   * 
   * TODO(spenceg) This is less effective than the procedure in SyntheticRules, but an
   * interesting idea for future work.
   * 
   * @param source
   * @param ruleList
   * @param sourceInputProperties
   * @param prefix
   * @param scorer
   * @param beams
   * @return The beam at which standard decoding should begin.
   */
  /*
  @Deprecated
  protected int prefixFillBeams(Sequence<TK> source, List<ConcreteRule<TK,FV>> ruleList,
      InputProperties sourceInputProperties, Sequence<TK> prefix, Scorer<FV> scorer, 
      List<Beam<Derivation<TK,FV>>> beams, int sourceInputId, OutputSpace<TK, FV> outputSpace) {
    if (source == null || source.size() == 0 || prefix == null || prefix.size() == 0) return 0;
    
    // Sort rule list by target
    final PrefixRuleGrid<TK,FV> prefixGrid = new PrefixRuleGrid<>(ruleList, source, prefix);
        
    // Book-keeping
    int[] hypsForBeam = new int[beams.size()];
    int minSourceCoverage = Integer.MAX_VALUE;
    
    // Populate beams
    int numHyps = 0;
    for (int i = 0, sz = beams.size(); i < sz; ++i) {
      final int beamCardinality = i;
      for (Derivation<TK,FV> antecedent : beams.get(beamCardinality)) {
        // Check the status of this antecedent
        final int insertionPosition = antecedent.targetSequence.size();
        if (insertionPosition >= prefix.size()) {
          // Compatible derivation
          minSourceCoverage = Math.min(minSourceCoverage, antecedent.sourceCoverage.cardinality());
          continue;
        }

        // Hypothesis expansion
        final List<ConcreteRule<TK,FV>> rulesForPosition = prefixGrid.get(insertionPosition);        
        for (ConcreteRule<TK,FV> rule : rulesForPosition) {
          if (antecedent.sourceCoverage.intersects(rule.sourceCoverage)) continue; // Check source coverage
          CoverageSet sourceCoverage = antecedent.sourceCoverage.clone();
          sourceCoverage.or(rule.sourceCoverage);
          int succCardinality = sourceCoverage.cardinality();
          if (hypsForBeam[succCardinality] > MAX_HYPS_PER_BEAM) continue; // Check beam capacity
          Derivation<TK,FV> successor = new Derivation<>(sourceInputId, rule, insertionPosition, 
              antecedent, featurizer, scorer, heuristic, outputSpace);
          assert succCardinality == successor.sourceCoverage.cardinality();
          beams.get(succCardinality).put(successor);
         
          // Book-keeping
          ++numHyps;
          hypsForBeam[succCardinality]++;
        }
      }
    }

    if (minSourceCoverage > source.size()) {
      // No compatible derivations
      logger.warn("input {}: no compatible derivations. Decoding will fail", sourceInputId);
      return -1;
    } else {
      logger.info("input {}: prefix filling derivations: {}  mincoverage: {}", sourceInputId, 
          numHyps, minSourceCoverage);
      return minSourceCoverage;
    }
  }*/
  
  
  private class QueueElement implements Comparable<QueueElement> {
    double score;
    int sourceLength;
    int hypId;
    int ruleId;
    
    public QueueElement(Derivation<TK,FV> antecedent,
        ConcreteRule<TK,FV> rule,
        int sourceLength,
        int hypId,
        int ruleId) {
      score = antecedent.score() + rule.isolationScore;
      this.sourceLength = sourceLength;
      this.hypId = hypId;
      this.ruleId = ruleId;
    }
    
    @Override
    public int compareTo(QueueElement other) {
      if(score > other.score) return -1;
      if(score < other.score) return 1;
      if(sourceLength < other.sourceLength) return -1;
      if(hypId < other.hypId) return -1;
      if(ruleId < other.ruleId) return -1;
      return 0;
    }
  }
  
  private void expandQueue(PriorityQueue<QueueElement> q, 
                           List<List<Derivation<TK,FV>>> chart, 
                           PrefixRuleGrid<TK,FV> prefixGrid,
                           int sourceLength,
                           int hypId,
                           int ruleId) {
    if(chart.get(sourceLength).size() <= hypId)
      return;
    
    Derivation<TK,FV> antecedent = chart.get(sourceLength).get(hypId);
    final int insertionPosition = antecedent.targetSequence.size();
    
    // now add new candidates to the queue. (cube pruning)
    int nextRuleId = ruleId;
    ConcreteRule<TK,FV> nextRule = prefixGrid.get(insertionPosition, sourceLength, nextRuleId);
    boolean noRules = nextRule == null;
    
    while (nextRule != null) {
      if(antecedent.sourceCoverage.intersects(nextRule.sourceCoverage)) { 
        ++nextRuleId;
        nextRule = prefixGrid.get(insertionPosition, sourceLength, nextRuleId);
        continue;
      }
      q.add(new QueueElement(antecedent, nextRule, sourceLength, hypId, nextRuleId));
      break;
    }
    
    boolean startWithNextAntecedent =
        ruleId == 1 || (ruleId == 0 && (nextRuleId > 0 || noRules) );
    
    if(startWithNextAntecedent)
      expandQueue(q, chart, prefixGrid, sourceLength, hypId + 1, 0); 
  }
  
  /**
   * Populate the beams given the prefix. Returns 0 if the prefix is of length 0.
   * 
   * @param source
   * @param ruleList
   * @param sourceInputProperties
   * @param prefix
   * @param scorer
   * @param beams
   * @return The beam at which standard decoding should begin.
   */
  @Deprecated
  protected int prefixFillBeams(Sequence<TK> source, List<ConcreteRule<TK,FV>> ruleList,
      InputProperties sourceInputProperties, Sequence<TK> prefix, Scorer<FV> scorer, 
      List<Beam<Derivation<TK,FV>>> beams, int sourceInputId, OutputSpace<TK, FV> outputSpace) {
    if (source == null || source.size() == 0 || prefix == null || prefix.size() == 0) return 0;
    
    // Sort rule list by target
    final PrefixRuleGrid<TK,FV> prefixGrid = new PrefixRuleGrid<>(ruleList, source, prefix);
   
    // Special case. Uncovered material at the beginning of a prefix. Just append to the null
    // hypothesis
//    List<ConcreteRule<TK,FV>> nullRules = prefixGrid.get(0);
//    if (nullRules.size() == 0) {
//      int start = 0;
//      int end = Math.max(prefixGrid.getTargetCoverage().nextSetBit(0), prefix.size());
//      Sequence<TK> nullTarget = prefix.subsequence(start, end);
//      beams.get(0).iterator().next().targetSequence = nullTarget;
//    }
    
    // Book-keeping
    int minSourceCoverage = Integer.MAX_VALUE;
    
    // Populate beams
    int numHyps = 0;
    for (int beamCardinality = 1, sz = beams.size(); beamCardinality < sz; ++beamCardinality) {
      System.err.println("cardinality: " + beamCardinality);
      
      int hypsForBeam = 0;
      int maxSourceLength = Math.min(prefixGrid.maxSourceLength(), beamCardinality);
      Beam<Derivation<TK,FV>> beam = beams.get(beamCardinality);
      
      PriorityQueue<QueueElement> q = new PriorityQueue<>();
      
      // The chart stores the hypotheses that can be expanded to beamCardinality
      // chart.get(i).get(j) is the jth best hypothesis with cardinality (beamCardinality - i - 1)
      List<List<Derivation<TK,FV>>> chart = new ArrayList<>(maxSourceLength);
      for (int srcLen = 0; srcLen < maxSourceLength; ++srcLen){
        System.err.println("srcLen: " + srcLen);
        chart.add(new ArrayList<>(MAX_HYPS_PER_BEAM));
        
        for (Derivation<TK,FV> antecedent : beams.get(beamCardinality - srcLen - 1))
          if(antecedent.targetSequence.size() < prefix.size())
            chart.get(srcLen).add(antecedent);
        
        // initialize queue
        if(!chart.get(srcLen).isEmpty()) {
          expandQueue(q, chart, prefixGrid, srcLen, 0, 0);
        }
      }
      
      while(hypsForBeam < MAX_HYPS_PER_BEAM && !q.isEmpty()) {
        QueueElement e = q.poll();
        System.err.println("polling score = " + e.score + " " + e.hypId + " " +e.ruleId);
        Derivation<TK,FV> antecedent = chart.get(e.sourceLength).get(e.hypId);
        final int insertionPosition = antecedent.targetSequence.size();
        
        // generate new hypothesis
        ConcreteRule<TK,FV> rule = prefixGrid.get(insertionPosition, e.sourceLength, e.ruleId);
        CoverageSet sourceCoverage = antecedent.sourceCoverage.clone();
        sourceCoverage.or(rule.sourceCoverage);
        Derivation<TK,FV> successor = new Derivation<>(sourceInputId, rule, insertionPosition, 
            antecedent, featurizer, scorer, heuristic, outputSpace);
        
        assert beamCardinality == successor.sourceCoverage.cardinality();        
        beam.put(successor);
        ++numHyps;
        ++hypsForBeam;

        if (successor.targetSequence.size() >= prefix.size()) {
          // Compatible derivation
          minSourceCoverage = Math.min(minSourceCoverage, antecedent.sourceCoverage.cardinality());
        }
        
        // now add new candidates to the queue. 
        expandQueue(q, chart, prefixGrid, e.sourceLength, e.hypId, e.ruleId + 1);
      }
    }

    if (minSourceCoverage > source.size()) {
      // No compatible derivations
      logger.warn("input {}: no compatible derivations. Decoding will fail", sourceInputId);
      return -1;
    } else {
      logger.info("input {}: prefix filling derivations: {}  mincoverage: {}", sourceInputId, 
          numHyps, minSourceCoverage);
      return minSourceCoverage;
    }
  }
  
  
  
  /**
   * Query the phrase table and decide how to handle unknown words.
   * 
   * @param source
   * @param sourceInputProperties
   * @param targets
   * @param sourceInputId
   * @param scorer
   * @return
   */
  protected PhraseQuery<TK,FV> getRules(Sequence<TK> source,
      InputProperties sourceInputProperties, List<Sequence<TK>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    
    // Initial query
    List<ConcreteRule<TK,FV>> ruleList = phraseGenerator.getRules(source, sourceInputProperties, 
        sourceInputId, scorer);
    
    // Compute coverage
    final CoverageSet coverage = new CoverageSet(source.size());
    for (ConcreteRule<TK,FV> rule : ruleList) coverage.or(rule.sourceCoverage);
    
    // Decide what to do if the coverage set is incomplete
    if (coverage.cardinality() != source.size()) {
      if (filterUnknownWords) {
        // Filter OOVs from the source and then query the phrase table again
        List<TK> filteredToks = new ArrayList<>(source.size());
        for (int i = 0, sz = source.size(); i  < sz; i++) {
          if (coverage.get(i)) {
            filteredToks.add(source.get(i));
          }
        }
        Sequence<TK> sourceFiltered = filteredToks.size() > 0 ? 
            new ArraySequence<TK>(filteredToks) : Sequences.emptySequence();
        ruleList = phraseGenerator.getRules(sourceFiltered, sourceInputProperties, sourceInputId, scorer);
        return new PhraseQuery<>(sourceFiltered, ruleList);
        
      } else {
        // Add rules from the OOV model
        for (int i = 0, sz = source.size(); i  < sz; i++) {
          if (coverage.get(i)) {
            continue;
          }
          int gapIndex = i;
          Sequence<TK> queryWord = source.subsequence(gapIndex, gapIndex + 1);
          List<ConcreteRule<TK,FV>> oovRules = 
              unknownWordModel.getRules(queryWord, sourceInputProperties, sourceInputId, scorer);
          CoverageSet oovCoverage = new CoverageSet(source.size());
          oovCoverage.set(gapIndex);
          for (ConcreteRule<TK,FV> rule : oovRules) {
            // Update the coverage set for the output of the OOV model
            ruleList.add(new ConcreteRule<TK,FV>(rule.abstractRule, 
                oovCoverage, featurizer, scorer, source, sourceInputId, 
                sourceInputProperties));
          }
        }
      }
    }
    return new PhraseQuery<>(source, ruleList);
  }
  
  /**
   * Container for the result of a TM query.
   * 
   * @author Spence Green
   *
   * @param <TK>
   * @param <FV>
   */
  public static class PhraseQuery<TK,FV> {
    public final List<ConcreteRule<TK,FV>> ruleList;
    public final Sequence<TK> filteredSource;
    public PhraseQuery(Sequence<TK> filteredSource, List<ConcreteRule<TK,FV>> ruleList) {
      this.ruleList = ruleList;
      this.filteredSource = filteredSource;
    }
  }
  
  /**
   * This procedure could be made more efficient by not reconstructing the Derivation---and thus
   * running the LM---for every lattice path. However, RichTranslation depends on Featurizable,
   * which is currently difficult to reconstruct without rebuilding the whole derivation.
   */
  @Override
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets, int size, boolean distinct) {
 
    if (outputSpace != null) outputSpace.setSourceSequence(source);
    final TimeKeeper timer = TimingUtils.start();
    
    // Decoding
    RecombinationHistory<Derivation<TK, FV>> recombinationHistory = new RecombinationHistory<>();
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId, sourceInputProperties,
        recombinationHistory, outputSpace, targets, size);
    if (beam == null) return null; // Decoder failure
    timer.mark("Decode");    

    // Configure n-best extractor from goal states in final beam.
    List<Derivation<TK, FV>> goalStates = new ArrayList<>(beam.size());
    for (Derivation<TK, FV> derivation : beam) goalStates.add(derivation);
    final StateLatticeDecoder<Derivation<TK, FV>> latticeDecoder = new StateLatticeDecoder<>(
        goalStates, recombinationHistory);

    // Extract lattice paths
    final boolean prefixDecoding = sourceInputProperties.containsKey(InputProperty.TargetPrefix);
    final boolean prefixDiversity = distinct && prefixDecoding;
    final Sequence<TK> prefix = prefixDecoding ? targets.get(0) : null;
    final Set<Sequence<TK>> distinctSurfaceTranslations = distinct ? new HashSet<>(size) : null;
    final List<RichTranslation<TK, FV>> translations = new ArrayList<>(size);
    int numExtracted = 0;
    long nbestId = 0;    
    for (List<Derivation<TK, FV>> latticePath : latticeDecoder) {
      ++numExtracted;
      if (numExtracted > MAX_POPPED_ITEMS) break;

      // Check for distinct n-best before building the Derivation and thus incurring the cost
      // of running the LM.
      // TODO(spenceg) Not sure if this works for DTU since derivations aren't built up
      // left-to-right. Might need to move this check *after* building the DTUHypothesis
      // below.
      if (distinct) {
        Sequence<TK> pathTarget = extractTarget(latticePath);
        if(prefixDiversity) {
          int start = prefix.size();
          int end = Math.min(pathTarget.size(), prefix.size() + PREFIX_DIVERSITY_SIZE);
          if (start < end) pathTarget = pathTarget.subsequence(start, end);
        }
        // Seen a higher-scoring derivation with this target string before
        if (distinctSurfaceTranslations.contains(pathTarget)) continue;

        distinctSurfaceTranslations.add(pathTarget);
      }
      
      // This is very inefficient. But we need to reconstruct the Featurizable
      // object for RichTranslation below, and there's not a good way to do that without
      // building up the derivation from the list of rule applications in the lattice path.
      boolean withDTUs = false;
      final Set<Rule<TK>> seenOptions = new HashSet<>();
      Derivation<TK, FV> goalHyp = null;
      for (Derivation<TK, FV> node : latticePath) {
        if (goalHyp == null) {
          // Root node.
          goalHyp = node;
          continue;
        }
        withDTUs = withDTUs || node.rule.abstractRule instanceof DTURule;
        goalHyp = withDTUs ? new DTUHypothesis<>(sourceInputId, node.rule, goalHyp.length, goalHyp, 
            node, featurizer, scorer, heuristic, seenOptions, outputSpace)
            : new Derivation<>(sourceInputId, node.rule, goalHyp.length, goalHyp, featurizer, scorer, 
                heuristic, outputSpace);
      }
      
      // Decoder failure in which the null hypothesis was returned.
      if (goalHyp == null || goalHyp.featurizable == null) {
        logger.warn("Input {}: null hypothesis encountered. Decoder failed.", sourceInputId);
        return null;
      }
      
      if (withDTUs) {
        DTUHypothesis<TK, FV> dtuHyp = (DTUHypothesis<TK, FV>) goalHyp;
        if (!dtuHyp.isDone() || dtuHyp.hasExpired())
          logger.warn("Option not complete({},{}): {}", translations.size(), 
              dtuHyp.hasExpired(), goalHyp);
      }
      
      // Create the n-best item
      RichTranslation<TK,FV> t = new RichTranslation<>(goalHyp.featurizable, goalHyp.score, 
          FeatureValues.combine(goalHyp), nbestId++);
      translations.add(t);
      
      // Book-keeping
      if (translations.size() >= size) break;
    }
    timer.mark("Extraction");

    // If an inadmissible search heuristic is used, the hypothesis
    // scores predicted by the lattice may not actually correspond to their real
    // scores.
    Collections.sort(translations, translationComparator);
    timer.mark("Sort");    
    
    logger.info("Input {}: nbest #extracted {} max-agenda-size {}", sourceInputId, numExtracted, latticeDecoder.maxAgendaSize);
    logger.info("Input {}: nbest timing {}", sourceInputId, timer);
    
    return translations;
  }

  /**
   * Extract the target sequence from the lattice path.
   * 
   * @param latticePath
   * @return
   */
  private Sequence<TK> extractTarget(List<Derivation<TK, FV>> latticePath) {
    List<TK> tokens = new ArrayList<>(100);
    for (Derivation<TK, FV> node : latticePath) {
      if (node.rule != null) {
        for (TK token : node.rule.abstractRule.target) {
          tokens.add(token);
        }
      }
    }
    return new ArraySequence<>(tokens);
  }

  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets) {
    return translate(scorer, source, sourceInputId, sourceInputProperties, outputSpace, targets);
  }

  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets) {
    if (outputSpace != null) outputSpace.setSourceSequence(source);
    final int nbestSize = 1;
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId, sourceInputProperties,
        null, outputSpace, targets, nbestSize);
    if (beam == null) return null; // Decoder failure
    final Derivation<TK, FV> best = beam.iterator().next();
    return new RichTranslation<>(best.featurizable, best.score, FeatureValues.combine(best), 0);
  }

  /**
	 * 
	 */
  abstract protected Beam<Derivation<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory,
      OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets, int nbest);
  
  /**
   * Produce a word alignment.
   * 
   * @param source
   * @param target
   * @param sourceInputId
   * @param sourceInputProperties
   * @return
   */
  @SuppressWarnings("unchecked")
  public SymmetricalWordAlignment wordAlign(Sequence<TK> source, Sequence<TK> target, int sourceInputId,
      InputProperties sourceInputProperties) {
    
    if (! (phraseGenerator instanceof DynamicTranslationModel)) {
      throw new RuntimeException("Word alignment requires DynamicTranslationModel");
    }

    // Fetch translation models
    final List<DynamicTranslationModel<FV>> tmList = new ArrayList<>(2);
    tmList.add((DynamicTranslationModel<FV>) phraseGenerator);
    if (sourceInputProperties.containsKey(InputProperty.ForegroundTM)) {
      tmList.add((DynamicTranslationModel<FV>) sourceInputProperties.get(InputProperty.ForegroundTM));
    }
    
    SymmetricalWordAlignment alignment = SyntheticRules.bidirAlign((Sequence<IString>)source, (Sequence<IString>)target, tmList, prefixAlignCompounds, SymmetrizationType.grow_diag_final_and);
      
    SyntheticRules.resolveUnalignedTargetWords(alignment, tmList);
    
    logger.info("src: " + alignment.f());
    logger.info("tgt: " + alignment.e());
    logger.info("align: " + alignment.toString());
    
    return alignment;
  }
  
  
  public void setPrefixAlignCompounds(boolean prefixAlignCompounds) {
    this.prefixAlignCompounds = prefixAlignCompounds;
  }

  /**
   *
   */
  @SuppressWarnings("unchecked")
  protected Beam<Derivation<TK, FV>>[] createBeamsForCoverageCounts(
      int beamCnt, int capacity,
      RecombinationFilter<Derivation<TK, FV>> filter,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory) {
    Beam<Derivation<TK, FV>>[] beams = new Beam[beamCnt];
    for (int i = 0; i < beams.length; i++) {
      beams[i] = BeamFactory.factory(beamType, filter, capacity,
          recombinationHistory);
    }
    return beams;
  }

  abstract public void dump(Derivation<TK, FV> hyp);
}
