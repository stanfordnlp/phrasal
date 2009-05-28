package mt.train;

import edu.stanford.nlp.util.IString;

import java.util.*;

import mt.base.Sequence;


/**
 * @author Michel Galley
 */
public class SoftPhraseExtractor extends AbstractPhraseExtractor {

  private final int maxConsistencyViolations;

  BitSet filter = new BitSet(IString.index.size());
  //Set<IString> filter = new HashSet<IString>();

  public SoftPhraseExtractor
   (Properties prop, int maxConsistencyViolations, AlignmentTemplates alTemps,
    List<AbstractFeatureExtractor> extractors, Sequence<IString>[] filter) {
    super(prop, alTemps, extractors);
    this.maxConsistencyViolations = maxConsistencyViolations;
    System.err.println("Using soft phrase extractor instead of Moses's.");
    System.err.println("Maximum number of consistency violations: "+maxConsistencyViolations);
    for(Sequence<IString> seq : filter)
      for(IString el : seq)
        this.filter.set(el.id);
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
    for(int fi=0; fi<fsize; ++fi) {
      if(filter.get(sent.f().get(fi).id)) {
        for(int ei : sent.f2e(fi)) {
          for(int fi1=Math.max(0,fi-maxPhraseLenF); fi1<=fi; ++fi1) {
            boolean goodPhrasePrefix=true;
            for(int f=fi1; f<fi; ++f) {
              if(!filter.get(sent.f().get(f).id)) {
                goodPhrasePrefix=false;
                break;
              }
            }
            if(goodPhrasePrefix) {
              for(int fi2=fi; fi2<=Math.min(fsize-1,fi+maxPhraseLenF); ++fi2) {
                if(fi2-fi1+1 > maxPhraseLenF)
                  break;
                boolean goodPhraseSuffix=true;
                for(int f=fi+1; f<=fi2; ++f) {
                  if(!filter.get(sent.f().get(f).id)) {
                    goodPhraseSuffix=false;
                    break;
                  }
                }
                if(goodPhraseSuffix) {
                  for(int ei1=Math.max(0,ei-maxPhraseLenE); ei1<=ei; ++ei1) {
                    for(int ei2=ei; ei2<=Math.min(esize-1,ei+maxPhraseLenE); ++ei2) {
                      if(ei2-ei1+1 > maxPhraseLenE)
                        break;
                      int consistencyViolations = 0;
                      for(int f=fi1; f<=fi2; ++f) {
                        for(int e : sent.f2e(f)) {
                          if(e < ei1 || e > ei2) {
                            ++consistencyViolations;
                          }
                        }
                      }
                      for(int e=ei1; e<=ei2; ++e) {
                        for(int f : sent.e2f(e)) {
                          if(f < fi1 || f > fi2) {
                            ++consistencyViolations;
                          }
                        }
                      }
                      if(consistencyViolations <= maxConsistencyViolations) {
                        if(consistencyViolations == 0) {
                          if(!checkAlignmentConsistency(sent, fi1, fi2, ei1, ei2))
                            System.err.printf("consistency violations %d, but should be 0.\n", consistencyViolations);
                        }
                        extractPhrase(sent,fi1,fi2,ei1,ei2);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    
    if(needAlGrid)
      extractPhrasesFromAlGrid(sent);
  }
}
