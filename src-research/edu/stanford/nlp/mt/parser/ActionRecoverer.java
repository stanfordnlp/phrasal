package edu.stanford.nlp.mt.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.mt.parser.Actions.ActionType;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.EnglishGrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;

public class ActionRecoverer {
  
  // check the correctness of recovered action trace
  private static final boolean checkCorrectness = false;
  
  /** recover the list of actions for constructing s.dependencies
   *  the actions are stored in s.actionTrace */
  private static void recoverActionTrace(Structure s) {
    // extract arc & relation
    Counter<IndexedWord> leftsideEdgeCounter = new OpenAddressCounter<IndexedWord>();
    Set<Pair<IndexedWord, IndexedWord>> arcs = new HashSet<Pair<IndexedWord, IndexedWord>>();
    Map<Pair<IndexedWord,IndexedWord>, GrammaticalRelation> arcRelation = 
      new HashMap<Pair<IndexedWord,IndexedWord>, GrammaticalRelation>();
    
    for(SemanticGraphEdge e : s.dependencies.edgeSet()) {
      IndexedWord gov = e.getGovernor();
      IndexedWord dependent = e.getDependent();
      Pair<IndexedWord, IndexedWord> pair = new Pair<IndexedWord, IndexedWord>(gov, dependent);      
      arcs.add(pair);
      arcRelation.put(pair, e.getRelation());
      
      if(gov.get(IndexAnnotation.class) < dependent.get(IndexAnnotation.class)) {
        leftsideEdgeCounter.incrementCount(dependent);
      } else {
        leftsideEdgeCounter.incrementCount(gov);
      }
    }
    
    // recover actions
    Set<IndexedWord> dependents = new HashSet<IndexedWord>();
    while(s.inputIndex < s.input.size()) {
      IndexedWord w = s.input.get(s.inputIndex);
      if(leftsideEdgeCounter.getCount(w) > 0) {
        IndexedWord topStack = s.stack.peek();
        
        Pair<IndexedWord, IndexedWord> govPair = new Pair<IndexedWord, IndexedWord>(w, topStack);
        Pair<IndexedWord, IndexedWord> depPair = new Pair<IndexedWord, IndexedWord>(topStack, w);
        
        if(arcs.contains(govPair)) { // left-arc
          s.actionTrace.add(new Action(ActionType.LEFT_ARC, arcRelation.get(govPair)));
          dependents.add(topStack);
          leftsideEdgeCounter.decrementCount(w);
          arcs.remove(govPair);
        } else if (arcs.contains(depPair)) { // right-arc
          s.actionTrace.add(new Action(ActionType.RIGHT_ARC, arcRelation.get(depPair)));
          dependents.add(w);
          leftsideEdgeCounter.decrementCount(w);
          arcs.remove(depPair);
        } else {
          if(!dependents.contains(s.stack.pop())) {
            throw new RuntimeException();
          }
          s.actionTrace.add(new Action(ActionType.REDUCE));
        }        
      } else {
        s.stack.push(w);
        s.inputIndex++;
        s.actionTrace.add(new Action(ActionType.SHIFT));
      }
    }
  }
  
  /** check the correctness of recovered action trace */
  private static void checkRecoveredActionTrace(Structure s){
    SemanticGraph gold = s.dependencies;
    List<Action> recoveredActions = s.actionTrace;
    s.resetIndex();
    
    for(Action a : recoveredActions) {
      Actions.doAction(a, s);
    }
    Set<SemanticGraphEdge> goldEdges = gold.edgeSet();
    for(SemanticGraphEdge e : s.dependencies.edgeList()) {
      if(!goldEdges.contains(e)) {
        throw new RuntimeException("Error in recovered actions");
      }
    }    
  }
  
  public static List<Structure> readTrainingData(String filename) throws IOException{
    List<GrammaticalStructure> gsList = 
      EnglishGrammaticalStructure.readCoNLLXGrammaticStructureCollection(filename);
    List<Structure> structures = new ArrayList<Structure>();
    for(GrammaticalStructure gs : gsList){
      Structure s = new Structure(gs);      
      recoverActionTrace(s);
      structures.add(s);
      if(checkCorrectness) checkRecoveredActionTrace(s);
    }
    return structures;
  }
  
  public static void main(String[] args) throws IOException{
    String filename = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
    List<Structure> structures = readTrainingData(filename);

    for(Structure s : structures) {
      checkRecoveredActionTrace(s);
    }
  }
}
