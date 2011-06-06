package edu.stanford.nlp.mt.parser;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;

public class Structure {

  protected LinkedStack<CoreLabel> stack;
  protected LinkedStack<CoreLabel> input;
  protected LinkedStack<TypedDependency> dependencies;
  protected LinkedStack<Action> actionTrace;

  public Structure() {
    stack = new LinkedStack<CoreLabel>();
    actionTrace = new LinkedStack<Action>();
  }

  public Structure(GrammaticalStructure gs) {
    this();
    dependencies = new LinkedStack<TypedDependency>(gs.typedDependencies(true));
    input = new LinkedStack<CoreLabel>();

    for (TreeGraphNode node : gs.getNodes()) {
      input.push(node.label());
    }
  }

  public Structure(List<CoreLabel> sentence) {
    this();
    input = new LinkedStack<CoreLabel>();
    dependencies = new LinkedStack<TypedDependency>();
    int index = 0;
    for (CoreLabel l : sentence) {
      CoreLabel w = new CoreLabel(l);
      w.set(IndexAnnotation.class, index++);
      input.push(w);
    }
  }

  public LinkedStack<CoreLabel> getStack() {
    return stack;
  }

  public LinkedStack<CoreLabel> getInput() {
    return input;
  }

  public LinkedStack<TypedDependency> getDependencyGraph() {
    return dependencies;
  }

  public LinkedStack<Action> getActionTrace() {
    return actionTrace;
  }
}
