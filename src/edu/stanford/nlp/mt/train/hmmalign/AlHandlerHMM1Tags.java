package edu.stanford.nlp.mt.train.hmmalign;

/**
 * This serves to handle the alignment probabilities.
 * The basic functionality is getProb(i,j,alignment) and incCount(i,j,alignment,val).
 * Handles conditioning on many different tag configurations - french tags and english tags.
 * The conditioning tags are specified with a number which contains flags for different tags.
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class AlHandlerHMM1Tags extends AlHandlerHMM1 {

  private ATableHMMHolder aHolder;
  private ATableHMM[] tables; //in some cases we will use just one dimension , when conditioning on just one tag
  private ATableHMM[][] tables_b; // when using both french and english tags, will use the 2 dim array
  /** In the mask the bits specify the presence/absence of tags.
   *  The meanings are     | 32-ETaj-1-1| 16-ET(aj-1)| 8-ET(aj-1)+1| 4-FTj-1| 2-FTj| 1 - FTj+1|
   */
  private int mask;
  private int numTags = 0;
  private boolean useF;
  private boolean useE;
  private WordEx fEOS;
  private WordEx eEOS;
  private static final double lambdasm = .1;
  // private double addBeta = 400;

  private final boolean DEBUG = false;

  public AlHandlerHMM1Tags(ATableHMMHolder a, int mask) {
    this.aHolder = a;
    this.mask = mask;
    //count the number of tags;
    for (int i = 1; i <= 32; i *= 2) {
      if ((mask & i) > 0) {
        numTags++;
      }
    }
    //System.out.println("Number of tags is "+numTags);
    fEOS = SentenceHandler.sTableF.getEos();
    eEOS = SentenceHandler.sTableE.getEos();
    //addBeta=SentenceHandler.get
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
      tables_b = new ATableHMM[2 * l + 1][m + 2];
    } else {
      if (useF) {
        tables = new ATableHMM[m + 2];
      } else {
        tables = new ATableHMM[2 * l + 1];
      }

    }

    IntTuple iT = IntTuple.getIntTuple(numTags);
    int current = 0;


    if (useE) {

      int i_real = 0;
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
            tables_b[i][j] = (ATableHMM) aHolder.get(iT);


          } //for j

        }//if useF
        else {
          tables[i] = (ATableHMM) aHolder.get(iT);

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
        tables[j] = (ATableHMM) aHolder.get(iT);


      } //for j

    }//else


  }


  public void addEntries(SentencePair sentPair, TupleCounter TC) {

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;

    IntTuple iT = IntTuple.getIntTuple(numTags);
    int current = 0;


    if (useE) {

      for (int i = 0; i <= 2 * l; i++) {

        current = 0;
        if ((mask & 32) > 0) {
          int i_real = i;
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
            iT.set(current, sentPair.e.getWord(i - l).getTagId());
          }
          current++;
        }//mask&32>0

        if ((mask & 16) > 0) {
          int i_real = i;
          if (i_real > l) {
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
            iT.set(current, sentPair.e.getWord(0).getTagId());//null
            System.out.println(" just added a null i is " + i);
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
            current = current_start;
            //add the tags for the js
            if ((mask & 4) > 0) { //tfj-1
              //System.out.println(" mask contains 4");
              if (j == 1) {
                iT.set(current, fEOS.getTagId());
              } else {

                iT.set(current, sentPair.f.getWord(j - 1).getTagId());
                //System.out.println(j+" setting current "+sentPair.f.getWord(j-1).getTagId());
              }
              current++;
            }//mask 4

            if ((mask & 2) > 0) { //tfj
              //System.out.println(" mask contains 2");
              if (j == m + 1) {
                iT.set(current, fEOS.getTagId());
              } else {

                iT.set(current, sentPair.f.getWord(j).getTagId());
              }
              current++;
            }//mask 2

            if ((mask & 1) > 0) { //tfj+1
              //System.out.println(" mask contains 1");
              if (j >= m) {
                iT.set(current, fEOS.getTagId());
              } else {

                iT.set(current, sentPair.f.getWord(j + 1).getTagId());
              }
              current++;
            }//mask 1

            //the tuple is ready
            if (wantadd(iT)) {
              TC.add(iT, sentPair.getCount());
            }


          } //for j

        }//if useF
        else {

          if (wantadd(iT)) {
            TC.add(iT, sentPair.getCount());
          }

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
        if (wantadd(iT)) {
          TC.add(iT, sentPair.getCount());
        }


      } //for j

    }//else


  }


  public void addEntries(SentencePair sentPair, TupleCounter TC, int[] viterbi_alignment) {

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;

    IntTuple iT = IntTuple.getIntTuple(numTags);
    int current = 0;


    if (DEBUG) {
      //print out stuff about the sentence & alignment
      //System.out.println(" Printing alignment");
      System.out.println(" French sentence length " + m + " english " + l);
      for (int j = 1; j <= m; j++) {
        System.out.print(" " + j + " " + viterbi_alignment[j]);
      }
      System.out.println();
      for (int i1 = 1; i1 <= l; i1++) {
        System.out.print(SentenceHandler.sTableE.getName(sentPair.e.getWord(i1).getTagId()) + " ");
      }
      System.out.println();

    }


    for (int jM = 1; jM <= m + 1; jM++) {

      //if(jM==m+1){i=2*l+1;}else{
      int i = viterbi_alignment[jM - 1];

      if (useE) {

        current = 0;
        if ((mask & 32) > 0) {
          int i_real = i;
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
          int i_real = i;
          if (i_real > l) {
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
            iT.set(current, eEOS.getTagId());//null
            //System.out.println(" just added a null i is "+i);
          }
          current++;
        }//mask&16>0


        if ((mask & 8) > 0) { //eai+1
          int ireal = i;
          if (i > l) {
            ireal -= l;
          }
          if (ireal >= l) {
            iT.set(current, eEOS.getTagId());
          } else {
            iT.set(current, sentPair.e.getWord(ireal + 1).getTagId());
          }

          current++;
        }//mask&8>0


        if (useF) {
          int current_start = current;
          int j = jM;
          current = current_start;
          //add the tags for the js
          if ((mask & 4) > 0) { //tfj-1
            //System.out.println(" mask contains 4");
            if (j == 1) {
              iT.set(current, fEOS.getTagId());
            } else {

              iT.set(current, sentPair.f.getWord(j - 1).getTagId());
              //System.out.println(j+" setting current "+sentPair.f.getWord(j-1).getTagId());
            }
            current++;
          }//mask 4

          if ((mask & 2) > 0) { //tfj
            //System.out.println(" mask contains 2");
            if (j == m + 1) {
              iT.set(current, fEOS.getTagId());
            } else {

              iT.set(current, sentPair.f.getWord(j).getTagId());
            }
            current++;
          }//mask 2

          if ((mask & 1) > 0) { //tfj+1
            //System.out.println(" mask contains 1");
            if (j >= m) {
              iT.set(current, fEOS.getTagId());
            } else {

              iT.set(current, sentPair.f.getWord(j + 1).getTagId());
            }
            current++;
          }//mask 1

          //the tuple is ready
          if (wantadd(iT)) {
            TC.add(iT, sentPair.getCount());
          }


        }//if useF
        else {

          if (wantadd(iT)) {
            TC.add(iT, sentPair.getCount());
          }

        }

      }//useE
      else {
        int j = jM;
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
        if (wantadd(iT)) {
          TC.add(iT, sentPair.getCount());
        }


      }//else


    }


  }


  public boolean wantadd(IntTuple iT) {
    return true;
    /*
     for(int i=0;i<iT.numElements;i++){

      tag=iT.get(i);
      nameTag=SentenceHandler.sTableE.getName(tag);
      if(nameTag.indexOf("NN") >-1){containsNN=true;}
      if(nameTag.indexOf("JJ")>-1){containsJJ=true;}

     }

     if(containsNN&&containsJJ){return true;}
     else{return false;}
     */
  }


  /*
   * get the probability p choose i for j
   */
  @Override
  public double getProb(int i, int j, int[] alignment) {

    ATableHMM a;
    double prob1;

    if (useE && useF) {
      a = tables_b[alignment[j - 1]][j];
    } else {
      if (useF) {
        a = tables[j];
      } else {
        a = tables[alignment[j - 1]];
      }
    }

    //System.out.println(" for j "+j+" i "+i+" using table "+a.name);

    prob1 = a.getProb(i, alignment[j - 1], l);

    if (this.aHolder.smooth) {
      double prob2 = aHolder.getSmoothTable().getProb(i, alignment[j - 1], l);
      //lambdasm=addBeta/(double)(a.getCount()+addBeta);

      prob1 = (1 - lambdasm) * prob1 + lambdasm * prob2;
    }
    return prob1;
  }


  /**
   * Increment the count for c(choose|ei) by val and also increment the probability for not choose
   * by 1-val
   */

  @Override
  public void incCount(int i, int j, int[] alignment, double val) {
    ATableHMM a;

    if (val == 0) {
      return;
    }
    if (Double.isNaN(val)) {
      System.out.println(" Incrementing with NAN " + i + " " + j);
      System.exit(-1);
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

    //System.out.println(" incrementing with "+val+" count for table "+a.name);
    a.incCount(i, alignment[j - 1], l, val);
    if (this.aHolder.smooth) {
      aHolder.getSmoothTable().incCount(i, alignment[j - 1], l, val);
    }
    //nothing
  }


}
