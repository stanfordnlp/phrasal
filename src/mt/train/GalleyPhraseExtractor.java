package mt.train;

import edu.stanford.nlp.util.IString;

import java.util.*;

import mt.base.Sequence;


/**
 * @author Michel Galley
 */
public class GalleyPhraseExtractor extends AbstractPhraseExtractor {

  Set<IString> filter = new HashSet<IString>();

  public GalleyPhraseExtractor(Properties prop, AlignmentTemplates alTemps,
                               List<AbstractFeatureExtractor> extractors, Sequence<IString>[] filter) {
    super(prop, alTemps, extractors);
    System.err.println("Using galley phrase extractor instead of Moses's.");
    for(Sequence<IString> seq : filter)
      for(IString el : seq)
        this.filter.add(el);
  }

  public void extractPhrases(WordAlignment sent) {

    int fsize = sent.f().size();
    int esize = sent.e().size();

    if(fsize > MAX_SENT_LEN || esize > MAX_SENT_LEN) {
      System.err.println("Warning: skipping too long sentence. Length: f="+fsize+" e="+esize);
      return;
    }

    if(needAlGrid) {
      alGrid.init(esize,fsize);
      if(fsize < PRINT_GRID_MAX_LEN && esize < PRINT_GRID_MAX_LEN)
        alGrid.printAlTempInGrid("line: "+sent.getId(),sent,null,System.err);
    }

    // Sentence boundaries:
    if(extractBoundaryPhrases) {
      // make sure we can always translate <s> as <s> and </s> as </s>:
      extractPhrase(sent,0,0,0,0);
      extractPhrase(sent,fsize-1,fsize-1,esize-1,esize-1);
    }

    // For each English phrase:
    for(int fi=0; fi<fsize; ++fi)
      if(filter.contains(sent.f().get(fi)))
        for(int ei : sent.f2e(fi))
          for(int fi1=Math.max(0,fi-maxPhraseLenF); fi1<=fi; ++fi1)
            if(filter.contains(sent.f().get(fi1)))
              for(int fi2=fi; fi2<=Math.min(fsize-1,fi+maxPhraseLenF); ++fi2)
                if(filter.contains(sent.f().get(fi2)))
                  for(int ei1=Math.max(0,ei-maxPhraseLenE); ei1<=ei; ++ei1)
                    for(int ei2=ei; ei2<=Math.min(esize-1,ei+maxPhraseLenE); ++ei2) {
                      extractPhrase(sent,fi1,fi2,ei1,ei2);
                    }
    
    if(needAlGrid)
      extractPhrasesFromAlGrid(sent);
  }
}
