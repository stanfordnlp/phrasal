package edu.stanford.nlp.mt.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;

public class Structure {

  protected Stack<IndexedWord> stack;
  protected List<IndexedWord> input;
  protected SemanticGraph dependencies;
  protected int inputIndex;
  protected List<Action> actionTrace;
  
  public Structure() {
    inputIndex = 0;
    stack = new Stack<IndexedWord>();
    actionTrace = new ArrayList<Action>();
  }
  public Structure(GrammaticalStructure gs) {
    this();
    dependencies = new SemanticGraph(gs.typedDependencies(true), getRootNodes(GrammaticalStructure.getRoots(gs.typedDependencies(true))));
    int size = dependencies.size();
    input = new ArrayList<IndexedWord>();
    for (int i=1; i <= size; i++) {
      input.add(dependencies.getNodeByIndex(i));
    }
  }
  
  public Stack<IndexedWord> getStack() { return stack; }
  public List<IndexedWord> getInput() { return input; }
  public SemanticGraph getDependencyGraph() { return dependencies; }
  public List<Action> getActionTrace() { return actionTrace; }
  public int getCurrentInputIndex() { return inputIndex; }

  private static Collection<TreeGraphNode> getRootNodes(Collection<TypedDependency> rootDependencies){
    Collection<TreeGraphNode> govs = new HashSet<TreeGraphNode>();
    for (TypedDependency dep : rootDependencies) {
      govs.add(dep.gov());
    }
    return govs;
  }
  
  public void resetIndex() {
    inputIndex = 0;
    stack = new Stack<IndexedWord>();
    dependencies = new SemanticGraph();
    actionTrace = new ArrayList<Action>();
  }
}
