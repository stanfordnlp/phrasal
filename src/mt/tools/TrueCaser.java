package mt.tools;

import mt.decoder.feat.*;
import mt.decoder.inferer.impl.*;
import mt.decoder.inferer.*;
import mt.decoder.util.*;
import mt.base.*;
import mt.decoder.recomb.*;
import mt.decoder.h.*;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

import java.util.*;
import java.io.*;

/**
 * Language Model based TrueCasing
 * 
 * This class implements n-gram language model based truecasing, 
 * an approach similar to that seen Lita et al 2003's paper tRuEcasIng.
 *
 * @author danielcer
 *
 */

public class TrueCaser {
  static final int BEAM_SIZE = 50;

  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage:\n\tjava ...TrueCaser (language model) < uncased_input > cased_output");
      System.exit(-1);
    }

    String lmFilename = args[0];
 
    MultiBeamDecoder.MultiBeamDecoderBuilder infererBuilder = (MultiBeamDecoder.MultiBeamDecoderBuilder) InfererBuilderFactory.factory(InfererBuilderFactory.MULTIBEAM_DECODER);
    
     // Read in LM & create LM featurizer
    NGramLanguageModelFeaturizer<IString> lmFeaturizer = NGramLanguageModelFeaturizer.fromFile(lmFilename, NGramLanguageModelFeaturizer.FEATURE_NAME);
    List<IncrementalFeaturizer<IString,String>> listFeaturizers = new LinkedList<IncrementalFeaturizer<IString,String>>();
    listFeaturizers.add(lmFeaturizer);
    CombinedFeaturizer<IString,String> combinedFeaturizer = new CombinedFeaturizer(listFeaturizers);

    infererBuilder.setIncrementalFeaturizer(combinedFeaturizer);
    Scorer<String> scorer = new UniformScorer<String>(false);
    infererBuilder.setScorer(scorer);

    // Create truecasing phrase generator
    infererBuilder.setPhraseGenerator(new AllCasePhraseGenerator(combinedFeaturizer, scorer));
    infererBuilder.setSearchHeuristic(new IsolatedPhraseForeignCoverageHeuristic<IString,String>(combinedFeaturizer, scorer));
    List<LanguageModel<IString>> lgModels = new LinkedList<LanguageModel<IString>>();
    lgModels.add(lmFeaturizer.lm);

    // misc. decoder configuration
    RecombinationFilter<Hypothesis<IString, String>> recombinationFilter = new TranslationNgramRecombinationFilter<IString, String>(lgModels, Integer.MAX_VALUE);
    infererBuilder.setRecombinationFilter(recombinationFilter);
    infererBuilder.setMaxDistortion(0);
    infererBuilder.setBeamCapacity(BEAM_SIZE);
    infererBuilder.setBeamType(HypothesisBeamFactory.BeamType.sloppybeam);

    // builder decoder 
    Inferer<IString, String> inferer = infererBuilder.build(); 

    // enter main truecasing loop
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in, "UTF-8"));
    for (String line; (line = reader.readLine()) != null; ) {
        String[] tokens = line.split("\\s+"); 
        int lineNumber = reader.getLineNumber();
        Sequence<IString> source = new SimpleSequence<IString>(true, IStrings
      .toIStringArray(tokens));
        RichTranslation<IString, String> translation = inferer.translate(source, lineNumber - 1, null, null);

        // manual fix up(s)

        // capitalize the first letter

        String trg = translation.translation.toString();
        String firstLetter = trg.substring(0,1);
        String rest = trg.substring(1,trg.length());
        String capTrg = firstLetter.toUpperCase() + rest;
        
        trg = capTrg;

        System.out.printf("%s \n", trg);
    }
    System.exit(-1);
  }
}

class AllCasePhraseGenerator extends AbstractPhraseGenerator<IString,String> {

  static final String NAME = "AllCasePhrGen";
  Map<String,List<String>> caseMap = new HashMap<String,List<String>>();

  public AllCasePhraseGenerator(IsolatedPhraseFeaturizer<IString, String> phraseFeaturizer, Scorer<String> scorer) {
    super(phraseFeaturizer, scorer);

    // TODO : caseMap should actually examine the language model(s) directly
    // rather than using a dump from IStrings.keySet()
    Set<String> tokens = IString.keySet();

    // construct uncased to cased map
    for (String token : tokens) {
      if (!caseMap.containsKey(token.toLowerCase())) {
        caseMap.put(token.toLowerCase(), new LinkedList<String>());
      }
      // add token as is  
      caseMap.get(token.toLowerCase()).add(token);

      // add all lower case version of token
      caseMap.get(token.toLowerCase()).add(token.toLowerCase());

      // add first letter capitalized version of token
      String firstLetter = token.substring(0,1);
      String rest = token.substring(1,token.length());
      String capToken = firstLetter.toUpperCase() + rest;
      caseMap.get(token.toLowerCase()).add(capToken);
    }  
  }

  public String getName() {
    return NAME;
  }

  public List<TranslationOption<IString>> getTranslationOptions(Sequence<IString> sequence) {
    if (sequence.size() != 1) { 
      throw new RuntimeException("Subsequence length != 1");
    }
    List<TranslationOption<IString>> list = new LinkedList<TranslationOption<IString>>();
    String token = sequence.get(0).toString().toLowerCase();
    List<String> casings = caseMap.get(token);
    if (casings == null) {
      casings = new LinkedList<String>();
      casings.add(sequence.get(0).toString());
    }
    RawSequence<IString> rawSource = new RawSequence(sequence);
  
    for (String casing : casings) {
      IString[] trgArr = IStrings.toIStringArray(new String[]{casing});
      RawSequence<IString> trg = new RawSequence<IString>(trgArr);
      list.add(new TranslationOption<IString>(new float[0], new String[0], trg, rawSource, new PhraseAlignment("I-I")));
    }
    return list;
  }
   
  public int longestForeignPhrase() {
    return 1;
  }

  public void setCurrentSequence(Sequence<IString> foreign,
      List<Sequence<IString>> tranList) {
    // no op
  }
}
