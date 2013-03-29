package edu.stanford.nlp.mt.decoder.feat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.annotators.SourceDependencyAnnotator;
import edu.stanford.nlp.mt.decoder.annotators.TargetDependencyAnnotator;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Index;

public class RootPhraseMatch implements IncrementalFeaturizer<IString, String>, AlignmentFeaturizer {
    static public final String FEATURE_NAME_MATCH = "RootPhraseMatch";
    static public final String FEATURE_NAME_MISMATCH = "RootPhraseMisMatch";
    static public final boolean DEBUG = true;
    
	@Override
	public void initialize(List<ConcreteTranslationOption<IString,String>> options,
			Sequence<IString> foreign, Index<String> featureIndex) { }

	@Override
	public void reset() { }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
	    return null;
	}

	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) {
		if (!f.hyp.isDone()) return null;
		
	    boolean rootMatch = false;
	    // TODO: fix API so that this isn't necessary
	    SourceDependencyAnnotator<IString,String> srcDepAnnotator = null;
	    TargetDependencyAnnotator<IString,String> trgDepAnnotator = null;
	    for (Annotator<IString,String> annotator : f.hyp.annotators) {
	    	if (annotator instanceof SourceDependencyAnnotator) {
	    		srcDepAnnotator = (SourceDependencyAnnotator<IString,String>)annotator;
	    	} else if (annotator instanceof TargetDependencyAnnotator) {
	    		trgDepAnnotator = (TargetDependencyAnnotator<IString,String>)annotator;
	    	}
	    }
	    if (srcDepAnnotator == null || trgDepAnnotator == null) {
	    	throw new RuntimeException("Both SourceDependencyAnnoator and TargetDependencyAnnotator are required for " + this.getClass().getCanonicalName());
	    }
	    if (DEBUG) {
	     System.out.println("Source Dependency Annotator:");
	     for (TypedDependency dep : srcDepAnnotator.gs.typedDependencies()) {
	    	  System.out.printf("\t%s\n", dep);
	      } 
	      System.out.println("Target Dependency Annotator:");
	      for (TypedDependency dep: trgDepAnnotator.struct.getDependencies()) {
	          System.out.printf("\t%s\n", dep);
	      } 
	    }
	    
	    int sourceRoot = -1;
	    for (TypedDependency dep: srcDepAnnotator.gs.typedDependencies()) {
	    	System.out.printf("\t%s: gov().index(): %d\n", dep, dep.gov().index());
	    	if (dep.gov().index() == 0) {
	        	sourceRoot = dep.dep().index();
	        }
	    }
	    
	    Set<Integer> possibleRoots = new HashSet<Integer>();
	    
	    for (TypedDependency dep: trgDepAnnotator.struct.getDependencies()) {
	       possibleRoots.add(dep.gov().index());
	    }
	    
	    for (TypedDependency dep: trgDepAnnotator.struct.getDependencies()) {
		   possibleRoots.remove(dep.dep().index());
		}
	    
	    if (DEBUG) {
	    	System.out.println("Source Root: "+sourceRoot);
	    	System.out.println("Target Root: "+possibleRoots);
	    }
	    
	    /*if (DEBUG) {
	       System.out.printf("Source root aligned to: (%d,%d)\n", 
	    		   f.f2tAlignmentIndex[sourceRoot-1][0],  f.f2tAlignmentIndex[sourceRoot-1][1]);
	       System.out.printf("Target root aligned to: (%d,%d)\n", 
	    		   f.t2fAlignmentIndex[targetRoot-1][0],  f.f2tAlignmentIndex[targetRoot-1][1]);
	    }
	    */
	    for (Integer possibleRoot : possibleRoots) {
	       int targetRoot = possibleRoot;
	       if (f.f2tAlignmentIndex[sourceRoot-1][0] <= targetRoot && f.f2tAlignmentIndex[sourceRoot-1][1] >= targetRoot)  {
	    	   return new FeatureValue<String>(FEATURE_NAME_MATCH, 1.0);
	       }    
	    }
	    return new FeatureValue<String>(FEATURE_NAME_MISMATCH, 1.0);
	}

}
