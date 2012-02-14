package edu.stanford.nlp.mt.sem;

import java.util.HashSet;
import java.util.Set;

import org.jgrapht.DirectedGraph;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.SimpleGraph;

import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * A place for experimental GrammaticalStructure utilities 
 * that I'd like to keep out of Core for the time being 
 * as I'm not sure how generally useful they will be.
 * 
 * @author danielcer
 *
 */
public class GrammaticalStructures {
   
   private GrammaticalStructures() { }
   
   public static DirectedGraph<TreeGraphNode, TypedDependency> toDirectedGraph(GrammaticalStructure gs, boolean forward) {
      DirectedGraph<TreeGraphNode, TypedDependency> jgt =
         new DefaultDirectedGraph<TreeGraphNode, TypedDependency>(TypedDependency.class);
      for (TypedDependency dep : gs.allTypedDependencies()) {
         if (forward) {
           jgt.addEdge(dep.dep(), dep.gov(), dep); 
         } else { 
            jgt.addEdge(dep.dep(), dep.gov(), dep);
         }
      }
      return jgt;
   }
   
   public static UndirectedGraph<TreeGraphNode, TypedDependency> toGraph(GrammaticalStructure gs) {
      UndirectedGraph<TreeGraphNode, TypedDependency> jgt =
         new SimpleGraph<TreeGraphNode, TypedDependency>(TypedDependency.class);
      
      Set<TreeGraphNode> nodes = new HashSet<TreeGraphNode>();
      for (TypedDependency dep : gs.allTypedDependencies()) {
         nodes.add(dep.gov()); nodes.add(dep.dep());
      }
      for (TreeGraphNode node : nodes) { 
         System.err.println("Adding: " + node);
         jgt.addVertex(node);
      }
      for (TypedDependency dep : gs.allTypedDependencies()) {
         System.err.println("Adding: " + dep);
         jgt.addEdge(dep.dep(), dep.gov(), dep); 
      }
      return jgt;
   }
   
}
