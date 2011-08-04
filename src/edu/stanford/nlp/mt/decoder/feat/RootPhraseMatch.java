package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.annotators.SourceDependencyAnnotator;
import edu.stanford.nlp.mt.decoder.annotators.TargetDependencyAnnotator;
import edu.stanford.nlp.trees.TypedDependency;

public class RootPhraseMatch implements IncrementalFeaturizer<IString, String>, AlignmentFeaturizer {
    static public final String FEATURE_NAME = "RootPhraseMatch";
    static public final boolean DEBUG = true;
    
	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) { }

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
	    SourceDependencyAnnotator<IString> srcDepAnnotator = null;
	    TargetDependencyAnnotator<IString> trgDepAnnotator = null;
	    for (Annotator<IString> annotator : f.hyp.annotators) {
	    	if (annotator instanceof SourceDependencyAnnotator) {
	    		srcDepAnnotator = (SourceDependencyAnnotator<IString>)annotator;
	    	} else if (annotator instanceof TargetDependencyAnnotator) {
	    		trgDepAnnotator = (TargetDependencyAnnotator<IString>)annotator;
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
	    
	    int sourceRoot = srcDepAnnotator.gs.root().index();
	    int targetRoot = -1;
	    for (TypedDependency dep: trgDepAnnotator.struct.getDependencies()) {
	       System.out.printf("\t%s\n", dep);
	       if (dep.gov().index() == 0) {
		      targetRoot = dep.dep().index();
		   }
	    }
	    if (DEBUG) {
	    	System.out.println("Source Root: "+sourceRoot);
	    	System.out.println("Target Root: "+targetRoot);
	    }
	    if (DEBUG) {
	       System.out.printf("Source root aligned to: (%d,%d)\n", f.f2tAlignmentIndex[sourceRoot-1][0],  f.f2tAlignmentIndex[sourceRoot-1][1]);
	       System.out.printf("Target root aligned to: (%d,%d)\n", f.t2fAlignmentIndex[targetRoot-1][0],  f.f2tAlignmentIndex[targetRoot-1][1]);
	    }
	    if (f.f2tAlignmentIndex[sourceRoot-1][0] <= targetRoot && f.f2tAlignmentIndex[sourceRoot-1][1] >= targetRoot)  {
	    	return new FeatureValue<String>(FEATURE_NAME, 1.0);
		} else {	
	        return new FeatureValue<String>(FEATURE_NAME, 0.0);
		}
	}

}
