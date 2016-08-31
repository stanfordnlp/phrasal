package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Extraction of diverse n-best lists relative to a prefix.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class DiverseNbestDecoder<TK,FV> {

  private static final Logger logger = LogManager.getLogger(DiverseNbestDecoder.class.getName());

  private final int prefixLength;
  private final List<Derivation<TK,FV>> markedNodes;

  private boolean isIncompleteLattice = false;
  
  // WSGDEBUG
//  private Derivation<TK,FV> oneBest;
//  private LongSet oneBestIds;
  //private final Sequence<TK> prefix;
  
  /**
   * Constructor.
   * 
   * @param goalBeam
   * @param recombinationHistory
   * @param sourceInputProperties
   * @param sourceInputId
   * @param targets
   * @param outputSpace
   */
  public DiverseNbestDecoder(Beam<Derivation<TK, FV>> goalBeam, 
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory, 
      InputProperties sourceInputProperties, List<Sequence<TK>> targets) {
    final boolean prefixDecoding = sourceInputProperties.containsKey(InputProperty.TargetPrefix);
    this.prefixLength = prefixDecoding ? targets.get(0).size() : 0;
    this.markedNodes = new ArrayList<>();

    // WSGDEBUG
    //this.prefix = targets.get(0);
    
    // Max queue of nodes
    Queue<Derivation<TK,FV>> unprocessed = new PriorityQueue<Derivation<TK,FV>>(goalBeam.size(),  
        new Comparator<Derivation<TK,FV>>() {
      @Override
      public int compare(Derivation<TK,FV> o1, Derivation<TK,FV> o2) {
        // Descending order of ids, so goal nodes are processed first.
        return (int) (o2.id - o1.id);
      }
    });
    for (Derivation<TK,FV> d : goalBeam) {
      // WSGDEBUG
//      if (this.oneBest == null) this.oneBest = d;
      
      unprocessed.add(d);
    }
    
    // WSGDEBUG
//    setOneBestPointers();
//    System.err.println(prefix);
//    System.err.println(oneBest);
//    System.err.println(oneBest.historyString());
//    System.err.println();
    
    // Walk back through the lattice
    final LongSet visited = new LongOpenHashSet();
    while( ! unprocessed.isEmpty()) {
      final Derivation<TK,FV> child = unprocessed.poll();
      
      // WSGDEBUG
//      System.err.printf("%d <- %d%n", child.parent == null ? -1 : child.parent.id, child.id);
      
      if (! visited.contains(child.id)) {
        visited.add(child.id);
        if (isNodeOfInterest(child)) markedNodes.add(child);
        
        // Set parent pointers
        if (child.parent != null) {
          setParentPointers(child);         
          if (! visited.contains(child.parent.id)) unprocessed.add(child.parent);
        }
                
        // Process the recombination list
        for (Derivation<TK,FV> recombinedChild : recombinationHistory.recombinations(child)) {
          if (! visited.contains(recombinedChild.id)) {
            visited.add(recombinedChild.id);
            if (isNodeOfInterest(recombinedChild)) markedNodes.add(recombinedChild);

            // WSGDEBUG
//            System.err.printf("  %d <- %d%n", recombinedChild.parent == null ? -1 : 
//              recombinedChild.parent.id, recombinedChild.id);

            // Set parent
            if (recombinedChild.parent != null) {
              setParentPointers(recombinedChild);         
              if (! visited.contains(recombinedChild.parent.id)) unprocessed.add(recombinedChild.parent);
            }
            
            // Also need to set child for recombined nodes
            Derivation<TK,FV> bestChild = child.bestChild;
            if (bestChild != null) {
              // child isn't a goal node
              double transitionScore = bestChild.score - recombinedChild.score;
              double completionScore = transitionScore + bestChild.completionScore;
              recombinedChild.bestChild = bestChild;
              recombinedChild.completionScore = completionScore;
            } else if (! (recombinedChild.isDone() && child.isDone())) {
              logger.warn("Incomplete lattice. Probably the output of decoder backoff.");
              this.isIncompleteLattice = true;
              return;
            }
          }        
        }
      }
    }
    logger.info("Nodes in lattice: {}", visited.size());
    
    // WSGDEBUG
//    System.err.printf("%d / %d%n", markedNodes.size(), visited.size());
//    System.err.println(targets.get(0).toString());
//    System.err.println(oneBest);
//    System.err.println(oneBest.historyString());
//    System.err.println();
//    setOneBestPointers();
    
    // Sorting merely gives an estimate. Combination costs could change the final ordering.
    // Sort the return list
//    Collections.sort(markedNodes, new Comparator<Derivation<TK,FV>>() {
//      @Override
//      public int compare(Derivation<TK, FV> o1, Derivation<TK, FV> o2) {
//        double o1Estimate = o1.score + o1.completionScore;
//        double o2Estimate = o2.score + o2.completionScore;
//        // Descending order
//        return (int) Math.signum(o2Estimate - o1Estimate);
//      }
//    });
  }

  private void setParentPointers(Derivation<TK, FV> child) {
    Derivation<TK,FV> parentNode = child.parent;
    double transitionScore = child.score - parentNode.score;
    double completionScore = transitionScore + child.completionScore;
    if (parentNode.bestChild == null || completionScore > parentNode.completionScore) {
      parentNode.bestChild = child;
      parentNode.completionScore = completionScore;
    }
    
    // WSGDEBUG
//    if (parentNode.isOneBest && completionScore > parentNode.completionScore) {
//      double goalScore = getGoalScore(child);
//      double bestChildGoalScore = getGoalScore(parentNode.bestChild);
//      System.err.println(prefix);
//      System.err.println(parentNode);
//      System.err.println(child);
//      System.err.println(child.id);
//      System.err.println(parentNode.bestChild);
//      System.err.println(parentNode.bestChild.id);
//      System.err.println(parentNode.bestChild.score - parentNode.score);
//      System.err.println(transitionScore);
//      System.err.println(child.isOneBest);
//      System.err.printf("%s %s %s %s%n", Double.toString(transitionScore), 
//          Double.toString(child.completionScore), 
//          Double.toString(completionScore), Double.toString(parentNode.completionScore));
//    }
  }

//  private void setOneBestPointers() {
//    Derivation<TK,FV> p = oneBest;
//    oneBestIds = new LongOpenHashSet();
//    while (p.parent != null) {
//      oneBestIds.add(p.id);
//      p.isOneBest = true;
//      double transitionScore = p.score - p.parent.score;
//      double completionScore = transitionScore + p.completionScore;
////      System.err.printf("%s %s%n", Double.toString(transitionScore), Double.toString(p.completionScore));
//      p.parent.completionScore = completionScore;
//      p.parent.bestChild = p;
//      p = p.parent;
//    }
//  }

  /**
   * Extract the n-best list.
   * 
   * @param size
   * @param distinct 
   * @return
   */
  public List<Derivation<TK,FV>> decode(int size, boolean distinct, int sourceInputId, 
      FeatureExtractor<TK, FV> featurizer, Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic, 
      OutputSpace<TK, FV> outputSpace) {
    if (isIncompleteLattice) return Collections.emptyList();
    
    List<Derivation<TK,FV>> returnList = new ArrayList<>(size);

    // WSGDEBUG

    // TODO(spenceg) Remaining bugs
    //
    //  1) Sometimes duplicate derivations can be extracted. Probably has to do with recombination.
    //
    for (int i = 0, sz = markedNodes.size(); i < sz; ++i) {
//    for (int i = 0, sz = Math.min(markedNodes.size(), size); i < sz; ++i) {
      Derivation<TK,FV> node = markedNodes.get(i);
      Derivation<TK,FV> finalDerivation = constructDerivation(node, sourceInputId, featurizer,
          scorer, heuristic, outputSpace);
      returnList.add(finalDerivation);
    }
  
    // Sort the return list
    returnList = returnList.stream().sorted().limit(size).collect(Collectors.toList());
    
    // Apply distinctness after the sort. The ordering of markedNodes doesn't account for
    // combination costs.
    if (distinct) {
      IntSet uniqSet = new IntOpenHashSet(markedNodes.size());
      List<Derivation<TK,FV>> uniqList = new ArrayList<>(returnList.size());
      for (Derivation<TK,FV> d : returnList) {
        int hashCode = d.targetSequence.hashCode();
        if (! uniqSet.contains(hashCode)) {
          uniqSet.add(hashCode);
          uniqList.add(d);
        }
      }
      returnList = uniqList;
    }
    
    // WSGDEBUG
//    System.err.printf("### %d: %d marked nodes ########%n", sourceInputId, markedNodes.size());
//    System.err.println(prefix);
//    System.err.println(oneBest);
//    System.err.println("-------");
//    returnList.stream().forEach(d -> {
//      System.err.println(d);
//    });
//    if (returnList.get(0).score < oneBest.score) {
//      System.err.println(returnList.get(0));
//      System.err.println(oneBest);
//    }
    
    return returnList;
  }

//  private double getGoalScore(Derivation<TK, FV> node) {
//    while(!node.isDone()) {
//      System.err.println(node.id);
//      node = node.bestChild;
//    }
//    return node.score;
//  }

  /**
   * Construct a new derivation from a node of interest.
   * 
   * @param markedNode
   * @param sourceInputId
   * @param featurizer
   * @param scorer
   * @param heuristic
   * @param outputSpace
   * @return
   */
  private Derivation<TK, FV> constructDerivation(Derivation<TK, FV> markedNode, int sourceInputId, 
      FeatureExtractor<TK, FV> featurizer, Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic, 
      OutputSpace<TK, FV> outputSpace) {
    final List<Derivation<TK,FV>> nodes = new LinkedList<>();
    // Walk backward
    Derivation<TK,FV> p = markedNode;
    while (p != null) {
      nodes.add(0, p);
      p = p.parent;
    }
    // Walk forward
    p = markedNode.bestChild;
    while (p != null) {
      nodes.add(p);
      p = p.bestChild;
    }

    // Iterate over derivation list to make the final derivation
    Derivation<TK, FV> goalHyp = null;
    for (Derivation<TK, FV> node : nodes) {
      goalHyp = goalHyp == null ? node : new Derivation<>(sourceInputId, node.rule, goalHyp.length, 
          goalHyp, featurizer, scorer, heuristic, outputSpace);
    }
    return goalHyp;
  }

  /**
   * Nodes of interest are defined as:
   *   parent.targetSequence <= prefix.length &&
   *   node.targetSequence > prefix.length
   * 
   * @param node
   * @return
   */
  private boolean isNodeOfInterest(Derivation<TK,FV> node) {
    final int parentLength = node.parent == null ? 0 : node.parent.targetSequence.size();
    final int length = node.targetSequence.size();
    return parentLength <= prefixLength && (length > prefixLength || node.isDone());
  }

  /**
   * Extract the target sequence from the lattice path.
   * 
   * @param node
   * @return
   */
//  private Sequence<TK> extractTarget(Derivation<TK, FV> node) {
//    final List<TK> tokens = new LinkedList<>();
//    // Walk backward
//    Derivation<TK,FV> p = node;
//    while (p != null) {
//      Sequence<TK> target = p.rule == null ? Sequences.emptySequence() : p.rule.abstractRule.target;
//      for (int i = target.size() - 1; i >= 0; --i) tokens.add(0, target.get(i));
//      p = p.parent;
//    }
//    // Walk forward
//    p = node.bestChild;
//    while (p != null) {
//      Sequence<TK> target = p.rule.abstractRule.target;
//      for (int i = 0, sz = target.size(); i < sz; ++i) tokens.add(target.get(i));
//      p = p.bestChild;
//    }
//    return new ArraySequence<>(tokens);
//  }
}
