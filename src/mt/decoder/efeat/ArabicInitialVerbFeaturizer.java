package mt.decoder.efeat;

import mt.base.*;
import mt.base.Featurizable;
import mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.tagger.maxent.TaggerConfig;

import java.util.List;
import java.util.Arrays;

/**
 * @author Michel Galley
 */
public class ArabicInitialVerbFeaturizer implements IncrementalFeaturizer<IString,String> {

  static final String FEATURE_NAME = "EnglishVSOPenalty";
  private static final double ENGLISH_VSO_PENALTY = -100;

  private static final String TAG_INT_PART = "INTERROG+PART"; // allow English VSO if a question
  private static final String[] TAG_SKIPS  = new String[] { "CONJ", "PUNC" };
  private static final String[] TAG_VERBS  = new String[] { "PV", "IV", "VERB" };

  /** The Arabic tagger (must use IBM tags!). */
  private static MaxentTagger tagger;
  private static String DEFAULT_TAGGER_FILE = "/scr/nlp/data/gale2/IBM_ATB/ibm-stanfordized/utf8/arabic.tagger";

  /** Tagged sentence. */
  private Sentence<TaggedWord> sentence = null;

  /** True if first word is a verb. */
  private boolean isVerb = false;

  public ArabicInitialVerbFeaturizer() {
    this(DEFAULT_TAGGER_FILE);
  }

  public ArabicInitialVerbFeaturizer(String taggerFile) {
    try {
      System.err.printf("Loading tagger from serialized file %s ...\n", taggerFile);
      // mg2008: note this doesn't load the serialized config file!!
      // tagger = new MaxentTagger(taggerFile);
      tagger = new MaxentTagger();
      TaggerConfig config = new TaggerConfig(new String[] {"-model",taggerFile});
      MaxentTagger.init(config.getModel(),config);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    if(f.prior == null && f.linearDistortion == 0 && isVerb) {
			return new FeatureValue<String>(FEATURE_NAME, ENGLISH_VSO_PENALTY);
		}
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {

    String[] words = IStrings.toStringArray(Sequences.toIntArray(foreign));
    sentence = tagger.processSentence(Sentence.toSentence(Arrays.asList(words)));
    TaggedWord[] tags = sentence.toArray(new TaggedWord[sentence.size()]);

    System.err.println("Arabic input sentence tagged as:");
    System.err.println(Arrays.toString(tags));

    // First skip some words such as conjunctions and punctuations:
    int pos=0;
    for(; pos < sentence.size(); ++pos) {
      String curTag = sentence.get(pos).tag();
      boolean skip=false;
      for(String t : TAG_SKIPS) {
        if(curTag.startsWith(t)) {
          skip = true;
          break;
        }
      }
      if(!skip)
        break;
    }
    if(pos < sentence.size()) {
      String curTag = sentence.get(pos).tag();
      System.err.println("First relevant tag:"+curTag);
      for(String t : TAG_VERBS) {
        if(curTag.startsWith(t)) {
          isVerb = true;
          System.err.println("Verb: yes.");
          return;
        }
      }
    }
    isVerb = false;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(
			Featurizable<IString, String> f) { return null; }

	public void reset() { }

}
