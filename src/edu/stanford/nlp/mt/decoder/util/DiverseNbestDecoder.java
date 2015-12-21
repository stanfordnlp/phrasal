package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.util.ArraySequence;
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

  private final List<Derivation<TK,FV>> nodeList;
  private final RecombinationHistory<Derivation<TK, FV>> recombinationHistory;
  private final int prefixLength;

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
    this.recombinationHistory = recombinationHistory;
    // List to hold one instance of every node in the lattice
    nodeList = new ArrayList<>();
    Queue<Derivation<TK,FV>> unprocessed = Lists.newLinkedList(goalBeam);
    Derivation<TK,FV> oneBest = unprocessed.peek();
    LongSet uniqSet = new LongOpenHashSet();
    // Walk back through the lattice
    while( ! unprocessed.isEmpty()) {
      Derivation<TK,FV> child = unprocessed.poll();
      if (! uniqSet.contains(child.id)) {
        // Add to node list and process recombinations
        nodeList.add(child);
        uniqSet.add(child.id);
        List<Derivation<TK,FV>> recombList = recombinationHistory.recombinations(child);
//        System.err.println("recomb: sz" + recombList.size());
        for (Derivation<TK,FV> alt : recombList) {
          if (! uniqSet.contains(alt.id)) {
            nodeList.add(alt);
            uniqSet.add(alt.id);
          }        
          if (alt.parent != null && ! uniqSet.contains(alt.parent.id)) unprocessed.add(alt.parent);
        }
//        System.err.println("unproc: sz" + unprocessed.size());
        if (child.parent != null && ! uniqSet.contains(child.parent.id)) unprocessed.add(child.parent);
      }
    }
    logger.info("number of nodes in lattice {}", nodeList.size());
    
//    System.err.println(targets.get(0).toString());
//    System.err.println(oneBest);
//    System.err.println(oneBest.historyString());
//    System.err.println();
    // TODO(spenceg) Node ids are issued relative to the JVM instance, so this while break when
    // the max long value is exceeded. 
    Collections.sort(nodeList, new Comparator<Derivation<TK,FV>>() {
      @Override
      public int compare(Derivation<TK,FV> o1, Derivation<TK,FV> o2) {
        // Descending order of ids
        return (int) (o2.id - o1.id);
      }
    });
    final boolean prefixDecoding = sourceInputProperties.containsKey(InputProperty.TargetPrefix);
    prefixLength = prefixDecoding ? targets.get(0).size() : 0;
  }

  /**
   * 
   * @param size
   * @param distinct 
   * @return
   */
  public List<Derivation<TK,FV>> decode(int size, boolean distinct, int sourceInputId, 
      FeatureExtractor<TK, FV> featurizer, Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic, 
      OutputSpace<TK, FV> outputSpace) {
    List<Derivation<TK,FV>> markedNodes = new ArrayList<>();
    for (Derivation<TK,FV> node : nodeList) {
      boolean isNodeOfInterest = isNodeOfInterest(node);
      if (isNodeOfInterest) markedNodes.add(node);
      if (node.parent == null) continue; // No message to pass
      
      // Collect all parents
      List<Derivation<TK,FV>> parentList = new ArrayList<>();
      parentList.add(node.parent);
      parentList.addAll(recombinationHistory.recombinations(node.parent));
      if (parentList.size() > 1) {
        int a = 10;
        a += 5;
      }
      for (Derivation<TK,FV> parentNode : parentList) {
        // Set forward pointers
        double transitionScore = node.score - parentNode.score;
        double completionScore = transitionScore + node.bestChildScore;
        if (parentNode.bestChild == null || completionScore > parentNode.bestChildScore) {
          parentNode.bestChild = node;
          parentNode.bestChildScore = completionScore;
        }
      }
    }
    logger.info("number of marked nodes: {}", markedNodes.size());
    // Sorting merely gives an estimate. Combination costs could change the final ordering.
    Collections.sort(markedNodes, new Comparator<Derivation<TK,FV>>() {
      @Override
      public int compare(Derivation<TK, FV> o1, Derivation<TK, FV> o2) {
        double o1Estimate = o1.score + o1.bestChildScore;
        double o2Estimate = o2.score + o2.bestChildScore;
        // Descending order
        return (int) Math.signum(o2Estimate - o1Estimate);
      }
    });
    List<Derivation<TK,FV>> returnList = new ArrayList<>(size);
    IntSet uniqSet = distinct ? new IntOpenHashSet(markedNodes.size()) : null;
//    for (int i = 0, sz = Math.min(markedNodes.size(), size); i < sz; ++i) {
  for (int i = 0, sz = markedNodes.size(); i < sz; ++i) {
      Derivation<TK,FV> node = markedNodes.get(i);
      if (distinct) {
        Sequence<TK> target = extractTarget(node);
        int hashCode = target.hashCode();
        if (uniqSet.contains(hashCode)) continue;
        else uniqSet.add(hashCode);
      }
      Derivation<TK,FV> finalDerivation = constructDerivation(node, sourceInputId, featurizer,
          scorer, heuristic, outputSpace);
      returnList.add(finalDerivation);
    }
    Collections.sort(returnList);
//    returnList.stream().forEach(d -> {
//      System.err.println(d);
//    });
    return returnList;
  }

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
      if (goalHyp == null) {
        // Root node.
        goalHyp = node;
        continue;
      }
      goalHyp = new Derivation<>(sourceInputId, node.rule, goalHyp.length, goalHyp, featurizer, scorer, 
          heuristic, outputSpace);
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
    return parentLength <= prefixLength && length > prefixLength;
  }

  /**
   * Extract the target sequence from the lattice path.
   * 
   * @param node
   * @return
   */
  private Sequence<TK> extractTarget(Derivation<TK, FV> node) {
    final List<TK> tokens = new LinkedList<>();
    // Walk backward
    Derivation<TK,FV> p = node;
    while (p != null) {
      Sequence<TK> target = p.targetSequence;
      for (int i = target.size() - 1; i >= 0; --i) tokens.add(0, target.get(i));
      p = p.parent;
    }
    // Walk forward
    p = node.bestChild;
    while (p != null) {
      Sequence<TK> target = p.targetSequence;
      for (int i = 0, sz = target.size(); i < sz; ++i) tokens.add(tokens.get(i));
      p = p.bestChild;
    }
    return new ArraySequence<>(tokens);
  }
}
