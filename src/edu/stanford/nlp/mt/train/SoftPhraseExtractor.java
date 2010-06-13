package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.util.Pair;

import java.util.*;


/**
 * Extracts phrase pairs with up to a given number of crossings.
 *
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class SoftPhraseExtractor extends AbstractPhraseExtractor {

  private int maxCrossings;
  private final Set<Pair<Integer,Integer>> cache = new HashSet<Pair<Integer,Integer>>();

  private static final boolean DISABLE_GROW = System.getProperty("disableGrow") != null;
  private static final boolean DISABLE_SHRINK = System.getProperty("disableShrink") != null;

  private static final int MAX_SENT_LEN = AlignmentGrid.MAX_SENT_LEN;

  // Count of number of alignments inside current range [f1,f2]:
  // (see loops inside extractPhrases to see what range [f1,f2] represents)
  private static final int[] in = new int[MAX_SENT_LEN];

  public SoftPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
    System.err.println("Using experimental phrase extractor. Max crossings: "+maxCrossings);
  }

  public void setMaxCrossings(int maxCrossings) {
    this.maxCrossings = maxCrossings;
  }

  private float expDecay(float w) {
    return (float)Math.exp(-w);
  }

  @Override
  public void extractPhrases(WordAlignment sent) {

    int fsize = sent.f().size();
    int esize = sent.e().size();

    if(fsize > MAX_SENT_LEN || esize > MAX_SENT_LEN) {
      System.err.println("Warning: skipping too long sentence. Length: f="+fsize+" e="+esize);
      return;
    }

    alGrid.init(sent);
    if(fsize < PRINT_GRID_MAX_LEN && esize < PRINT_GRID_MAX_LEN)
      alGrid.printAlTempInGrid("line: "+sent.getId(),null,System.err);

    // For each Foreign phrase [f1,f2]:
    for(int f1=0; f1<fsize; ++f1) {

      int e1=Integer.MAX_VALUE;
			int e2=Integer.MIN_VALUE;
      int lastf = Math.min(fsize,f1+maxPhraseLenF)-1;

      for(int f2=f1; f2<=lastf; ++f2) {

        cache.clear();

        // Find range of f aligning to e1...e2:
        SortedSet<Integer> ess = sent.f2e(f2);
        if(!ess.isEmpty()) {
          int emin = ess.first();
          int emax = ess.last();
          if(emin<e1) e1 = emin;
          if(emax>e2) e2 = emax;
        }

        // No word alignment within that range:
        if(e1>e2)
          continue;
        
        // Count alignments outside [e1,e2] and inside [f1,f2]:
        int crossingsInside = 0;
        for(int ei=e1; ei<=e2; ++ei) {
          in[ei] = sent.e2fSize(ei,f1,f2);
          crossingsInside += sent.e2f(ei).size() - in[ei];
        }

        //System.err.printf("INIT: %d-%d %d-%d\n",f1,f2,e1,e2);

        // Grow block downwards:
        if(!DISABLE_GROW) {
          int crossingsInsideBelowD = crossingsInside;
          int lastE1 = Math.max(0,e2-maxPhraseLenE+1);

          for(int i=e1; i>=lastE1; --i) {

            if(i<e1)
              crossingsInsideBelowD += sent.e2f(i).size();
            if(maxCrossings < crossingsInsideBelowD)
              continue;

            // Grow block upwards:
            {
              int crossingsU = crossingsInsideBelowD;
              int lastE2 = Math.min(esize-1,i+maxPhraseLenE-1);

              for(int j=e2; j<=lastE2; ++j) {
                assert(j-i < maxPhraseLenE);
                if(j>e2)
                  crossingsU += sent.e2f(j).size();
                if(maxCrossings >= crossingsU && newPair(i,j)) {
                  //System.err.printf("G-G %d %d %d\n",crossingsInside,crossingsInsideBelowD,crossingsU);
                  addPhraseToIndex(sent,f1,f2,i,j,crossingsU==0,expDecay(crossingsU));
                }
              }
            }

            // Shrink block downwards:
            if(maxCrossings > 0) {
              int crossingsD = crossingsInsideBelowD;
              int mini = Math.max(i,e1);
              for(int j=e2; j>=mini; --j) {
                if(j<e2)
                  crossingsD += 2*in[j] - sent.e2f(j).size();
                if(maxCrossings >= crossingsD && newPair(i,j)) {
                  //System.err.printf("G-S %d %d %d\n",crossingsInside,crossingsInsideBelowD,crossingsD);
                  addPhraseToIndex(sent,f1,f2,i,j,false,expDecay(crossingsD));
                }
              }
            }
          }
        }

        // Shrink block upwards:
        if(!DISABLE_SHRINK && maxCrossings > 0) {
          int crossingsInsideBelowU = crossingsInside;
          for(int i=e1; i<=e2; ++i) {

            if(i>e1) {
              crossingsInsideBelowU += 2*in[i-1] - sent.e2f(i-1).size();
              //System.err.printf("S-G update at %d: %d\n",i-1,crossingsInsideBelowU);
            }
            if(maxCrossings < crossingsInsideBelowU)
              continue;

            // Grow block upwards:
            if(!DISABLE_GROW) {
              int crossingsU = crossingsInsideBelowU;
              int lastE2 = Math.min(esize-1,i+maxPhraseLenE-1);

              for(int j=e2; j<=lastE2; ++j) {
                assert(j-i < maxPhraseLenE);
                if(j>e2)
                  crossingsU += sent.e2f(j).size();
                if(maxCrossings >= crossingsU && newPair(i,j)) {
                  //System.err.printf("S-G %d %d %d\n",crossingsInside,crossingsInsideBelowU,crossingsU);
                  addPhraseToIndex(sent,f1,f2,i,j,false,expDecay(crossingsU));
                }
              }
            }

            // Shrink block downwards:
            if(!DISABLE_SHRINK) {
              int crossingsD = crossingsInsideBelowU;
              int mini= Math.max(i,e1);
              for(int j=e2; j>=mini; --j) {
                if(j<e2) {
                  crossingsD += 2*in[j+1] - sent.e2f(j+1).size();
                  //System.err.printf("S-S update at %d: %d\n",j+1,crossingsD);
                }
                if(maxCrossings >= crossingsD && newPair(i,j)) {
                  //System.err.printf("S-S %d %d %d\n",crossingsInside,crossingsInsideBelowU,crossingsD);
                  addPhraseToIndex(sent,f1,f2,i,j,false,expDecay(crossingsD));
                }
              }
            }
          }
        }
      }
      
    }
    extractPhrasesFromGrid(sent);
  }

  private boolean newPair(int i, int j) {
    Pair<Integer,Integer> p = new Pair<Integer,Integer>(i,j);
    return cache.add(p); 
  }
}
