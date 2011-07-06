package edu.stanford.nlp.mt.parser;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ValueAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.mt.tools.MajorityTagger;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.tagger.common.TaggerConstants;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;

public class Structure {

  protected static final boolean useGoldTag = false;
  private static final boolean useLemma = true;

  protected LinkedStack<CoreLabel> stack;
  protected LinkedStack<CoreLabel> input;
  protected LinkedStack<TypedDependency> dependencies;
  protected LinkedStack<Action> actionTrace;

  public Structure() {
    stack = new LinkedStack<CoreLabel>();
    actionTrace = new LinkedStack<Action>();
    input = new LinkedStack<CoreLabel>();
    dependencies = new LinkedStack<TypedDependency>();
  }

  public Structure(GrammaticalStructure gs, MajorityTagger tagger, Morphology lemmatizer) {
    this();
    dependencies = new LinkedStack<TypedDependency>(gs.typedDependencies(true));
    input = new LinkedStack<CoreLabel>();

    for (Tree treeNode : gs.root().getLeaves()) {
      TreeGraphNode node = (TreeGraphNode)treeNode;
      CoreLabel cl = node.label();
      Tree p = treeNode.parent();
      cl.set(TextAnnotation.class, cl.get(ValueAnnotation.class));
      if(useGoldTag) cl.set(PartOfSpeechAnnotation.class, ((TreeGraphNode)p).label().get(ValueAnnotation.class));
      else {  // use majority tagger
        tagger.tagWord(cl, (input.size()==0));
      }
      if(useLemma) lemmatizer.stem(cl);
      input.push(cl);
    }

    // padding EOS token to guarantee every word has the right word
    CoreLabel cl = new CoreLabel();
    cl.set(TextAnnotation.class, TaggerConstants.EOS_WORD);
    cl.set(PartOfSpeechAnnotation.class, TaggerConstants.EOS_TAG);
    cl.set(IndexAnnotation.class, input.size()+1);
    input.push(cl);
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

  public LinkedStack<TypedDependency> getDependencies() {
    return dependencies;
  }

  public LinkedStack<Action> getActionTrace() {
    return actionTrace;
  }

  public void reset() {
    stack = new LinkedStack<CoreLabel>();
    actionTrace = new LinkedStack<Action>();
    dependencies = new LinkedStack<TypedDependency>();
    input = new LinkedStack<CoreLabel>();
  }
}
