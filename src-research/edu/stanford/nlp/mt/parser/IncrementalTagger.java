package edu.stanford.nlp.mt.parser;

import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.tools.PrefixTagger;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.Pair;

public class IncrementalTagger {

  protected final MaxentTagger tagger;
  protected final PrefixTagger ts;
  
  public PrefixTagger getPrefixTagger() { return ts; }

  /** load default MaxentTagger model */
  public IncrementalTagger() {
    try {
      this.tagger = new MaxentTagger(MaxentTagger.DEFAULT_NLP_GROUP_MODEL_PATH);
      this.ts = new PrefixTagger(tagger);
      assert(ts.rightWindow()==0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  /** load MaxentTagger model */
  public IncrementalTagger(String modelfile) {
    try {
      this.tagger = new MaxentTagger(modelfile);
      this.ts = new PrefixTagger(tagger);
      assert(ts.rightWindow()==0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void tagWord(CoreLabel cl, IString[] sequence) {
    Pair<IString, Float> tag = ts.getBestTag(sequence);
    cl.set(PartOfSpeechAnnotation.class, tag.first.word());
  }
}
