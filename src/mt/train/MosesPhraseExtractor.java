package mt.train;

import java.util.*;


/**
 * Phrase extraction as implemented in Moses, which is not particularly efficient.
 *
 * @author Michel Galley
 */
public class MosesPhraseExtractor extends AbstractPhraseExtractor {

  public MosesPhraseExtractor(AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(alTemps,extractors);
  }

  //public void extractPhrases(SymmetricalWordAlignment sent) {
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

    // For each English phrase:
    for(int e1=0; e1<esize; ++e1) {
      for(int e2=e1; e2<esize && e2-e1<maxPhraseLenE; ++e2) {
        // Find range of f aligning to e1...e2:
        int f1=Integer.MAX_VALUE;
        int f2=Integer.MIN_VALUE;
        for(int ei=e1; ei<=e2; ++ei) {
          for(int fi : sent.e2f(ei)) {
            if(fi<f1) f1 = fi;
            if(fi>f2) f2 = fi;
          }
        }
        // Phrase too long:
        if(f2-f1>=maxPhraseLenF)
          continue; 
        // No word alignment within that range, or phrase too long?
        if(NO_EMPTY_ALIGNMENT && f1>f2)
          continue;
        // Check if range [e1-e2] [f1-f2] is admissible:
        boolean admissible = true;
        for(int fi=f1; fi<=f2 && admissible; ++fi) {
          for(int ei : sent.f2e(fi)) {
            if(ei<e1 || ei>e2) {
              admissible = false;
              break;
            }
          }
        }
        if(!admissible)
          continue;
        // See how much we can expand the phrase to cover unaligned words:
        int F1 = f1, F2 = f2;
        while(F1-1>=0    && f2-F1<maxPhraseLenF-1 && sent.f2e(F1-1).size()==0) { --F1; }
        while(F2+1<fsize && F2-f1<maxPhraseLenF-1 && sent.f2e(F2+1).size()==0) { ++F2; }

        for(int i=F1; i<=f1; ++i)
          for(int j=f2; j<=F2; ++j)
            if(j-i < maxPhraseLenF)
              extractPhrase(sent,i,j,e1,e2);
      }
    }
    if(needAlGrid)
      extractPhrasesFromAlGrid(sent);
  }
}
