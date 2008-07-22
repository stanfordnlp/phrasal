package mt.hmmalign;

/**
 * This serves to handle the alignment probabilities
 * the basic functionality is getProb(i,j,alignment) and incCount(i,j,alignment,val);
 * Handles conditioning on many different tag configurations - french tags and english tags
 * The conditioning tags are specified with a number which contains flags for different tags
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class AlHandlerHMM2Tags extends AlHandlerHMM2 {
  ATableHMMHolder aHolder;
  ATable tables[]; //in some cases we will use just one dimension , when conditioning on just one tag
  ATable tables_b[][]; // when using both french and english tags, will use the 2 dim array
  int mask;
  int numTags = 0;
  boolean useF;
  boolean useE;
  WordEx fEOS;
  WordEx eEOS;
  int ID_START = 0;
  double lambdasm = .3;
  //in the mask the bits specify the presence/absence of tags
  // the meanings are     | 32-ETaj-1-1| 16-ET(aj-1)| 8-ET(aj-1)+1| 4-FTj-1| 2-FTj| 1 - FTj+1|


  public AlHandlerHMM2Tags(ATableHMMHolder a, int mask, boolean eqclasses) {
    this.aHolder = a;
    this.mask = mask;
    //count the number of tags;
    if (eqclasses) {
      ID_START = ATableHMM2EQ.MAX_FLDS;
    }
    for (int i = 1; i <= 32; i *= 2) {
      if ((mask & i) > 0) {
        numTags++;
      }
    }
    System.out.println("Number of tags is " + numTags);
    fEOS = SentenceHandler.sTableF.getEos();
    eEOS = SentenceHandler.sTableE.getEos();
    if ((mask & (1 | 2 | 4)) > 0) {
      useF = true;
    }
    if ((mask & (8 | 16 | 32)) > 0) {
      useE = true;
    }
  }


  @Override
	public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
  }


  @Override
	public void init() {
    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;

    if (useF && useE) {
      tables_b = new ATable[2 * l + 1][m + 2];
    } else {
      if (useF) {
        tables = new ATable[m + 2];
      } else {
        tables = new ATable[2 * l + 1];
      }

    }

    IntTuple iT = IntTuple.getIntTuple(numTags);
    int current = 0;
    int i_real = 0;


    if (useE) {

      for (int i = 0; i <= 2 * l; i++) {

        current = 0;
        if ((mask & 32) > 0) {
          i_real = i;
          if (i > l) {
            i_real -= l;
          }

          if (i_real <= l) {
            if (i_real - 1 <= 0) {
              iT.set(current, eEOS.getTagId());
            } else {
              iT.set(current, sentPair.e.getWord(i_real - 1).getTagId());
            }
          }//if i<=l
          else {

            iT.set(current, sentPair.e.getWord(i_real - 1).getTagId());
          }
          current++;
        }//mask&32>0

        if ((mask & 16) > 0) {
          i_real = i;
          if (i > l) {
            i_real -= l;
          }

          if (i_real <= l) {
            if (i_real <= 0) {
              iT.set(current, eEOS.getTagId());
            } else {
              iT.set(current, sentPair.e.getWord(i_real).getTagId());
            }
          }//if i<=l
          else {
            iT.set(current, sentPair.e.getWord(i_real).getTagId());//null
          }
          current++;
        }//mask&16>0


        if ((mask & 8) > 0) { //eai+1
          int ireal = i;
          if (i > l) {
            ireal -= l;
          }
          if (ireal == l) {
            iT.set(current, eEOS.getTagId());
          } else {
            iT.set(current, sentPair.e.getWord(ireal + 1).getTagId());
          }

          current++;
        }//mask&8>0


        if (useF) {

          int current_start = current;
          for (int j = 1; j <= m + 1; j++) {
            //add the tags for the js
            current = current_start;
            if ((mask & 4) > 0) { //tfj-1
              if (j == 1) {
                iT.set(current, fEOS.getTagId());
              } else {

                iT.set(current, sentPair.f.getWord(j - 1).getTagId());
              }
              current++;
            }//mask 4

            if ((mask & 2) > 0) { //tfj
              if (j == m + 1) {
                iT.set(current, fEOS.getTagId());
              } else {

                iT.set(current, sentPair.f.getWord(j).getTagId());
              }
              current++;
            }//mask 2

            if ((mask & 1) > 0) { //tfj+1
              if (j >= m) {
                iT.set(current, fEOS.getTagId());
              } else {

                iT.set(current, sentPair.f.getWord(j + 1).getTagId());
              }
              current++;
            }//mask 1

            //the tuple is ready
            tables_b[i][j] = aHolder.get(iT);


          } //for j

        }//if useF
        else {
          tables[i] = aHolder.get(iT);

        }


      } //for i

    }//useE
    else {
      for (int j = 1; j <= m + 1; j++) {
        //add the tags for the js
        current = 0;
        if ((mask & 4) > 0) { //tfj-1
          if (j == 1) {
            iT.set(current, fEOS.getTagId());
          } else {

            iT.set(current, sentPair.f.getWord(j - 1).getTagId());
          }
          current++;
        }//mask 4

        if ((mask & 2) > 0) { //tfj
          if (j == m + 1) {
            iT.set(current, fEOS.getTagId());
          } else {

            iT.set(current, sentPair.f.getWord(j).getTagId());
          }
          current++;
        }//mask 2

        if ((mask & 1) > 0) { //tfj+1
          if (j >= m) {
            iT.set(current, fEOS.getTagId());
          } else {

            iT.set(current, sentPair.f.getWord(j + 1).getTagId());
          }
          current++;
        }//mask 1

        //the tuple is ready
        tables[j] = aHolder.get(iT);


      } //for j

    }//else


  }


  /*
   * get the probability p choose i for j
   */
  @Override
	public double getProb(int i, int j, int[] alignment) {

    ATable a;
    double prob, prob1;

    if (useE && useF) {
      a = tables_b[alignment[j - 1]][j];
    } else {
      if (useF) {
        a = tables[j];
      } else {
        a = tables[alignment[j - 1]];
      }
    }

    //System.out.println(" for j "+j+" "+i+" i_prev "+alignment[j-1]+" using table "+a.name+" jmp "+(j>1?alignment[j-2]:ID_START)+" sent length "+l);


    if (j == 1) {
      prob = a.getProb(i, 0, ID_START, l);
    } else {
      prob = a.getProb(i, alignment[j - 1], alignment[j - 2], l);
    }

    if (this.aHolder.smooth) {
      int jmp = ID_START, i_p = 0;

      if (j > 1) {
        jmp = alignment[j - 2];
        i_p = alignment[j - 1];
      }
      prob1 = aHolder.getSmoothTable().getProb(i, i_p, jmp, l);
      prob = (1 - lambdasm) * prob + lambdasm * prob1;


    }

    //System.out.println(" returning prob "+prob);
    return prob;
  }


  /**
   * Increment the count for c(choose|ei) by val and also increment the probability for not choose
   * by 1-val
   */

  @Override
	public void incCount(int i, int j, int[] alignment, double val) {
    ATable a;

    if (val == 0) {
      return;
    }
    if (useE && useF) {
      a = tables_b[alignment[j - 1]][j];
    } else {
      if (useF) {
        a = tables[j];
      } else {
        a = tables[alignment[j - 1]];
      }
    }

    int i_p, i_pp;

    if (j == 1) {
      i_p = 0;
      i_pp = ID_START;
    } else {
      i_p = alignment[j - 1];
      i_pp = alignment[j - 2];
    }
    //System.out.println("Incrementing count for "+i+" "+i_p+" "+i_pp+" with "+val+" length is "+l+" j is "+j);
    a.incCount(i, i_p, i_pp, l, val);
    if (aHolder.smooth) {
      //System.out.println("Incrementing count for smooth table with "+val);
      aHolder.getSmoothTable().incCount(i, i_p, i_pp, l, val);

    }
    //nothing
  }


}
