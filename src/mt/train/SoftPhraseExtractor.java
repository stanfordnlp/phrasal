package mt.train;

import edu.stanford.nlp.util.IString;

import java.util.*;

import mt.base.Sequence;


/**
 * @author Michel Galley
 */
public class SoftPhraseExtractor extends AbstractPhraseExtractor {

  private final int maxCrosses;

  //private final boolean exact = true;

  BitSet filter = new BitSet(IString.index.size());

  public SoftPhraseExtractor
   (Properties prop, int maxConsistencyViolations, AlignmentTemplates alTemps,
    List<AbstractFeatureExtractor> extractors, Sequence<IString>[] filter) {
    super(prop, alTemps, extractors);
    this.maxCrosses = maxConsistencyViolations;
    System.err.println("Using soft phrase extractor (v2) instead of Moses's.");
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

    for(int fi=0; fi<fsize; ++fi) {
      for(int ei : sent.f2e(fi)) {
        for(int ei1=ei; ei1>=Math.max(0,ei-maxPhraseLenE+1); --ei1) {
          for(int ei2=ei; ei2<=Math.min(esize-1,ei1+maxPhraseLenE-1); ++ei2) {
            int crosses = 0;
            for(int e : sent.f2e(fi)) {
              if(e < ei1 || e > ei2) {
                if(++crosses >= maxCrosses)
                  break;
              }
            }
            if(crosses <= maxCrosses) {
              int crosses2 = crosses;
              for(int fi1=fi; fi1>=Math.max(0,fi-maxPhraseLenF+1); --fi1) {
                for(int e : sent.f2e(fi1)) {
                  if(e < ei1 || e > ei2) {
                    if(++crosses2 >= maxCrosses)
                      break;
                  }
                }
                if(crosses2 <= maxCrosses) {
                  int crosses3 = crosses2;
                  for(int fi2=fi; fi2<=Math.min(fsize-1,fi1+maxPhraseLenF); ++fi2) {
                    for(int e : sent.f2e(fi2)) {
                      if(e < ei1 || e > ei2) {
                        if(++crosses3 >= maxCrosses)
                          break;
                      }
                    }
                    if(crosses3 <= maxCrosses) {
                      int crosses4 = crosses3;
                      for(int e=ei1; e<=ei2; ++e) {
                        for(int f : sent.e2f(e)) {
                          if(f < fi1 || f > fi2) {
                            ++crosses4;
                          }
                        }
                      }
                      if(crosses4 <= maxCrosses) {
                        //if(consistencyViolations == 0) {
                        //  if(!checkAlignmentConsistency(sent, fi1, fi2, ei1, ei2))
                        //    System.err.printf("consistency violations %d, but should be 0.\n", crosses4);
                        //}
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
