package edu.stanford.nlp.mt.decoder.annotators;

import java.io.IOException;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
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
public class TargetDependencyAnnotator<TK> implements Annotator<TK> {
	final DepDAGParser parser;
	final int index;
	final Structure struct;
	static IncrementalTagger tagger = new IncrementalTagger();
	
	private TargetDependencyAnnotator(DepDAGParser parser, Structure struct, int index) {
		this.parser = parser;
		this.struct = struct;
		this.index = index;
	}
	
	public TargetDependencyAnnotator(String... args) {
		String modelFile = args[0];
		try {
		  parser = IOUtils.readObjectFromFile(modelFile); 	
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		struct = null;
		index = -1;
   }
	
	@Override
	public Annotator<TK> initalize(Sequence<TK> source) {
	   TargetDependencyAnnotator<TK> pa = new TargetDependencyAnnotator<TK>(parser, new Structure(), 0);
	   return pa;
	}

	
	@Override
	public Annotator<TK> extend(ConcreteTranslationOption<TK> option) {
		int localIndex = index;
		Structure localStruct = struct.clone();
		
		for (TK word : option.abstractOption.translation) {
			CoreLabel w = new CoreLabel();
	        w.set(TextAnnotation.class, word.toString());
	        w.set(IndexAnnotation.class, localIndex++);
	        w.set(PartOfSpeechAnnotation.class, "FIXME");
            parser.parseToken(struct, w);
		}
		TargetDependencyAnnotator<TK> pa = new TargetDependencyAnnotator<TK>(parser, localStruct, localIndex);
				
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
}
