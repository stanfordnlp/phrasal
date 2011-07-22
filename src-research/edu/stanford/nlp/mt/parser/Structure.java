package edu.stanford.nlp.mt.parser;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ValueAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
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

  @Override
  public Structure clone() {
	  return new Structure(stack.clone(), input.clone(), dependencies.clone(), actionTrace.clone());
  }
  
  private Structure(LinkedStack<CoreLabel> stack, LinkedStack<CoreLabel> input, LinkedStack<TypedDependency> dependencies, 
		  LinkedStack<Action> actionTrace) {
    this.stack = stack;
    this.actionTrace = actionTrace;
    this.input = input;
    this.dependencies = dependencies;  
  }

  public Structure() {
    stack = new LinkedStack<CoreLabel>();
    actionTrace = new LinkedStack<Action>();
    input = new LinkedStack<CoreLabel>();
    dependencies = new LinkedStack<TypedDependency>();
  }

  public Structure(GrammaticalStructure gs, IncrementalTagger tagger, Morphology lemmatizer, POSTaggerAnnotator posTagger) {
    this();
    dependencies = new LinkedStack<TypedDependency>(gs.typedDependencies(true));
    input = new LinkedStack<CoreLabel>();
    int seqLen = tagger.ts.leftWindow() + 1;

    // to check the performance of POS tagger
    boolean useStanfordTagger = true;
    List<CoreLabel> sent = new ArrayList<CoreLabel>();
    for (Tree treeNode : gs.root().getLeaves()) {
      TreeGraphNode node = (TreeGraphNode)treeNode;
      CoreLabel cl = node.label();
      cl.set(TextAnnotation.class, cl.get(ValueAnnotation.class));
      sent.add(cl);
    }
    posTagger.processText(sent);

    int idx = 0;
    for (Tree treeNode : gs.root().getLeaves()) {
      TreeGraphNode node = (TreeGraphNode)treeNode;
      CoreLabel cl = node.label();
      Tree p = treeNode.parent();
      cl.set(TextAnnotation.class, cl.get(ValueAnnotation.class));
      input.push(cl);

      if(useGoldTag) cl.set(PartOfSpeechAnnotation.class, ((TreeGraphNode)p).label().get(ValueAnnotation.class));
      else if(useStanfordTagger) {
        // do nothing
      }
      else {  // use incremental tagger
        int len = Math.min(seqLen, input.size());
        IString[] sequence = new IString[len];
        int i = sequence.length-1;
        for(Object c : input.peekN(len)) {
          CoreLabel w = (CoreLabel) c;
          sequence[i--] = new IString(w.get(TextAnnotation.class));
        }
        tagger.tagWord(cl, sequence);
      }
      if(useLemma) lemmatizer.stem(cl);

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
