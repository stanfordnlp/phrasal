package edu.stanford.nlp.mt.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;

public class Structure {

  protected LinkedStack<IndexedWord> stack;
  protected List<IndexedWord> input;
  protected SemanticGraph dependencies;
  protected int inputIndex;
  protected List<Action> actionTrace;

  public Structure() {
    inputIndex = 0;
    stack = new LinkedStack<IndexedWord>();
    actionTrace = new ArrayList<Action>();
  }

  public Structure(GrammaticalStructure gs) {
    this();
    dependencies = new SemanticGraph(gs.typedDependencies(true),
        getRootNodes(GrammaticalStructure.getRoots(gs.typedDependencies(true))));
    int size = dependencies.size();
    input = new ArrayList<IndexedWord>();
    for (int i = 1; i <= size; i++) {
      input.add(dependencies.getNodeByIndex(i));
    }
  }

  public Structure(List<CoreLabel> sentence) {
    this();
    input = new ArrayList<IndexedWord>();
    dependencies = new SemanticGraph();
    int index = 0;
    for (CoreLabel l : sentence) {
      IndexedWord w = new IndexedWord(l);
      w.set(IndexAnnotation.class, index++);
      input.add(w);
    }
  }

  // for mt dep parser
  public Structure(Structure s) {
    stack = new LinkedStack<IndexedWord>();
    // TODO add all elements in Stack

    input = new ArrayList<IndexedWord>();
    input.addAll(s.input);
    dependencies = new SemanticGraph(s.dependencies);
    inputIndex = s.inputIndex;
    actionTrace = new ArrayList<Action>();
    actionTrace.addAll(s.actionTrace);
  }

  public LinkedStack<IndexedWord> getStack() {
    return stack;
  }

  public List<IndexedWord> getInput() {
    return input;
  }

  public SemanticGraph getDependencyGraph() {
    return dependencies;
  }

  public List<Action> getActionTrace() {
    return actionTrace;
  }

  public int getCurrentInputIndex() {
    return inputIndex;
  }

  private static Collection<TreeGraphNode> getRootNodes(
      Collection<TypedDependency> rootDependencies) {
    Collection<TreeGraphNode> govs = new HashSet<TreeGraphNode>();
    for (TypedDependency dep : rootDependencies) {
      govs.add(dep.gov());
    }
    return govs;
  }

  public void resetIndex() {
    inputIndex = 0;
    stack = new LinkedStack<IndexedWord>();
    dependencies = new SemanticGraph();
    actionTrace = new ArrayList<Action>();
  }
}
