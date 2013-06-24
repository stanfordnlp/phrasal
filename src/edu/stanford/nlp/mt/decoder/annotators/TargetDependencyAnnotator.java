package edu.stanford.nlp.mt.decoder.annotators;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.parser.DepDAGParser;
import edu.stanford.nlp.mt.parser.IncrementalTagger;
import edu.stanford.nlp.mt.parser.LinkedStack;
import edu.stanford.nlp.mt.parser.Structure;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * 
 * @author daniel
 *
 */
public class TargetDependencyAnnotator<TK,FV> implements Annotator<TK,FV> {
  final DepDAGParser parser;
  final int index;
  public final Structure struct;
  static IncrementalTagger tagger = new IncrementalTagger();

  private TargetDependencyAnnotator(DepDAGParser parser, Structure struct, int index) {
    this.parser = parser;
    this.struct = struct;
    this.index = index;
  }

  public TargetDependencyAnnotator(String... args) {
    String modelFile = args[0];
    boolean labelRelation = true;
    boolean extractTree = true;
    if (args.length >= 2) {
      try {
        labelRelation = Boolean.parseBoolean(args[1]);
        if(args.length > 2) extractTree = Boolean.parseBoolean(args[2]);
      } catch (Exception e) {
//         do nothing (default is labelRelation = true)
        throw new RuntimeException(String.format("Can't parse %s as a boolean value", args[1]));
      }
    }
    try {
      parser = IOUtils.readObjectFromFile(modelFile);
      parser.labelRelation = labelRelation;
      parser.extractTree = extractTree;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    System.err.printf("Finished loading %s (Labeled: %s)\n", modelFile, labelRelation);
    struct = null;
    index = -1;
  }

  @Override
  public Annotator<TK,FV> initialize(Sequence<TK> source) {
    TargetDependencyAnnotator<TK,FV> pa = new TargetDependencyAnnotator<TK,FV>(parser, new Structure(), 1);
    return pa;
  }


  @Override
  public Annotator<TK,FV> extend(ConcreteRule<TK,FV> option) {
    //System.out.println("Extend Called!");
    int localIndex = index;
    Structure localStruct = struct.clone();
    //System.out.println("Cloned structure: "+localStruct);
    //System.out.println("Extension: " + option.abstractOption.translation);
    for (TK word : option.abstractOption.target) {
      CoreLabel w = new CoreLabel();
      w.set(TextAnnotation.class, word.toString());
      w.set(IndexAnnotation.class, localIndex++);
      
      int len = Math.min(localStruct.getInput().size(), tagger.getPrefixTagger().leftWindow()) + 1;
      IString[] seq = new IString[len];
      int i = seq.length-1;
      seq[i--] = new IString(word.toString());
      Object[] toks = localStruct.getInput().peekN(len-1);
      for(Object c : toks) {
        CoreLabel cl = (CoreLabel) c;
        seq[i--] = new IString(cl.get(TextAnnotation.class));
      }
      tagger.tagWord(w, seq);
      parser.parseToken(localStruct, w);
    }
    TargetDependencyAnnotator<TK,FV> pa = new TargetDependencyAnnotator<TK,FV>(parser, localStruct, localIndex);

    return pa;
  }

  @Override
  public String toString() {
    LinkedStack<TypedDependency> deps = struct.getDependencies();
    StringBuilder builder = new StringBuilder();
    for (TypedDependency dep : deps) {
      builder.append(dep.toString());
    }
    return builder.toString();
  }

  public void addRoot() {
    struct.addRoot();
  }
}
