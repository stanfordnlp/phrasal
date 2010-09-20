package edu.stanford.nlp.mt.train.hmmalign;

/* This class does HMM training with EM.
 * Reports perplexity on training and test data.
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import edu.stanford.nlp.util.MutableInteger;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map;

public class HMM {

  private TTable tTable;
  private SentenceHandler corpus;
  static double PROB_SMOOTH;
  private TTable fTable;
  boolean eTags;
  boolean fTags;
  private boolean useFTagsT = GlobalParams.useFTagsT; //use the french tags for translation probabilities
  private boolean useETagsT = GlobalParams.useETagsT; //use the English tags in translation probabilities
  private ATable aTable;
  boolean verbose = false;
  static boolean trigram = false;
  boolean init_translation = true;
  boolean distOnly = true;
  static boolean updateTTable = true;
  static boolean useStayGoProbs = false;
  boolean useFNull = false;
  static boolean eQClasses = true;
  static boolean frontier = false;
  private ATableHMMHolder aTablesHolder;
  String fileAlignment = "";
  PrintStream aFile;
  private StayGoTable sgTable = null;
  private StayGoTables sgTables = null;
  int mask = 0;

  public HMM() {
  }


  public TTable getTTable() {
    return tTable;
  }

  public TTable getFTable() {
    return fTable;
  }

  public HMM(SentenceHandler corpus, int numIterations, TTable tTable, TTable fTable, int mask, int startiter) {
    this.corpus = corpus;
    eTags = corpus.eHasTags();
    fTags = corpus.fHasTags();
    this.mask = mask;
    PROB_SMOOTH = TTable.PROB_SMOOTH;
    if (distOnly) {
      if (trigram) {

        if (eQClasses) {
          aTable = new ATableHMM2EQ(corpus.getMaxLength());
        } else {
          aTable = new ATableHMM2(corpus.getMaxLength());
        }

      } else {
        aTable = new ATableHMM(corpus.getMaxLength());
      }
    } else {
      aTable = new ATableHMMFull(corpus.getMaxLength());
    }
    aTable.initializeUniform();
    this.tTable = tTable;
    this.fTable = fTable;
    init_translation = false;
    init();

    if (useStayGoProbs) {
      train_test_loop(startiter, numIterations);
    } else {
      train_test_loop(startiter, numIterations);
    }

  }


  public HMM(SentenceHandler corpus, int numIterations, TTable tTable, TTable fTable, ATable startTable, int mask, int startiter) {
    this.corpus = corpus;
    eTags = corpus.eHasTags();
    fTags = corpus.fHasTags();
    this.mask = mask;
    PROB_SMOOTH = TTable.PROB_SMOOTH;
    aTable = startTable;
    this.tTable = tTable;
    this.fTable = fTable;
    init_translation = false;
    init();
    train_test_loop(startiter, numIterations);
  }


  public HMM(SentenceHandler corpus, int numIterations, int mask) {
    this.corpus = corpus;
    eTags = corpus.eHasTags();
    fTags = corpus.fHasTags();
    this.mask = mask;
    PROB_SMOOTH = TTable.PROB_SMOOTH;
    if (distOnly) {
      if (trigram) {

        if (eQClasses) {
          aTable = new ATableHMM2EQ(corpus.getMaxLength());
        } else {
          aTable = new ATableHMM2(corpus.getMaxLength());
        }

      } else {
        aTable = new ATableHMM(corpus.getMaxLength());
      }
    } else {
      aTable = new ATableHMMFull(corpus.getMaxLength());
    }
    aTable.initializeUniform();
    init();
    train_test_loop(0, numIterations);

  }


  public void init() {

    if (HMM.useStayGoProbs) {
      if (!trigram) {
        sgTable = new StayGoTable(corpus);
        sgTable.initialize(.08);
      } else {
        sgTables = new StayGoTables(2, corpus);
        sgTables.initialize(.08);
      }

    }

    if (useFNull) {
      if (fTable == null) {
        System.out.println("Initializing fNull");
        fTable = new TTable(false);
      }
    }

    if (init_translation) {
      initializeTable();
    }

    initializeATables(mask);
  }


  /**
   * Initialize the alignment tables for tag sequences with enough support
   * Initializes according to a mask which specifies which tags to use
   * for alignment conditioning
   */

  public void initializeATables(int mask) {


    if (mask == 0) {
      return;
    }

    this.aTablesHolder = new ATableHMMHolder(mask);
    TupleCounter TC = new TupleCounter();
    int numTables = 0;

    //add the tables while testining on the training corpus
    //with the viterbi alignments of HMM simple or em_loop_2_eq

    ATable old = this.aTable;
    if (trigram) {
      aTable = new ATableHMM(corpus.getMaxLength());
      aTable.initializeUniform();
    }
    aTablesHolder.setUniform(aTable);
    aTablesHolder.setSmoothing(aTable);
    //aTable.printProbs();
    em_loop(true, null, TC);
    aTable = old;


    //now add to aTablesHolder only the tuples with enough support
    ATable a;
    for (Iterator<Map.Entry<IntTuple, MutableInteger>> i = TC.getIterator(); i.hasNext();) {
    	Map.Entry<IntTuple, MutableInteger> eN = i.next();
      IntTuple iT = eN.getKey();
      MutableInteger iH = eN.getValue();
      if (iH.intValue() > ATable.countCutoff) {
        //add an ATable for that one
        //if(iT.getSource()!=6){continue;}
        //if((iT.getMiddle()!=2)&&(iT.getMiddle()!=7)&&(iT.getMiddle()!=8)){continue;}
        if (distOnly) {

          if (trigram) {

            if (eQClasses) {
              a = new ATableHMM2EQ(corpus.getMaxLength());
            } else {
              a = new ATableHMM2(corpus.getMaxLength());
            }

          }//trigram
          else {
            a = new ATableHMM(corpus.getMaxLength());
          }

          //distonly
        } else {
          a = new ATableHMMFull(corpus.getMaxLength());
        }
        a.initialize(aTable); //initialzie from aTable
        //a.fixEmpty=false;
        //a.initializeUniform();
        a.incCount(iH.intValue());
        a.name = iT.toNameString(mask);

        numTables++;
        aTablesHolder.add(iT, a);


      }// if has enough data
      else {
        i.remove();
      }

    } //for iterator

    //set the smoothing table
    if (aTablesHolder.smooth) {

      if (distOnly) {

        if (trigram) {

          if (eQClasses) {
            a = new ATableHMM2EQ(corpus.getMaxLength());
          } else {
            a = new ATableHMM2(corpus.getMaxLength());
          }

        }//trigram
        else {
          a = new ATableHMM(corpus.getMaxLength());
        }

        //distonly
      } else {
        a = new ATableHMMFull(corpus.getMaxLength());
      }
      a.initialize(aTable); //initialzie from aTable
      //a.fixEmpty=false;
      //a.initializeUniform();
      aTablesHolder.setSmoothing(a);
      a.name = "smoothing";
      System.out.println(" Set smoothing table ");
    }// if smooth

    System.out.println("The number of alignment tables is " + numTables);
    aTablesHolder.setUniform(aTable);
  }


  public void train_test_loop(int startiter, int numIterations) {

    PrintStream alStream = null;
    //if(fTags&this.useFTagsA){this.aTablesHolder.printProbs();}

    System.out.println(" mask is " + mask);

    for (int it = startiter; it < numIterations + startiter; it++) {
      //First test on the test set to calculate perplexity /cross entropy
      System.out.println("HMM iteration " + (it + 1));

      if (GlobalParams.dumpAlignments) {
        this.fileAlignment = GlobalParams.resultPath + "At.hmm." + (it + 1);
        try {
          alStream = new PrintStream(new FileOutputStream(fileAlignment));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }


      if (trigram) {
        if (eQClasses) {
          em_loop_2_eq(false, alStream);
        } else {
          em_loop_2(false, alStream);
        }

      } else {
        if (frontier) {
          em_loop_right(false, alStream);
        } else {
          em_loop(false, alStream, null);
        }
      }

      //Iterate on the training set
      alStream.close();
      if (GlobalParams.dumpAlignments) {
        this.fileAlignment = GlobalParams.resultPath + "A.hmm." + (it + 1);
        try {
          alStream = new PrintStream(new FileOutputStream(fileAlignment));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      if (trigram) {
        if (eQClasses) {
          em_loop_2_eq(true, alStream);
        } else {
          em_loop_2_beam(true, alStream);
        }
      } else {
        if (frontier) {
          em_loop_right(true, alStream);
        } else {
          em_loop(true, alStream, null);
        }
      }

      //tTable.print();
      if (updateTTable) {
        tTable.normalizeTable(2);
        if (useFNull) {
          fTable.normalizeTable(2);
        }
      }
      if (useStayGoProbs) {
        if (!trigram) {
          sgTable.normalize();
        } else {
          sgTables.normalize();
        }
      }

      if ((it < 10)) {
        if (mask > 0) {
          this.aTablesHolder.normalize();
        } else {

          aTable.normalize();
        }
      }

      if ((mask > 0) && verbose) {
        this.aTablesHolder.printProbs();
      }
      System.out.println("Alignment probabilities are " + aTable.checkOK());
      System.out.println("Average English word entropy " + tTable.getCondEntropy());
      if (verbose) {
        System.out.println("Transition probabilities aTable ");
        aTable.printProbs();
        tTable.print();
      }

      if ((it + 1) % GlobalParams.saveFreq == 0) {
        //tTable.save(GlobalParams.resultPath+"tt.hmm."+(it+1));
        if (mask > 0) {
          aTablesHolder.saveNames(mask, GlobalParams.resultPath + "alt.hmm." + (it + 1));
        } else {
          aTable.save(GlobalParams.resultPath + "alt.hmm." + (it + 1));
        }
      }

    }//for it
    //save the final parameters
    if (mask > 0) {
      aTablesHolder.saveNames(mask, GlobalParams.resultPath + "at.final");
    } else {

      aTable.save(GlobalParams.resultPath + "at.final");
    }

    if (HMM.useStayGoProbs) {
      if (!trigram) {
        sgTable.save(GlobalParams.resultPath + "sg.final");
      } else {
        sgTables.save(GlobalParams.resultPath + "sg.final");
      }
    }

  }


  public void initializeTable() {
    tTable = new TTable(true);
    SentencePair sentPair;
    while ((sentPair = corpus.getNextPairTrain()) != null) {
      int count = sentPair.getCount();
      double uniform = count * (1 / (double) (sentPair.e.getLength()));
      for (int i = 0; i < sentPair.e.getLength(); i++) {
        int word_e = sentPair.getSource().getWord(i).getIndex();
        for (int j = 1; j < sentPair.getTarget().getLength(); j++) {
          int word_f = sentPair.getTarget().getWord(j).getIndex();
          if (fTags && (!useFTagsT)) {
            word_f = sentPair.getTarget().getWord(j).getWordId();
          }
          tTable.insert(word_e, word_f, 0, uniform);

        } //j
      }//i

    }//while

    tTable.normalizeTable(2);
  }




  /*
   * This is the simplest bigram HMM model with or without tags
   *
   */

  public void em_loop(boolean inTrain, PrintStream alStream, TupleCounter TC) {
    double cross_entropy = 0.0, viterbi_cross_entropy = 0.0;
    SentencePair sentPair;
    TPHandler tpHandler;
    AlHandlerHMM1 alHandler;

    if (useStayGoProbs) {
      alHandler = new AlHandlerHMM1SG(sgTable, mask, aTablesHolder, aTable);
    } else {
      if (mask == 0) {
        alHandler = new AlHandlerHMM1(aTable);
      } else {
        alHandler = new AlHandlerHMM1Tags(aTablesHolder, mask);
      }
    }

    int numWords = 0, sent_no = 0;

    if (useFNull) {

      if (eTags && fTags && useFTagsT) {
        tpHandler = new TPHandlerNULL(tTable, fTable, 2);
      } else {
        tpHandler = new TPHandlerNULL(tTable, fTable, 0);
      }

    } else {


      if (eTags && fTags && useFTagsT) {
        tpHandler = new TPHandlerFE1(tTable);
      } else {
        if (eTags && useETagsT) {
          tpHandler = new TPHandlerEtags(tTable);
        } else {
          tpHandler = new TPHandler(tTable);
        }
      }

    }

    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      sent_no++;
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      int start = 0, inc = 1, bound;
      tpHandler.setPair(sentPair);
      alHandler.setPair(sentPair);
      int[] alignment = new int[m + 1];  //for f from 1 to m, the corresponding i in the viterbi alignment
//now start collecting tables

      double[][] sum = new double[m + 1][2 * l + 1]; // forward probabilities
      double[][] max = new double[m + 1][2 * l + 1]; //maximum probability
      int[][] argmax = new int[m + 1][2 * l + 1]; //maximizing previous state
      double[][] backward = new double[m + 1][2 * l + 1];//backward probabilities
      bound = 2 * l;

      sum[0][0] = 1; // the rest are 0, starting in state 0
      max[0][0] = 1; // the rest are 0, starting in state 0

      int index;
      double prob;
      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i = 0; i <= 2 * l; i++) {

          if (i > l) {
            index = 0;
          } else {
            index = i;
          }

          prob = tpHandler.getProb(index, j);

          if (i == 0) {
            bound = 0;
          } else {
            bound = 2 * l;
          }
          if (i > l) {
            inc = l;
            start = i - l;
          } else {
            inc = 1;
            start = 0;
          }

          for (int i_p = start; i_p <= bound; i_p += inc) {
            alignment[j - 1] = i_p;
            double pjump = alHandler.getProb(i, j, alignment);
            sum[j][i] += sum[j - 1][i_p] * pjump * prob;
            double pmax = max[j - 1][i_p] * pjump * prob;
            if (pmax > max[j][i]) {
              max[j][i] = pmax;
              argmax[j][i] = i_p;
            }

          }//i_p
        }//i

      }//j

      //now add the jump prob for end of sentence
      for (int i = 0; i <= 2 * l; i++) {
        alignment[m] = i;
        double jmp = alHandler.getProb(2 * l + 1, m + 1, alignment);
        sum[m][i] *= jmp;
        max[m][i] *= jmp;
      }

      //now collect the backward probabilities

      for (int i = 0; i <= 2 * l; i++) {
        //initialize for jump to the end of sentence
        alignment[m] = i;
        backward[m][i] = alHandler.getProb(2 * l + 1, m + 1, alignment);

      }

      for (int j = m - 1; j >= 0; j--) {

        for (int i = 0; i <= 2 * l; i++) {

          start = 1;
          bound = 2 * l;
          inc = 1;
          if (i == 0) {
            start = 0;
            bound = l;
          }
          //start=0;bound=2*l;inc=1;

          for (int i_next = start; i_next <= bound; i_next += inc) {

            if (i_next > l) {
              index = 0;
              if ((i_next != i) && (i_next != (i + l))) {
                continue;
              }
            } else {
              index = i_next;
            }

            prob = tpHandler.getProb(index, j + 1);
            alignment[j] = i;
            backward[j][i] += alHandler.getProb(i_next, j + 1, alignment) * backward[j + 1][i_next] * prob;


          }//i_next


        }//i

      }//j

      //now backward[0][0] should be the total probability

      double denom = backward[0][0];

      if (denom == 0) {
        System.out.println("Denom is 0");
      }

      double max_prob = 0;
      int max_i = 0;
      double total_prob = 0;

      for (int i = 0; i <= 2 * l; i++) {
        total_prob += sum[m][i];
        if (max_prob < max[m][i]) {
          max_prob = max[m][i];
          max_i = i;
        }
      }

      if (denom < max_prob) {
        System.out.println(" denom is smaller than maxprob " + denom + " " + max_prob);
        //System.exit](-1);
      }

      //System.out.println(" total "+total_prob+" denom "+denom);
      //collect the viterbi alignment

      int[] viterbi_alignment = new int[m + 1];
      viterbi_alignment[m] = max_i;
      for (int j = m; j > 0; j--) {
        viterbi_alignment[j - 1] = argmax[j][viterbi_alignment[j]];
      }

      if (GlobalParams.dumpAlignments && (!inTrain) && (TC == null)) {

        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, sent_no, max_prob);

      } else {
        if (TC != null) {
          AlHandlerHMM1Tags aH = new AlHandlerHMM1Tags(aTablesHolder, mask);
          aH.addEntries(sentPair, TC, viterbi_alignment);
        }
      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain && (TC == null)) {

        //increment the alignment counts
        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index = 0;
            }
            double probF = tpHandler.getProb(index, j);

            if (i == 0) {
              bound = 0;
            } else {
              bound = 2 * l;
            }
            if (i > l) {
              inc = l;
              start = i - l;
            } else {
              inc = 1;
              start = 0;
            }

            for (int i_p = start; i_p <= bound; i_p += inc) {
              alignment[j - 1] = i_p;
              prob = sum[j - 1][i_p] * alHandler.getProb(i, j, alignment) * probF * backward[j][i];
              alHandler.incCount(i, j, alignment, prob * val);
            }
          }//i
        }//j

        //now for j+1

        for (int i_p = 0; i_p <= 2 * l; i_p++) {
          alignment[m] = i_p;
          alHandler.incCount(2 * l + 1, m + 1, alignment, sum[m][i_p] * val);

        }

        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            //we need the posterior
            prob = sum[j][i] * backward[j][i];

            if (j == m) {
              prob = sum[j][i];
            }
            //increment the translation table counts
            index = i;
            if (i > l) {
              index = 0;
            }
            tpHandler.incCount(index, j, prob * val);
          }//i
        } //j

      } //in train

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * (m + 1);
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    double perplexity = Math.exp(-cross_entropy / numWords);
    double viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }



  /*
    * This is the bigram HMM model with or without tags  with right frontier
    *
    */

  public void em_loop_right(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0, prob, probF, pjump, pmax, denom, max_prob = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    TPHandler tpHandler;
    AlHandlerHMM1 alHandler;

    if (useStayGoProbs) {
      alHandler = new AlHandlerHMM1SG(sgTable, mask, aTablesHolder, aTable);

    } else {

      if (mask == 0) {

        alHandler = new AlHandlerHMM1(aTable);
      } else {
        alHandler = new AlHandlerHMM1Tags(aTablesHolder, mask);
      }

    }

    int numWords = 0, index, sent_no = 0;
    double[][] sum; // forward probabilities
    double[][] max; //maximum probability
    IntPair[][] argmax; //maximizing previous state
    double[][] backward;//backward probabilities
    int[] viterbi_alignment, alignment;  //for f from 1 to m, the corresponding i in the viterbi alignment


    if (useFNull) {
      tpHandler = new TPHandlerNULL(tTable, fTable, 0);
    } else {


      if (eTags && fTags && useFTagsT) {
        tpHandler = new TPHandlerFE1(tTable);
      } else {
        if (eTags && useETagsT) {
          tpHandler = new TPHandlerEtags(tTable);
        } else {
          tpHandler = new TPHandler(tTable);
        }
      }

    }

    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      sent_no++;
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      tpHandler.setPair(sentPair);
      alHandler.setPair(sentPair);
      alignment = new int[m + 1];
      //now start collecting tables

      sum = new double[m + 1][2 * l + 1];   //sum(j,i)=p(f1,...,fj,i|e)  , j=0..m
      max = new double[m + 1][2 * l + 1];   //max{aj-1}max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      argmax = new IntPair[m + 1][2 * l + 1]; //argmax {aj-1} max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      backward = new double[m + 1][2 * l + 1]; // backward(j,i)=p(fj+1,..fm|aj=i) j=0..m

      sum[0][0] = 1; // the rest are 0, starting in state 0
      max[0][0] = 1; // the rest are 0, starting in state 0
      argmax[0][0] = new IntPair(0, 0);

      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i_front = 0; i_front <= l; i_front++) { // thr frontier can only be from 0 to l

          prob = tpHandler.getProb(i_front, j);
          for (int i_pfront = 0; i_pfront < i_front; i_pfront++) {
            //i has to be i_front
            alignment[j - 1] = i_pfront;
            pjump = alHandler.getProb(i_front, j, alignment);
            sum[j][i_front] += sum[j - 1][i_pfront] * pjump * prob;
            //System.out.println(" added to sum "+j+" "+i_front+" "+sum[j-1][i_pfront]*pjump*prob+" for prev "+i_pfront);
            pmax = max[j - 1][i_pfront] * pjump * prob;
            if (pmax > max[j][i_front]) {
              max[j][i_front] = pmax;
              argmax[j][i_front] = new IntPair(i_front, i_pfront);
            }

          }

          //now for i_pfront=i_front;

          int i_pfront = i_front;
          for (int i = 0; i <= i_front; i++) {

            if ((i_pfront > 0) && (i == 0)) {
              continue;
            }
            prob = tpHandler.getProb(i, j);
            alignment[j - 1] = i_pfront;
            pjump = alHandler.getProb(i, j, alignment);
            sum[j][i_front] += sum[j - 1][i_pfront] * pjump * prob;
            //System.out.println(" added to sum "+j+" "+i_front+" "+sum[j-1][i_pfront]*pjump*prob+" for prev "+i_pfront+" and i "+i);
            pmax = max[j - 1][i_pfront] * pjump * prob;
            if (pmax > max[j][i_front]) {
              max[j][i_front] = pmax;
              argmax[j][i_front] = new IntPair(i, i_pfront);
            }

          }
          //now i=i_pfront+l;

          if (i_pfront > 0) {
            prob = tpHandler.getProb(i_pfront + l, j);
            alignment[j - 1] = i_pfront;
            pjump = alHandler.getProb(i_pfront + l, j, alignment);
            sum[j][i_front] += sum[j - 1][i_pfront] * pjump * prob;
            //System.out.println(" added to sum "+j+" "+i_front+" "+sum[j-1][i_pfront]*pjump*prob+" for prev "+i_pfront+" and i "+(i_pfront+l));
            pmax = max[j - 1][i_pfront] * pjump * prob;
            if (pmax > max[j][i_front]) {
              max[j][i_front] = pmax;
              argmax[j][i_front] = new IntPair(i_pfront + l, i_pfront);
            }
          }

        }//i_front
      }//j


      //now add the jump prob for end of sentence
      for (int i = 0; i <= l; i++) {
        alignment[m] = i;
        double jmp = alHandler.getProb(2 * l + 1, m + 1, alignment);
        sum[m][i] *= jmp;
        max[m][i] *= jmp;
        //System.out.println(" multiplied sum m "+i+" times "+jmp);

      }



      //now collect the backward probabilities

      for (int i = 0; i <= l; i++) {
        //initialize for jump to the end of sentence
        alignment[m] = i;
        backward[m][i] = alHandler.getProb(2 * l + 1, m + 1, alignment);

      }


      for (int j = m - 1; j >= 0; j--) {

        for (int i_front = 0; i_front <= l; i_front++) { // thr frontier can only be from 0 to l


          for (int i_nextfront = i_front + 1; i_nextfront <= l; i_nextfront++) {
            //i has to be i_nextfront
            prob = tpHandler.getProb(i_nextfront, j + 1);
            alignment[j] = i_front;
            pjump = alHandler.getProb(i_nextfront, j + 1, alignment);
            backward[j][i_front] += backward[j + 1][i_nextfront] * pjump * prob;

          }

          //now for i_nextfront=i_front;

          int i_nextfront = i_front;
          for (int i = 0; i <= i_front; i++) {
            if ((i_front > 0) && (i == 0)) {
              continue;
            }
            prob = tpHandler.getProb(i, j + 1);
            alignment[j] = i_front;
            pjump = alHandler.getProb(i, j + 1, alignment);
            backward[j][i_front] += backward[j + 1][i_nextfront] * pjump * prob;

          }
          //now i=i_pfront+l;

          if (i_front > 0) {
            prob = tpHandler.getProb(i_front + l, j + 1);
            alignment[j] = i_front;
            pjump = alHandler.getProb(i_front + l, j + 1, alignment);
            backward[j][i_front] += backward[j + 1][i_nextfront] * pjump * prob;
          }

        }//i_front

      }//j

      //now backward[0][0] should be the total probability

      denom = backward[0][0];
      max_prob = 0;
      int max_i = 0;
      double total_prob = 0;

      for (int i = 0; i <= l; i++) {
        total_prob += sum[m][i];
        if (max_prob < max[m][i]) {
          max_prob = max[m][i];
          max_i = i;
        }
      }

      if (denom < max_prob) {
        System.out.println(" denom is smaller than maxprob " + denom + " " + max_prob);
        //System.exit](-1);
      }

      //System.out.println("total "+total_prob+" denom "+denom);

      //collect the viterbi alignment

      viterbi_alignment = new int[m + 1];
      viterbi_alignment[m] = argmax[m][max_i].getSource();
      int fr_prev = max_i;
      for (int j = m; j > 0; j--) {

        fr_prev = argmax[j][fr_prev].getTarget();
        //System.out.println("fr prev is "+fr_prev);
        viterbi_alignment[j - 1] = argmax[j - 1][fr_prev].getSource();

      }


      if (GlobalParams.dumpAlignments && (!inTrain)) {

        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, sent_no, max_prob);

      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain) {

        //increment the alignment counts
        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index = 0;
            }
            probF = tpHandler.getProb(index, j);

            for (int i_p = 0; i_p <= l; i_p++) {
              alignment[j - 1] = i_p;
              int i_next = i_p;
              if ((i <= l) && (i > i_p)) {
                i_next = i;
              }
              prob = sum[j - 1][i_p] * alHandler.getProb(i, j, alignment) * probF * backward[j][i_next];
              alHandler.incCount(i, j, alignment, prob * val);

            }
          }//i
        }//j

        //now for j+1


        for (int i_p = 0; i_p <= l; i_p++) {
          alignment[m] = i_p;
          alHandler.incCount(2 * l + 1, m + 1, alignment, sum[m][i_p] * val);

        }

        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            //we need the posterior
            index = i;
            if (i > l) {
              index = 0;
            }
            probF = tpHandler.getProb(index, j);
            prob = 0;
            for (int i_p = 0; i_p <= l; i_p++) {
              alignment[j - 1] = i_p;
              int i_next = i_p;
              if ((i <= l) && (i > i_p)) {
                i_next = i;
              }
              prob += sum[j - 1][i_p] * alHandler.getProb(i, j, alignment) * probF * backward[j][i_next];


            }//i_p

            tpHandler.incCount(index, j, prob * val);

          }//i
        } //j


      } //in train

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * (m + 1);
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    perplexity = Math.exp(-cross_entropy / numWords);
    viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }


  /**
   * Trigram HMM alignment
   */

  public void em_loop_2(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0, prob, probF, pjump, pmax, denom, max_prob = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    TPHandler tpHandler;
    AlHandlerHMM2 alHandler;
    if (mask > 0) {
      alHandler = new AlHandlerHMM2Tags(this.aTablesHolder, mask, false);
    } else {
      alHandler = new AlHandlerHMM2(aTable);
    }
    int numWords = 0, index, sent_no = 0;
    double[][][] sum; // forward probabilities
    double[][][] max; //maximum probability
    int[][][] argmax; //maximizing previous state
    double[][][] backward;//backward probabilities
    int[] viterbi_alignment, alignment;  //for f from 1 to m, the corresponding i in the viterbi alignment
    tpHandler = new TPHandler(tTable);


    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      sent_no++;
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      int start = 0, inc = 1, bound;
      tpHandler.setPair(sentPair);
      alHandler.setPair(sentPair);
      alignment = new int[m + 1];
      //now start collecting tables

      sum = new double[m + 1][2 * l + 1][2 * l + 1];      //sum(j,i)=p(f1,...,fj,i|e)  , j=0..m
      max = new double[m + 1][2 * l + 1][2 * l + 1];      //max{aj-1}max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      argmax = new int[m + 1][2 * l + 1][2 * l + 1];      //argmax {aj-1} max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      backward = new double[m + 1][2 * l + 1][2 * l + 1]; // backward(j,i)=p(fj+1,..fm|aj=i) j=0..m
      bound = 2 * l;

      sum[0][0][0] = 1; // the rest are 0, starting in state 0
      max[0][0][0] = 1; // the rest are 0, starting in state 0

      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i = 0; i <= 2 * l; i++) {

          if (i > l) {
            index = 0;
          } else {
            index = i;
          }

          prob = tpHandler.getProb(index, j);

          if (j == 1) {
            bound = 0;
          }
          if (i == 0) {
            bound = 0;
          } else {
            bound = 2 * l;
          }
          if (i > l) {
            inc = l;
            start = i - l;
          } else {
            inc = 1;
            start = 0;
          }

          for (int i_p = start; i_p <= bound; i_p += inc) {
            for (int i_pp = 0; i_pp <= bound; i_pp++) {
              alignment[j - 1] = i_p;
              if (j > 1) {
                alignment[j - 2] = i_pp;
              }
              pjump = alHandler.getProb(i, j, alignment);
              sum[j][i][i_p] += sum[j - 1][i_p][i_pp] * pjump * prob;
              pmax = max[j - 1][i_p][i_pp] * pjump * prob;
              if (pmax > max[j][i][i_p]) {
                max[j][i][i_p] = pmax;
                argmax[j][i][i_p] = i_pp;
              }
            }//i_pp
          }//i_p
        }//i

      }//j


      //now add the jump prob for end of sentence
      for (int i = 0; i <= 2 * l; i++) {
        alignment[m] = i;
        for (int i_p = 0; i_p <= 2 * l; i_p++) {
          alignment[m - 1] = i_p;
          if (!legitimate(i, i_p, l)) {
            continue;
          }
          double jmp = alHandler.getProb(2 * l + 1, m + 1, alignment);
          sum[m][i][i_p] *= jmp;
          max[m][i][i_p] *= jmp;
        }
      }

      //now collect the backward probabilities

      for (int i = 0; i <= 2 * l; i++) {
        for (int j = 0; j <= 2 * l; j++) {
          if ((j == 0) && (i > l)) {
            continue;
          }
          if ((i == 0) && (j > 0)) {
            continue;
          }
          if ((i > l) && (i != (j + l)) && (i != j)) {
            continue;
          }
          alignment[m] = i;
          alignment[m - 1] = j;
          backward[m][i][j] = alHandler.getProb(2 * l + 1, m + 1, alignment);

        }
      }

      for (int j = m - 1; j >= 0; j--) {

        for (int i_p = 0; i_p <= 2 * l; i_p++) {
          for (int i = 0; i <= 2 * l; i++) {

            if (!legitimate(i, i_p, l)) {
              continue;
            }
            start = 1;
            bound = 2 * l;
            inc = 1;
            if (i == 0) {
              start = 0;
              bound = l;
            }
            //start=0;bound=2*l;inc=1;

            for (int i_next = start; i_next <= bound; i_next += inc) {

              if (i_next > l) {
                index = 0;
                if ((i_next != i) && (i_next != (i + l))) {
                  continue;
                }
              } else {
                index = i_next;
              }

              prob = tpHandler.getProb(index, j + 1);
              alignment[j] = i;
              if (j > 0) {
                alignment[j - 1] = i_p;
              }
              backward[j][i][i_p] += alHandler.getProb(i_next, j + 1, alignment) * backward[j + 1][i_next][i] * prob;


            }//i_next


          }//i
        }//i_p

      }//j

      //now backward[0][0] should be the total probability

      denom = backward[0][0][0];
      max_prob = 0;
      int max_i = 0;
      int max_i_1 = 0;
      for (int i = 0; i <= 2 * l; i++) {
        for (int j = 0; j <= 2 * l; j++) {
          if (max_prob < max[m][i][j]) {
            max_prob = max[m][i][j];
            max_i = i;
            max_i_1 = j;
          }
        }
      }

      if (denom < max_prob) {
        System.out.println(" denom is smaller than maxprob " + denom + " " + max_prob);
        //System.exit(-1);
      }

      //collect the viterbi alignment

      viterbi_alignment = new int[m + 1];
      viterbi_alignment[m] = max_i;
      viterbi_alignment[m - 1] = max_i_1;
      for (int j = m; j > 1; j--) {
        viterbi_alignment[j - 2] = argmax[j][viterbi_alignment[j]][viterbi_alignment[j - 1]];

      }


      if (GlobalParams.dumpAlignments) {

        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, sent_no, max_prob);

      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain) {

        //increment the alignment counts
        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index = 0;
            }
            probF = tpHandler.getProb(index, j);

            if (i == 0) {
              bound = 0;
            } else {
              bound = 2 * l;
            }
            if (i > l) {
              inc = l;
              start = i - l;
            } else {
              inc = 1;
              start = 0;
            }

            for (int i_p = start; i_p <= bound; i_p += inc) {
              for (int i_pp = 0; i_pp <= bound; i_pp++) {
                alignment[j - 1] = i_p;
                if (j > 1) {
                  alignment[j - 2] = i_pp;
                }
                prob = sum[j - 1][i_p][i_pp] * alHandler.getProb(i, j, alignment) * probF * backward[j][i][i_p];
                alHandler.incCount(i, j, alignment, prob * val);

              }
            }
          }//i
        }//j
        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            for (int i_p = 0; i_p <= 2 * l; i_p++) {
              //we need the posterior
              prob = sum[j][i][i_p] * backward[j][i][i_p];

              //increment the translation table counts
              index = i;
              if (i > l) {
                index = 0;
              }
              tpHandler.incCount(index, j, prob * val);


            }//i
          }
        } //j


      } //in train

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * (m + 1);
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    perplexity = Math.exp(-cross_entropy / numWords);
    viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }


  /**
   * Trigram HMM alignment when we have classes for the previous
   * jump and not all -30 to 30
   */

  public void em_loop_2_eq(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0, prob, probF, pjump, pmax, denom, max_prob = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    TPHandler tpHandler;
    AlHandler alHandler;
    ATableHMM2EQ aTable = (ATableHMM2EQ) this.aTable;


    if (useStayGoProbs) {
      alHandler = new AlHandlerHMM2EQSG(sgTables, mask, aTablesHolder, aTable);

    } else {

      if (mask > 0) {
        alHandler = new AlHandlerHMM2Tags(aTablesHolder, mask, true);
      } else {
        alHandler = new AlHandlerHMM2EQ(aTable);

      }
    }
    int numWords = 0, index, sent_no = 0;
    double[][][] sum; // forward probabilities
    double[][][] max; //maximum probability
    IntPair[][][] argmax; //maximizing previous state
    double[][][] backward;//backward probabilities
    int[] viterbi_alignment, alignment;  //for f from 1 to m, the corresponding i in the viterbi alignment

    if (useFNull) {
      tpHandler = new TPHandlerNULL(tTable, fTable, 0);
    } else {
      tpHandler = new TPHandler(tTable);
    }

    int MAX_FLDS = ATableHMM2EQ.MAX_FLDS; // should make that better


    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      sent_no++;
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      int start = 0, inc = 1, bound;
      tpHandler.setPair(sentPair);
      alHandler.setPair(sentPair);
      alignment = new int[m + 1];
      //now start collecting tables

      sum = new double[m + 1][2 * l + 1][2 * MAX_FLDS];      //sum(j,i)=p(f1,...,fj,i|e)  , j=0..m
      max = new double[m + 1][2 * l + 1][2 * MAX_FLDS];      //max{aj-1}max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      argmax = new IntPair[m + 1][2 * l + 1][2 * MAX_FLDS];      //argmax {aj-1} max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      backward = new double[m + 1][2 * l + 1][2 * MAX_FLDS]; // backward(j,i)=p(fj+1,..fm|aj=i) j=0..m
      bound = 2 * l;

      sum[0][0][MAX_FLDS] = 1; // the rest are 0, starting in state 0
      max[0][0][MAX_FLDS] = 1; // the rest are 0, starting in state 0

      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i = 0; i <= 2 * l; i++) {

          if (i > l) {
            index = 0;
          } else {
            index = i;
          }

          prob = tpHandler.getProb(index, j);

          for (int jump = 0; jump < 2 * MAX_FLDS; jump++) {

            if (!ATableHMM2EQ.possibleExternal(i, jump, l)) {
              continue;
            }

            for (int i_p = 0; i_p <= 2 * l; i_p++) {

              if (!legitimate(i, i_p, l)) {
                continue;
              }

              if (aTable.jump(i, i_p, l) != jump) {
                continue;
              }


              for (int j_pp = 0; j_pp < 2 * MAX_FLDS; j_pp++) {

                if (!ATableHMM2EQ.possibleExternal(i_p, j_pp, l)) {
                  continue;
                }

                //System.out.println(i_p +" with jump "+j_pp+" is possible");
                alignment[j - 1] = i_p;
                if (j > 1) {
                  alignment[j - 2] = j_pp;
                }
                pjump = alHandler.getProb(i, j, alignment);
                //System.out.println("called for forward "+j+" "+i+" "+jump);
                sum[j][i][jump] += sum[j - 1][i_p][j_pp] * pjump * prob;
                pmax = max[j - 1][i_p][j_pp] * pjump * prob;
                if (pmax > max[j][i][jump]) {
                  max[j][i][jump] = pmax;
                  argmax[j][i][jump] = new IntPair(i_p, j_pp);
                }
              }//j_pp
            }//i_p

            //System.out.println(" forward "+j+" "+i+" jump "+jump +" is "+sum[j][i][jump]);

          }//jump

        }//i

      }//j


      //now add the jump prob for end of sentence
      for (int i = 0; i <= 2 * l; i++) {
        alignment[m] = i;
        for (int i_p = 0; i_p < 2 * MAX_FLDS; i_p++) {
          alignment[m - 1] = i_p;
          if (!ATableHMM2EQ.possibleExternal(i, i_p, l)) {
            continue;
          }
          double jmp = alHandler.getProb(2 * l + 1, m + 1, alignment);
          sum[m][i][i_p] *= jmp;
          max[m][i][i_p] *= jmp;
        }
      }

      //now collect the backward probabilities

      for (int i = 0; i <= 2 * l; i++) {
        for (int j = 0; j < 2 * MAX_FLDS; j++) {
          if (!ATableHMM2EQ.possibleExternal(i, j, l)) {
            continue;
          }
          alignment[m] = i;
          alignment[m - 1] = j;
          backward[m][i][j] = alHandler.getProb(2 * l + 1, m + 1, alignment);

        }
      }

      for (int j = m - 1; j >= 0; j--) {

        for (int jump = 0; jump < 2 * MAX_FLDS; jump++) {

          for (int i = 0; i <= 2 * l; i++) {
            //looking at i to be the next
            if (!ATableHMM2EQ.possibleExternal(i, jump, l)) {
              continue;
            }

            start = 1;
            bound = 2 * l;
            inc = 1;
            if (i == 0) {
              start = 0;
              bound = l;
            }
            //start=0;bound=2*l;inc=1;

            for (int i_next = start; i_next <= bound; i_next += inc) {

              if (!legitimate(i_next, i, l)) {
                continue;
              }

              int jump_next = aTable.jump(i_next, i, l);
              if (i_next > l) {
                index = 0;
                if ((i_next != i) && (i_next != (i + l))) {
                  continue;
                }
              } else {
                index = i_next;
              }

              prob = tpHandler.getProb(index, j + 1);
              alignment[j] = i;
              if (j > 0) {
                alignment[j - 1] = jump;
              }
              backward[j][i][jump] += alHandler.getProb(i_next, j + 1, alignment) * backward[j + 1][i_next][jump_next] * prob;


            }//i_next


          }//i
        }//i_p

      }//j

      //now backward[0][0] should be the total probability

      denom = backward[0][0][MAX_FLDS];
      max_prob = 0;
      int max_i = 0;
      int max_i_1 = 0;
      for (int i = 0; i <= 2 * l; i++) {
        for (int j = 0; j < 2 * MAX_FLDS; j++) {
          if (max_prob < max[m][i][j]) {
            max_prob = max[m][i][j];
            max_i = i;
            max_i_1 = j;
          }
        }
      }

      if (denom < max_prob) {
        System.out.println(" denom is smaller than maxprob " + denom + " " + max_prob);
        //System.exit(-1);
      }

      //collect the viterbi alignment

      viterbi_alignment = new int[m + 1];
      viterbi_alignment[m] = max_i;

      for (int j = m; j > 1; j--) {
        viterbi_alignment[j - 1] = argmax[j][viterbi_alignment[j]][max_i_1].getSource();
        max_i_1 = argmax[j][viterbi_alignment[j]][max_i_1].getTarget();
        //viterbi_alignment[j-2]=argmax[j][viterbi_alignment[j]][viterbi_alignment[j-1]];
      }


      if (GlobalParams.dumpAlignments && (!inTrain)) {
        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, sent_no, max_prob);
      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain) {

        //increment the alignment counts
        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index = 0;
            }
            probF = tpHandler.getProb(index, j);

            for (int jump_1 = 0; jump_1 < 2 * MAX_FLDS; jump_1++) {
              if (!ATableHMM2EQ.possibleExternal(i, jump_1, l)) {
                continue;
              }

              for (int i_p = 0; i_p <= 2 * l; i_p++) {

                if (!legitimate(i, i_p, l)) {
                  continue;
                }

                if (aTable.jump(i, i_p, l) != jump_1) {
                  continue;
                }

                for (int jump = 0; jump < 2 * MAX_FLDS; jump++) {

                  if (!ATableHMM2EQ.possibleExternal(i_p, jump, l)) {
                    continue;
                  }


                  alignment[j - 1] = i_p;
                  if (j > 1) {
                    alignment[j - 2] = jump;
                  }
                  prob = sum[j - 1][i_p][jump] * alHandler.getProb(i, j, alignment) * probF * backward[j][i][jump_1];
                  alHandler.incCount(i, j, alignment, prob * val);
                }//jump
              } //i_p
            }//jump_1
          }//i
        }//j


        //increment for EOS
        for (int i = 0; i <= 2 * l; i++) {
          for (int jump_1 = 0; jump_1 < 2 * MAX_FLDS; jump_1++) {
            if (!ATableHMM2EQ.possibleExternal(i, jump_1, l)) {
              continue;
            }
            prob = sum[m][i][jump_1];
            alignment[m] = i;
            alignment[m - 1] = jump_1;
            alHandler.incCount(2 * l + 1, m + 1, alignment, prob * val);
          }
        }



        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            for (int i_p = 0; i_p < 2 * MAX_FLDS; i_p++) {
              //we need the posterior
              prob = sum[j][i][i_p] * backward[j][i][i_p];
              if (j == m) {
                prob = sum[j][i][i_p];
              }

              //increment the translation table counts
              index = i;
              if (i > l) {
                index = 0;
              }
              tpHandler.incCount(index, j, prob * val);


            }//i
          }
        } //j


      } //in train

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * (m + 1);
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    perplexity = Math.exp(-cross_entropy / numWords);
    viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }


  /**
   * returns true if i can legitimately follow i_p
   */
  public static boolean legitimate(int i, int i_p, int l) {

    if ((i > l) && (i < 2 * l + 1) && (i_p == 0)) {
      return false;
    }
    if ((i == 0) && (i_p > 0)) {
      return false;
    }
    if ((i > l) && (i_p != i) && (i_p != (i - l))) {
      return false;
    }
    return true;

  }


  /**
   * Trigram HMM alignment . This will be viterbi training
   * using a beam search
   */

  public void em_loop_2_beam(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0, probF, pjump, pmax, denom, max_prob = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    TPHandler tpHandler;
    AlHandlerHMM2 alHandler;
    if (mask > 0) {
      alHandler = new AlHandlerHMM2Tags(this.aTablesHolder, mask, false);
    } else {
      alHandler = new AlHandlerHMM2(aTable);
    }
    int numWords = 0, sent_no = 0;
    if (useFNull) {
      tpHandler = new TPHandlerNULL(tTable, fTable, 0);
    } else {
      tpHandler = new TPHandler(tTable);
    }
    int K = 500;
    if (!inTrain) {
      K = 5000;
    }


    double ratio_kept = 0;
    //each time ratio_kept+=current_ratio+old*s/s+1

    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      sent_no++;
      if (sent_no % 500 == 0) {
        System.out.println(sent_no);
      }
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      int start = 0, inc = 1, bound;
      tpHandler.setPair(sentPair);
      alHandler.setPair(sentPair);
      int[] alignment = new int[m + 1];  //for f from 1 to m, the corresponding i in the viterbi alignment
//now start collecting tables

      double[][][] backward = new double[m + 1][2 * l + 1][2 * l + 1];//backward probabilities
      double[][][] sum = new double[m + 1][2 * l + 1][2 * l + 1]; // forward probabilities
      double[][][] max = new double[m + 1][2 * l + 1][2 * l + 1]; //maximum probability
      double[][][] max_backward = new double[m + 1][2 * l + 1][2 * l + 1]; //maximum backward probability
      int[][][] argmax = new int[m + 1][2 * l + 1][2 * l + 1]; //maximizing previous state

      bound = 2 * l;

      sum[0][0][0] = 1; // the rest are 0, starting in state 0
      max[0][0][0] = 1; // the rest are 0, starting in state 0

      double max_prev_iter = 1.0; //if a path is smaller than max_prev_iter/K , cut it
      double threshold = max_prev_iter / K;
      double max_this_iter = 0;

      int total = 0;
      int kept = 0;

      int index;
      double prob;
      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i = 0; i <= 2 * l; i++) {

          if (i > l) {
            index = 0;
          } else {
            index = i;
          }

          prob = tpHandler.getProb(index, j);

          if (i == 0) {
            bound = 0;
          } else {
            bound = 2 * l;
          }
          if (i > l) {
            inc = l;
            start = i - l;
          } else {
            inc = 1;
            start = 0;
          }

          for (int i_p = start; i_p <= bound; i_p += inc) {
            for (int i_pp = 0; i_pp <= bound; i_pp++) {

              total++;
              if (max[j - 1][i_p][i_pp] < threshold) {
                continue;
              }
              alignment[j - 1] = i_p;
              kept++;
              if (j > 1) {
                alignment[j - 2] = i_pp;
              }
              pjump = alHandler.getProb(i, j, alignment);
              sum[j][i][i_p] += sum[j - 1][i_p][i_pp] * pjump * prob;
              pmax = max[j - 1][i_p][i_pp] * pjump * prob;
              if (pmax > max[j][i][i_p]) {
                max[j][i][i_p] = pmax;
                argmax[j][i][i_p] = i_pp;
              }
              if (pmax > max_this_iter) {
                max_this_iter = pmax;
              }
            }//i_pp
          }//i_p
        }//i

        //System.out.println(j+" kept "+kept+" out of "+total);

        max_prev_iter = max_this_iter;
        max_this_iter = 0;
        threshold = max_prev_iter / K;

      }//j

      ratio_kept = (ratio_kept * 2 * sent_no + (kept / (float) total)) / (2 * sent_no + 1);

      //now add the jump prob for end of sentence
      for (int i = 0; i <= 2 * l; i++) {
        alignment[m] = i;
        for (int i_p = 0; i_p <= 2 * l; i_p++) {
          alignment[m - 1] = i_p;
          double jmp = alHandler.getProb(2 * l + 1, m + 1, alignment);
          sum[m][i][i_p] *= jmp;
          max[m][i][i_p] *= jmp;
        }
      }



      //now collect the backward probabilities
      if (inTrain) {
        max_this_iter = 0;
        for (int i = 0; i <= 2 * l; i++) {
          for (int j = 0; j <= 2 * l; j++) {
            if ((j == 0) && (i > l)) {
              continue;
            }
            if ((i == 0) && (j > 0)) {
              continue;
            }
            if ((i > l) && (i != (j + l)) && (i != j)) {
              continue;
            }
            alignment[m] = i;
            alignment[m - 1] = j;
            backward[m][i][j] = alHandler.getProb(2 * l + 1, m + 1, alignment);
            max_backward[m][i][j] = backward[m][i][j];
            if (backward[m][i][j] > max_this_iter) {
              max_this_iter = backward[m][i][j];
            }

          }
        }


        max_prev_iter = max_this_iter;
        max_this_iter = 0;
        threshold = max_prev_iter / K;

        total = 0;
        kept = 0;
        for (int j = m - 1; j >= 0; j--) {
          for (int i_p = 0; i_p <= 2 * l; i_p++) {
            for (int i = 0; i <= 2 * l; i++) {

              start = 1;
              bound = 2 * l;
              inc = 1;
              if (i == 0) {
                start = 0;
                bound = l;
              }
              //start=0;bound=2*l;inc=1;

              for (int i_next = start; i_next <= bound; i_next += inc) {

                if (i_next > l) {
                  index = 0;
                  if ((i_next != i) && (i_next != (i + l))) {
                    continue;
                  }
                } else {
                  index = i_next;
                }
                total++;
                if (max_backward[j + 1][i_next][i] < threshold) {
                  continue;
                }
                kept++;
                prob = tpHandler.getProb(index, j + 1);
                alignment[j] = i;
                if (j > 0) {
                  alignment[j - 1] = i_p;
                }
                pjump = alHandler.getProb(i_next, j + 1, alignment);
                backward[j][i][i_p] += pjump * backward[j + 1][i_next][i] * prob;
                pmax = max_backward[j + 1][i_next][i] * pjump * prob;
                if (pmax > max_backward[j][i][i_p]) {
                  max_backward[j][i][i_p] = pmax;
                }
                if (pmax > max_this_iter) {
                  max_this_iter = pmax;
                }

              }//i_next

            }//i
          }//i_p

          //System.out.println(j+" kept "+kept+" out of "+total);
          max_prev_iter = max_this_iter;
          max_this_iter = 0;
          threshold = max_prev_iter / K;

        }//j

        ratio_kept = (ratio_kept * (2 * sent_no + 1) + (kept / (float) total)) / (2 * sent_no + 2);

      }

      max_prob = 0;
      int max_i = 0;
      int max_i_1 = 0;
      denom = 0;
      for (int i = 0; i <= 2 * l; i++) {
        for (int j = 0; j <= 2 * l; j++) {
          denom += sum[m][i][j];
          if (max_prob < max[m][i][j]) {
            max_prob = max[m][i][j];
            max_i = i;
            max_i_1 = j;
          }
        }
      }

      if (backward[0][0][0] == denom) {
        System.out.println("Amazing! These are equal \n");
      }

      //collect the viterbi alignment

      int[] viterbi_alignment = new int[m + 1];
      viterbi_alignment[m] = max_i;
      viterbi_alignment[m - 1] = max_i_1;
      for (int j = m; j > 1; j--) {
        viterbi_alignment[j - 2] = argmax[j][viterbi_alignment[j]][viterbi_alignment[j - 1]];

      }


      if (GlobalParams.dumpAlignments) {

        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, sent_no, max_prob);

      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain) {

        //increment the alignment counts
        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index = 0;
            }
            probF = tpHandler.getProb(index, j);

            if (i == 0) {
              bound = 0;
            } else {
              bound = 2 * l;
            }
            if (i > l) {
              inc = l;
              start = i - l;
            } else {
              inc = 1;
              start = 0;
            }

            for (int i_p = start; i_p <= bound; i_p += inc) {
              if (backward[j][i][i_p] == 0) {
                continue;
              }
              for (int i_pp = 0; i_pp <= bound; i_pp++) {
                if (sum[j - 1][i_p][i_pp] == 0) {
                  continue;
                }
                alignment[j - 1] = i_p;
                if (j > 1) {
                  alignment[j - 2] = i_pp;
                }
                prob = sum[j - 1][i_p][i_pp] * alHandler.getProb(i, j, alignment) * probF * backward[j][i][i_p];
                alHandler.incCount(i, j, alignment, prob * val);

              }
            }
          }//i
        }//j
        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            for (int i_p = 0; i_p <= 2 * l; i_p++) {
              //we need the posterior

              prob = sum[j][i][i_p] * backward[j][i][i_p];
              if (prob == 0) {
                continue;
              }
              //increment the translation table counts
              index = i;
              if (i > l) {
                index = 0;
              }
              tpHandler.incCount(index, j, prob * val);


            }//i
          }
        } //j


      } //in train

      /*
       if(inTrain){

       for(int i_last=0;i_last<=2*l;i_last++){

       for(int i_plast=0;i_plast<=2*l;i_plast++){

       if(sum[m][i_last][i_plast]>0){

       prob=sum[m][i_last][i_plast];
       viterbi_alignment=new int[m+1];
       viterbi_alignment[m]=i_last;
       viterbi_alignment[m-1]=i_plast;
       for(int j=m;j>1;j--){
        viterbi_alignment[j-2]=argmax[j][viterbi_alignment[j]][viterbi_alignment[j-1]];

      }

       //increment the alignment counts
      for(int j=1;j<=m;j++){
       int i=viterbi_alignment[j];
       int i_p=viterbi_alignment[j-1];
       int i_pp=(j>1?viterbi_alignment[j-2]:0);
       alignment[j-1]=i_p;
       if(j>1){alignment[j-2]=i_pp;}
         alHandler.incCount(i,j,alignment,prob*val);

       }//j
       //increment the translation probability counts

       for(int j=1;j<=m;j++){
        int i=viterbi_alignment[j];

       //increment the translation table counts
        index=i; if(i>l){index=0;}
        tpHandler.incCount(index,j,prob*val);

       } //j

       }
       }
       }

      } //in train
      */

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * (m + 1);
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    System.out.println("Ratio kept " + ratio_kept);
    perplexity = Math.exp(-cross_entropy / numWords);
    viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }




  /*
   * This is the  HMM model with the probability of NULL
   * conditioned on left and right non-null context
   *
   */

  public void em_loop_mnull(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0, prob, probF, pjump, pmax, denom, max_prob = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    TPHandler tpHandler;
    AlHandlerHMM1 alHandler;

    if (useStayGoProbs) {
      alHandler = new AlHandlerHMM1SG(sgTable, mask, aTablesHolder, aTable);

    } else {
      if (mask == 0) {
        alHandler = new AlHandlerHMM1(aTable);
      } else {
        alHandler = new AlHandlerHMM1Tags(aTablesHolder, mask);
      }
    }

    int numWords = 0, sent_no = 0;


    if (useFNull) {
      tpHandler = new TPHandlerNULL(tTable, fTable, 0);
    } else {


      if (eTags && fTags && useFTagsT) {
        tpHandler = new TPHandlerFE1(tTable);
      } else {
        if (eTags && useETagsT) {
          tpHandler = new TPHandlerEtags(tTable);
        } else {
          tpHandler = new TPHandler(tTable);
        }
      }

    }

    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      sent_no++;
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      int start = 0, inc = 1, bound;
      tpHandler.setPair(sentPair);
      alHandler.setPair(sentPair);
      int[] alignment = new int[m + 1];  //for f from 1 to m, the corresponding i in the viterbi alignment
      //now start collecting tables

      double[][] forward = new double[m + 2][l + 2]; // forward probabilities
      double[][] max = new double[m + 2][l + 2]; //maximum probability
      IntPair[][] argmax = new IntPair[m + 2][l + 2]; //maximizing previous state
      double[][] backward = new double[m + 1][l + 2];//backward probabilities
      double[][][][] sigma = new double[m + 1][m + 1][l + 1][l + 1]; // these are sigma probabilities
      bound = 2 * l;

      forward[0][0] = 1; // the rest are 0, starting in state 0
      max[0][0] = 1; // the rest are 0, starting in state 0
      backward[m + 1][l + 1] = 1; // the rest are zero


      //first collect the sigmas

      for (start = 0; start <= m - 1; start++) {

        for (int end = start + 2; end <= m + 1; end++) {
          //the zeros are from start+1 to end-1 i.e. end-start-1
          for (int i_prev = 0; i_prev <= l; i_prev++) {

            alignment[start] = i_prev;

            double p_zeros = alHandler.getProb(l + i_prev, start + 1, alignment);

            for (int i = 1; i <= l + 1; i++) { // this will be the connection of a-end
              alignment[end] = i;


              prob = 1;

              for (int j = start + 1; j <= end - 1; j++) {
                prob *= p_zeros;
                alignment[j] = 0;
              }

              for (int j = start + 1; j <= end - 1; j++) {
                prob *= tpHandler.getProb(0, j, alignment);
              }

              sigma[start][end - start - 1][i_prev][i] = prob;

            } //i

          } //i_prev

        } //end

      }// start


      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i = 1; i <= l; i++) {


          prob = tpHandler.getProb(i, j);


          start = 0;
          bound = l;
          inc = 1;

          for (int i_p = start; i_p <= bound; i_p += inc) {
            alignment[j - 1] = i_p;
            pjump = alHandler.getProb(i, j, alignment);
            forward[j][i] += forward[j - 1][i_p] * pjump * prob;
            pmax = max[j - 1][i_p] * pjump * prob;
            if (pmax > max[j][i]) {
              max[j][i] = pmax;
              argmax[j][i] = new IntPair(i_p, 0);
            }
            for (int s = 1; s <= j - 1; s++) {
              alignment[j - s - 1] = i_p;
              alignment[j - s] = 0;
              pjump = alHandler.getProb(i, j, alignment);
              //add also the possibility of s zeros before i
              forward[j][i] += forward[j - 1 - s][i_p] * pjump * prob * sigma[j - s - 1][s][i_p][i];
              pmax = max[j - 1 - s][i_p] * pjump * prob * sigma[j - s - 1][s][i_p][i];
              if (pmax > max[j][i]) {
                max[j][i] = pmax;
                argmax[j][i] = new IntPair(i_p, s);
              }
              //do the argmaxes later

            }//s


          }//i_p
        }//i

      }//j

      //now add the jump prob for end of sentence
      for (int i = 0; i <= l; i++) {
        alignment[m] = i;
        double jmp = alHandler.getProb(2 * l + 1, m + 1, alignment);
        forward[m][i] *= jmp;
        max[m][i] *= jmp;
        forward[m + 1][l + 1] += forward[m][i];

      }

      //now collect the backward probabilities

      for (int i = 0; i <= l; i++) {
        //initialize for jump to the end of sentence
        alignment[m] = i;
        backward[m][i] = alHandler.getProb(2 * l + 1, m + 1, alignment);

      }


      for (int j = m - 1; j >= 0; j--) {

        for (int i = 1; i <= l; i++) {

          for (int i_next = 1; i_next <= l + 1; i_next += 1) {

            prob = tpHandler.getProb(i_next, j + 1);
            alignment[j] = i;
            backward[j][i] += alHandler.getProb(i_next, j + 1, alignment) * backward[j + 1][i_next] * prob;

            for (int s = 1; s <= m - j; s++) {
              alignment[j + s] = 0;
              pjump = alHandler.getProb(i_next, j + s + 1, alignment);
              prob = tpHandler.getProb(i_next, j + s + 1);
              //add also the possibility of s zeros before i
              backward[j][i] += pjump * prob * backward[j + s + 1][i_next] * sigma[j][s][i][i_next];
              //do the argmaxes later

            }//s

          }//i_next

        }//i

      }//j

      //now backward[0][0] should be the total probability

      denom = backward[0][0];

      if (backward[0][0] == forward[m + 1][l + 1]) {

        System.out.println("Congrats ! That thing works!");
      }

      max_prob = 0;
      int max_i = 0;

      for (int i = 0; i <= 2 * l; i++) {
        if (max_prob < max[m][i]) {
          max_prob = max[m][i];
          max_i = i;
        }
      }

      if (denom < max_prob) {
        System.out.println(" denom is smaller than maxprob " + denom + " " + max_prob);
        //System.exit](-1);
      }

      //collect the viterbi alignment

      int[] viterbi_alignment = new int[m + 1];
      viterbi_alignment[m] = max_i;
      int jw = m - 1;
      while (jw > 0) {

        int dist = argmax[jw][viterbi_alignment[jw]].getTarget();
        viterbi_alignment[jw - 1 - dist] = argmax[jw][viterbi_alignment[jw]].getSource();
        jw -= dist - 1;

      }


      if (GlobalParams.dumpAlignments) {

        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, sent_no, max_prob);

      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain) {

        //increment the alignment counts
        int index;
        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index = 0;
            }
            probF = tpHandler.getProb(index, j);

            if (i == 0) {
              bound = 0;
            } else {
              bound = 2 * l;
            }
            if (i > l) {
              inc = l;
              start = i - l;
            } else {
              inc = 1;
              start = 0;
            }

            for (int i_p = start; i_p <= bound; i_p += inc) {
              alignment[j - 1] = i_p;
              prob = forward[j - 1][i_p] * alHandler.getProb(i, j, alignment) * probF * backward[j][i];
              alHandler.incCount(i, j, alignment, prob * val);

            }
          }//i
        }//j

        //now for j+1

        for (int i_p = 0; i_p <= 2 * l; i_p++) {
          alignment[m] = i_p;
          alHandler.incCount(2 * l + 1, m + 1, alignment, forward[m][i_p] * val);

        }

        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            //we need the posterior
            prob = forward[j][i] * backward[j][i];

            //increment the translation table counts
            index = i;
            if (i > l) {
              index = 0;
            }
            tpHandler.incCount(index, j, prob * val);


          }//i
        } //j


      } //in train

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * m;
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    perplexity = Math.exp(-cross_entropy / numWords);
    viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }


  public void em_loop_ftagsA(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0, prob, probF, pjump, pmax, denom, max_prob = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    int numWords = 0, index;
    WordEx eWord, fWord;
    IntPair tmpPair = new IntPair();
    ProbCountHolder[][] cache;
    double[][] sum; // forward probabilities
    double[][] max; //maximum probability
    int[][] argmax; //maximizing previous state
    double[][] backward;//backward probabilities
    int[] viterbi_alignment;
    int max_i, sent_no;
    ATable[] tables;

    sent_no = 0;
    max_i = 0;
    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      sent_no++;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      int start = 0, inc = 1, bound;

      cache = new ProbCountHolder[l + 1][m + 1];
      //put first all probabilities in the cache
      tables = new ATable[m];
      IntPair iP = new IntPair();
      iP.setSource(0);
      for (int j = 1; j <= m; j++) {
        fWord = sentPair.f.getWord(j);
        if (!useFTagsT) {
          tmpPair.setTarget(fWord.getWordId());
        } else {
          tmpPair.setTarget(fWord.getIndex());
        }
        iP.setTarget(fWord.getTagId());
        tables[j - 1] = this.aTablesHolder.get(iP);
        iP.setSource(fWord.getTagId());
        for (int i = 0; i <= l; i++) {
          eWord = sentPair.e.getWord(i);
          tmpPair.setSource(eWord.getIndex());
          cache[i][j] = tTable.get(tmpPair);
        }
      }


      //now start collecting tables

      sum = new double[m + 1][2 * l + 1];   //sum(j,i)=p(f1,...,fj,i|e)  , j=0..m
      max = new double[m + 1][2 * l + 1];   //max{aj-1}max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      argmax = new int[m + 1][2 * l + 1]; //argmax {aj-1} max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      backward = new double[m + 1][2 * l + 1]; // backward(j,i)=p(fj+1,..fm|aj=i) j=0..m
      bound = 2 * l;

      sum[0][0] = 1; // the rest are 0, starting in state 0
      max[0][0] = 1; // the rest are 0, starting in state 0

      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i = 0; i <= 2 * l; i++) {

          if (i > l) {
            index = 0;
          } else {
            index = i;
          }

          if (cache[index][j] == null) {
            prob = PROB_SMOOTH;
          } else {
            prob = cache[index][j].getProb();
            if (prob < PROB_SMOOTH) {
              prob = PROB_SMOOTH;
            }
          }

          if (i == 0) {
            bound = 0;
          } else {
            bound = 2 * l;
          }
          if (i > l) {
            inc = l;
            start = i - l;
          } else {
            inc = 1;
            start = 0;
          }

          for (int i_p = start; i_p <= bound; i_p += inc) {
            pjump = tables[j - 1].getProb(i, i_p, l);
            sum[j][i] += sum[j - 1][i_p] * pjump * prob;
            pmax = max[j - 1][i_p] * pjump * prob;
            if (pmax > max[j][i]) {
              max[j][i] = pmax;
              argmax[j][i] = i_p;
            }

          }//i_p
        }//i

      }//j

      //now collect the backward probabilities

      for (int i = 0; i <= 2 * l; i++) {
        backward[m][i] = 1;
      }

      for (int j = m - 1; j >= 0; j--) {

        for (int i = 0; i <= 2 * l; i++) {

          start = 1;
          bound = 2 * l;
          inc = 1;
          if (i == 0) {
            start = 0;
            bound = l;
          }
          //start=0;bound=2*l;inc=1;

          for (int i_next = start; i_next <= bound; i_next += inc) {

            if (i_next > l) {
              index = 0;
              if ((i_next != i) && (i_next != (i + l))) {
                continue;
              }
            } else {
              index = i_next;
            }

            if (cache[index][j + 1] == null) {
              prob = PROB_SMOOTH;
            } else {
              prob = cache[index][j + 1].getProb();
              if (prob < PROB_SMOOTH) {
                prob = PROB_SMOOTH;
              }
            }
            backward[j][i] += tables[j].getProb(i_next, i, l) * backward[j + 1][i_next] * prob;


          }//i_next


        }//i

      }//j

      //now backward[0][0] should be the total probability

      denom = backward[0][0];
      max_prob = 0;

      for (int i = 0; i <= 2 * l; i++) {
        if (max_prob < max[m][i]) {
          max_i = i;
          max_prob = max[m][i];
        }
      }

      if (denom < max_prob) {
        System.out.println(" denom is smaller than maxprob " + denom + " " + max_prob);
        System.exit(-1);
      }


      //collect the viterbi alignment

      viterbi_alignment = new int[m + 1];
      viterbi_alignment[m] = max_i;
      for (int j = m; j > 0; j--) {
        viterbi_alignment[j - 1] = argmax[j][viterbi_alignment[j]];

      }


      if (GlobalParams.dumpAlignments) {

        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, sent_no, max_prob);


      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain) {

        //increment the alignment counts
        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index -= l;
            }
            if (cache[index][j] == null) {
              probF = PROB_SMOOTH;
            } else {
              probF = cache[index][j].getProb();
              if (probF < PROB_SMOOTH) {
                probF = PROB_SMOOTH;
              }
            }

            if (i == 0) {
              bound = 0;
            } else {
              bound = 2 * l;
            }
            if (i > l) {
              inc = l;
              start = i - l;
            } else {
              inc = 1;
              start = 0;
            }

            for (int i_p = start; i_p <= bound; i_p += inc) {
              prob = sum[j - 1][i_p] * tables[j - 1].getProb(i, i_p, l) * probF * backward[j][i];
              tables[j - 1].incCount(i, i_p, l, prob * val);

            }
          }//i
        }//j
        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            //we need the posterior
            prob = sum[j][i] * backward[j][i];

            //increment the translation table counts
            index = i;
            if (i > l) {
              index -= l;
            }
            if (cache[index][j] == null) {
              tmpPair.setTarget(sentPair.f.getWord(j).getWordId());
              tmpPair.setSource(sentPair.e.getWord(index).getIndex());
              tTable.incCount(tmpPair, prob * val, true);
            } else {
              cache[index][j].incCount(val * prob);
            }

          }//i
        } //j


      } //in train

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * m;
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    perplexity = Math.exp(-cross_entropy / numWords);
    viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }


  public void em_loop_etags(boolean inTrain, PrintStream alStream) {
  }


  public void em_loop_etagsA(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0, prob, probF, pjump, pmax, denom, max_prob = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    int numWords = 0, index;
    WordEx eWord, fWord;
    IntPair tmpPair = new IntPair();
    ProbCountHolder[][] cache;
    double[][] sum; // forward probabilities
    double[][] max; //maximum probability
    double[][] argmax; //maximizing previous state
    double[][] backward;//backward probabilities
    ATable[] tables;
    ATable tmpATable = null;
    double unifunknown = 1 / (double) (SentenceHandler.sTableF.getMaxSimpleIds());

    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      int start = 0, inc = 1, bound;

      cache = new ProbCountHolder[l + 1][m + 1];
      //put first all probabilities in the cache
      tables = new ATable[l];
      IntTriple iP = new IntTriple();
      iP.setSource(0);

      //collect the translation probabilities in the cache
      //discard the source tags and the target tags if any

      for (int j = 1; j <= m; j++) {
        fWord = sentPair.f.getWord(j);
        tmpPair.setTarget(fWord.getWordId());
        for (int i = 0; i <= l; i++) {
          eWord = sentPair.e.getWord(i);
          tmpPair.setSource(eWord.getWordId());
          cache[i][j] = tTable.get(tmpPair);
        }
      }

      //cache the alignment probabilities tables in the array tables


      iP.setMiddle(sentPair.e.getWord(1).getTagId());
      for (int i = 2; i <= l + 1; i++) {
        int target = 0;
        if (i != (l + 1)) {
          target = sentPair.getSource().getWord(i).getTagId();
          iP.setTarget(0);
        }
        tables[i - 2] = aTablesHolder.get(iP);
        //shuffle them for next time
        iP.setSource(iP.getMiddle());
        //iP.setSource(0);
        iP.setMiddle(target);

      }

      //now start collecting tables

      sum = new double[m + 1][2 * l + 1];   //sum(j,i)=p(f1,...,fj,i|e)  , j=0..m
      max = new double[m + 1][2 * l + 1];   //max{aj-1}max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      argmax = new double[m + 1][2 * l + 1]; //argmax {aj-1} max(j-1,aj-1)*p(i|aj-1)*p(fj|i)
      backward = new double[m + 1][2 * l + 1]; // backward(j,i)=p(fj+1,..fm|aj=i) j=0..m
      bound = 2 * l;

      sum[0][0] = 1; // the rest are 0, starting in state 0
      max[0][0] = 1; // the rest are 0, starting in state 0

      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i = 0; i <= 2 * l; i++) {

          if (i > l) {
            index = 0;
          } else {
            index = i;
          }

          if (cache[index][j] == null) {
            prob = (sentPair.getSource().getWord(index).getCount() == 0 ? unifunknown : PROB_SMOOTH);
          } else {
            prob = cache[index][j].getProb();
            if (prob < PROB_SMOOTH) {
              prob = PROB_SMOOTH;
            }
          }

          if (i == 0) {
            bound = 0;
          } else {
            bound = 2 * l;
          }
          if (i > l) {
            inc = l;
            start = i - l;
          } else {
            inc = 1;
            start = 0;
          }

          for (int i_p = start; i_p <= bound; i_p += inc) {

            if (i_p == 0) {
              pjump = aTable.getProb(i, i_p, l);
            } else {
              if (i_p > l) {
                pjump = tables[i_p - l - 1].getProb(i, i_p, l);
              } else {
                pjump = tables[i_p - 1].getProb(i, i_p, l);
              }

            }
            sum[j][i] += sum[j - 1][i_p] * pjump * prob;
            pmax = max[j - 1][i_p] * pjump * prob;
            if (pmax > max[j][i]) {
              max[j][i] = pmax;
              argmax[j][i] = i_p;
            }

          }//i_p
        }//i

      }//j

      //now collect the backward probabilities

      for (int i = 0; i <= 2 * l; i++) {
        backward[m][i] = 1;
      }

      for (int j = m - 1; j >= 0; j--) {

        for (int i = 0; i <= 2 * l; i++) {

          start = 1;
          bound = 2 * l;
          inc = 1;
          if (i == 0) {
            start = 0;
            bound = l;
          }
          //start=0;bound=2*l;inc=1;

          for (int i_next = start; i_next <= bound; i_next += inc) {

            if (i_next > l) {
              index = 0;
              if ((i_next != i) && (i_next != (i + l))) {
                continue;
              }
            } else {
              index = i_next;
            }
            if (cache[index][j + 1] == null) {
              prob = (sentPair.getSource().getWord(index).getCount() == 0 ? unifunknown : PROB_SMOOTH);
            } else {
              prob = cache[index][j + 1].getProb();
              if (prob < PROB_SMOOTH) {
                prob = PROB_SMOOTH;
              }
            }
            tmpATable = aTable;
            if (i > 0) {
              tmpATable = (i > l ? tables[i - l - 1] : tables[i - 1]);
            }

            backward[j][i] += tmpATable.getProb(i_next, i, l) * backward[j + 1][i_next] * prob;

          }//i_next

        }//i

      }//j

      //now backward[0][0] should be the total probability

      denom = backward[0][0];
      max_prob = 0;

      for (int i = 0; i <= 2 * l; i++) {
        if (max_prob < max[m][i]) {
          max_prob = max[m][i];
        }
      }

      if (denom < max_prob) {
        System.out.println(" denom is smaller than maxprob " + denom + " " + max_prob);
        System.exit(-1);
      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain) {

        //increment the alignment counts
        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index = 0;
            }
            if (cache[index][j] == null) {
              probF = (sentPair.getSource().getWord(index).getCount() == 0 ? unifunknown : PROB_SMOOTH);
            } else {
              probF = cache[index][j].getProb();
              if (probF < PROB_SMOOTH) {
                probF = PROB_SMOOTH;
              }
            }

            if (i == 0) {
              bound = 0;
            } else {
              bound = 2 * l;
            }
            if (i > l) {
              inc = l;
              start = i - l;
            } else {
              inc = 1;
              start = 0;
            }

            for (int i_p = start; i_p <= bound; i_p += inc) {
              tmpATable = aTable;
              if (i_p > 0) {
                tmpATable = (i_p > l ? tables[i_p - l - 1] : tables[i_p - 1]);
              }

              prob = sum[j - 1][i_p] * tmpATable.getProb(i, i_p, l) * probF * backward[j][i];
              tmpATable.incCount(i, i_p, l, prob * val);
            }
          }//i
        }//j
        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            //we need the posterior
            prob = sum[j][i] * backward[j][i];

            //increment the translation table counts
            index = i;
            if (i > l) {
              index = 0;
            }
            if (cache[index][j] == null) {
              tmpPair.setTarget(sentPair.f.getWord(j).getWordId());
              tmpPair.setSource(sentPair.e.getWord(index).getWordId());
              tTable.incCount(tmpPair, prob * val, true);
            } else {
              cache[index][j].incCount(val * prob);
            }

          }//i
        } //j


      } //in train

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * m;
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    perplexity = Math.exp(-cross_entropy / numWords);
    viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }





  /*
   * This is the simplest HMM model where there is
   * no dependency on any tags. Here I also incorporate stay/go probabilities
   * dependent on words
   */

  public void em_loop_sg(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    int numWords = 0, sent_no = 0;
    IntPair tmpPair = new IntPair();
    ProbCountHolder[] cacheS; // caching the stay go probabilities
    ProbCountHolder[] cacheG;

    double unifunknown = 1 / (double) (SentenceHandler.sTableF.getMaxSimpleIds());

    cacheG = new ProbCountHolder[corpus.getMaxLength() + 1];
    cacheS = new ProbCountHolder[corpus.getMaxLength() + 1];


    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      sent_no++;
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      int start = 0, inc = 1, bound;

      ProbCountHolder[][] cache = new ProbCountHolder[l + 1][m + 1];
      //put first all probabilities in the cache
      for (int j = 1; j <= m; j++) {
        WordEx fWord = sentPair.f.getWord(j);
        if (GlobalParams.tagsOnly) {
          tmpPair.setTarget(fWord.getTagId());
        } else {

          if (useFTagsT) {
            tmpPair.setTarget(fWord.getIndex());
          } else {
            tmpPair.setTarget(fWord.getWordId());
          }
        }
        for (int i = 0; i <= l; i++) {
          WordEx eWord = sentPair.e.getWord(i);
          if (GlobalParams.tagsOnly) {
            tmpPair.setSource(eWord.getTagId());
          } else {
            tmpPair.setSource(eWord.getIndex());
          }
          if (j == 1) {
            cacheS[i] = sgTable.getEntryStay(tmpPair.getSource());
            cacheG[i] = sgTable.getEntryGo(tmpPair.getSource());
          }
          cache[i][j] = tTable.get(tmpPair);
        }
      }


      //now start collecting tables

      double[][] sum = new double[m + 1][2 * l + 1]; // forward probabilities
      double[][] max = new double[m + 1][2 * l + 1]; //maximum probability
      int[][] argmax = new int[m + 1][2 * l + 1]; //maximizing previous state
      double[][] backward = new double[m + 1][2 * l + 1];//backward probabilities
      bound = 2 * l;

      sum[0][0] = 1; // the rest are 0, starting in state 0
      max[0][0] = 1; // the rest are 0, starting in state 0

      int index;
      double prob;
      double pjump;
      for (int j = 1; j <= m; j++) {
        //collect the sum, the max and the argmax
        for (int i = 0; i <= 2 * l; i++) {

          if (i > l) {
            index = 0;
          } else {
            index = i;
          }

          if (cache[index][j] == null) {

            prob = (sentPair.getSource().getWord(index).getCount() == 0 ? unifunknown : PROB_SMOOTH);

          } else {
            prob = cache[index][j].getProb();
            if (prob < PROB_SMOOTH) {
              prob = PROB_SMOOTH;
            }
          }

          if (i == 0) {
            bound = 0;
          } else {
            bound = 2 * l;
          }
          if (i > l) {
            inc = l;
            start = i - l;
          } else {
            inc = 1;
            start = 0;
          }

          for (int i_p = start; i_p <= bound; i_p += inc) {

            if (i_p == 0) {
              pjump = aTable.getProb(i, i_p, l);
            } else {

              if (i == i_p) {//stay
                pjump = cacheS[index].getProb();

              } else { //go
                pjump = cacheG[i_p > l ? 0 : i_p].getProb() * aTable.getProb(i, i_p, l);

              }
            }

            sum[j][i] += sum[j - 1][i_p] * pjump * prob;
            double pmax = max[j - 1][i_p] * pjump * prob;
            if (pmax > max[j][i]) {
              max[j][i] = pmax;
              argmax[j][i] = i_p;
            }

          }//i_p
        }//i

      }//j

      //now collect the backward probabilities

      for (int i = 0; i <= 2 * l; i++) {
        backward[m][i] = 1;
      }

      for (int j = m - 1; j >= 0; j--) {

        for (int i = 0; i <= 2 * l; i++) {

          start = 1;
          bound = 2 * l;
          inc = 1;
          if (i == 0) {
            start = 0;
            bound = l;
          }
          //start=0;bound=2*l;inc=1;

          for (int i_next = start; i_next <= bound; i_next += inc) {

            if (i_next > l) {
              index = 0;
              if ((i_next != i) && (i_next != (i + l))) {
                continue;
              }
            } else {
              index = i_next;
            }

            if (cache[index][j + 1] == null) {
              prob = (sentPair.getSource().getWord(index).getCount() == 0 ? unifunknown : PROB_SMOOTH);
            } else {
              prob = cache[index][j + 1].getProb();
              if (prob < PROB_SMOOTH) {
                prob = PROB_SMOOTH;
              }
            }
            double pjumpNext = 0;
            if (i == 0) {
              pjumpNext = aTable.getProb(i_next, i, l);
            } else {
              if (i_next == i) {
                pjumpNext = cacheS[index].getProb();
              } else {
                pjumpNext = cacheG[i > l ? 0 : i].getProb() * aTable.getProb(i_next, i, l);

              }
            }

            backward[j][i] += pjumpNext * backward[j + 1][i_next] * prob;
          }//i_next

        }//i

      }//j

      //now backward[0][0] should be the total probability

      double denom = backward[0][0];
      double max_prob = 0.0;
      int max_i = 0;

      for (int i = 0; i <= 2 * l; i++) {
        if (max_prob < max[m][i]) {
          max_prob = max[m][i];
          max_i = i;
        }
      }

      double sumprobs = 0;
      for (int i = 0; i <= 2 * l; i++) {
        sumprobs += sum[m][i];
      }

      if (Math.abs(sumprobs - denom) > 0.0001) {
        System.out.println("Probabilities do not add up " + sumprobs + " " + denom);
        System.exit(0);
      }

      if (denom < max_prob) {
        System.out.println(" denom is smaller than maxprob " + denom + " " + max_prob);
        System.exit(-1);
      }

      //collect the viterbi alignment

      int[] viterbi_alignment = new int[m + 1];  //for f from 1 to m, the corresponding i in the viterbi alignment
      viterbi_alignment[m] = max_i;
      for (int j = m; j > 0; j--) {
        viterbi_alignment[j - 1] = argmax[j][viterbi_alignment[j]];
      }

      if (GlobalParams.dumpAlignments) {
        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, sent_no, max_prob);
      }

      //now again, this time incrementing counts and computing perplexity
      double val = count / denom;

      if (inTrain) {

        //increment the alignment counts
        for (int j = 1; j <= m; j++) {
          double totalProb = 0;

          for (int i = 0; i <= 2 * l; i++) {
            index = i;
            if (i > l) {
              index = 0;
            }
            double probF;
            if (cache[index][j] == null) {
              probF = (sentPair.getSource().getWord(index).getCount() == 0 ? unifunknown : PROB_SMOOTH);
            } else {
              probF = cache[index][j].getProb();
              if (probF < PROB_SMOOTH) {
                probF = PROB_SMOOTH;
              }
            }

            if (i == 0) {
              bound = 0;
            } else {
              bound = 2 * l;
            }
            if (i > l) {
              inc = l;
              start = i - l;
            } else {
              inc = 1;
              start = 0;
            }

            for (int i_p = start; i_p <= bound; i_p += inc) {

              if (i_p == 0) {
                pjump = aTable.getProb(i, i_p, l);
              } else {
                if (i_p == i) {
                  pjump = cacheS[index].getProb();
                } else {
                  pjump = cacheG[i_p > l ? 0 : i_p].getProb() * aTable.getProb(i, i_p, l);
                }
              }

              prob = sum[j - 1][i_p] * pjump * probF * backward[j][i];
              totalProb += prob;

              if ((i_p == 0) || (i != i_p)) {
                aTable.incCount(i, i_p, l, prob * val);
              }

              if ((i == i_p) && (i_p > 0)) {
                //increment the stay probability for index
                cacheS[index].incCount(prob * val);
              }

              if ((i_p > 0) && (i != i_p)) {
                //increment the go probabilities for i_p
                cacheG[i_p > l ? 0 : i_p].incCount(prob * val);
              }
            }
          }//i
          //System.out.println(" total "+totalProb+" denom "+denom);
        }//j
        //increment the translation probability counts

        for (int j = 1; j <= m; j++) {
          for (int i = 0; i <= 2 * l; i++) {
            //we need the posterior
            prob = sum[j][i] * backward[j][i];

            //increment the translation table counts
            index = i;
            if (i > l) {
              index = 0;
            }
            if (cache[index][j] == null) {
              tmpPair.setTarget(sentPair.f.getWord(j).getIndex());
              tmpPair.setSource(sentPair.e.getWord(index).getIndex());
              tTable.incCount(tmpPair, prob * val, true);
            } else {
              cache[index][j].incCount(val * prob);
            }

          }//i
        } //j

      } //in train

      cross_entropy_sent += Math.log(denom);
      viterbi_cross_entropy_sent += Math.log(max_prob);

      double pml = Perplexity.getProb(m, l);
      numWords += count * m;
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);

    }// while next pair

    perplexity = Math.exp(-cross_entropy / numWords);
    viterbi_perplexity = Math.exp(-viterbi_cross_entropy / numWords);
    if (inTrain) {
      System.out.println("Training set results");
    } else {
      System.out.println("Test set results ");
    }
    System.out.println("Perplexity " + perplexity);
    System.out.println("Viterbi perplexity " + viterbi_perplexity);
  }


}
