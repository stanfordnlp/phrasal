package mt.hmmalign;


/* This class does Model1 training with EM
 * Reports perplexity on training and test data
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.FileOutputStream;
import java.io.PrintStream;

public class Model1 {
  private TTable tTable;
  private SentenceHandler corpus;
  private PTable pTable;
  private TTable fTable;
  static double PROB_SMOOTH;
  boolean eTags;
  boolean fTags;
  boolean verbose = true;
  boolean useCProbs = false;
  boolean useETagsT = GlobalParams.useETagsT;
  boolean useFTagsT = GlobalParams.useFTagsT;
  PrintStream alStream;
  String fileAlignment;
  boolean useFNull = false;

  public Model1() {
  }

  public Model1(SentenceHandler corpus, int numIterations) {
    this.corpus = corpus;
    eTags = corpus.eHasTags();
    fTags = corpus.fHasTags();
    PROB_SMOOTH = TTable.PROB_SMOOTH;
    if (useCProbs) {
      pTable = new PTable(corpus);
      pTable.initialize(.09);
    }
    initializeTable();
    init();
    train_test_loop(numIterations);
    if (useCProbs) {
      pTable.save(GlobalParams.resultPath + "/" + "cnc.final");
    }


  }


  public Model1(SentenceHandler corpus, TTable tTable, int numIterations) {
    this.corpus = corpus;
    eTags = corpus.eHasTags();
    fTags = corpus.fHasTags();
    this.tTable = tTable;
    PROB_SMOOTH = TTable.PROB_SMOOTH;
    if (useCProbs) {
      pTable = new PTable(corpus);
      pTable.initialize(.09);
    }
    init();
    train_test_loop(numIterations);
    if (useCProbs) {
      pTable.save(GlobalParams.resultPath + "/" + "cnc.final");
    }

  }


  public void init() {

    if (useFNull) {
      if (fTable == null) {
        fTable = new TTable(false);
      }
    }


  }


  public TTable getTTable() {
    return tTable;
  }

  public TTable getFTable() {
    return fTable;
  }

  public void train_test_loop(int numIterations) {
    //tTable.print();
    for (int it = 0; it < numIterations; it++) {
      //First iterate on the training set
      System.out.println("Model 1 iteration " + (it + 1));
      if (GlobalParams.dumpAlignments) {
        this.fileAlignment = GlobalParams.resultPath + "A.m1." + (it + 1);
        try {
          alStream = new PrintStream(new FileOutputStream(fileAlignment));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      em_loop(true, alStream);

      alStream.close();

      //tTable.print();
      tTable.normalizeTable(2);
      if (fTable != null) {
        fTable.normalizeTable(2);

      }
      if (useCProbs) {
        pTable.normalize();
      }
      if (verbose) {

        System.out.println("Average English word entropy " + tTable.getCondEntropy());
      }

      //Iterate on the test set
      if (GlobalParams.dumpAlignments) {
        this.fileAlignment = GlobalParams.resultPath + "At.m1." + (it + 1);
        try {
          alStream = new PrintStream(new FileOutputStream(fileAlignment));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      em_loop(false, alStream);
      alStream.close();


      if ((it + 1) % GlobalParams.saveFreq == 0) {
        ;//tTable.save(GlobalParams.resultPath+"tt.m1."+(it+1));
      } else {
        System.out.println(" didn't save b/e res is " + ((it + 1) % GlobalParams.saveFreq));
      }

    }

    tTable.save(GlobalParams.resultPath + "tt.m1.final");
    //tTable.print();
    System.out.println("Average English word entropy " + tTable.getCondEntropy());

  }


  public void initializeTable() {
    tTable = new TTable(true);
    SentencePair sentPair;
    int word_e, word_f;
    while ((sentPair = corpus.getNextPairTrain()) != null) {
      int count = sentPair.getCount();
      double uniform = count * (1 / (double) (sentPair.e.getLength()));
      for (int i = 0; i < sentPair.e.getLength(); i++) {
        if (eTags && useETagsT) {
          word_e = sentPair.getSource().getWord(i).getIndex();
        } else {
          word_e = sentPair.getSource().getWord(i).getWordId();
        }
        for (int j = 1; j < sentPair.getTarget().getLength(); j++) {
          word_f = sentPair.getTarget().getWord(j).getWordId();
          tTable.insert(word_e, word_f, 0, uniform);
          if (eTags && useETagsT) {
            tTable.insert(sentPair.getSource().getWord(i).getTagId(), word_f, 0, uniform);
          }

        } //j
      }//i


    }//while

    tTable.normalizeTable(2);
  }


  public void em_loop(boolean inTrain, PrintStream alStream) {
    double cross_entropy = 0, viterbi_cross_entropy = 0;
    double perplexity = 0, viterbi_perplexity = 0;
    SentencePair sentPair;
    int numWords = 0;
    int[] viterbi_alignment;
    int pair_no = 0;
    TPHandler tpHandler = null;
    AlHandler aHandler;


    if (useFNull) {

      if (fTags && useFTagsT) {

        if (eTags) {
          tpHandler = new TPHandlerNULL(tTable, fTable, 2);
        }
      } else {

        tpHandler = new TPHandlerNULL(tTable, fTable, 0);

      }


    } else {

      if (fTags && useFTagsT) {

        if (eTags) {
          tpHandler = new TPHandlerFE1(tTable);
        }
      } else {

        if (eTags && useETagsT) {

          tpHandler = new TPHandlerEtags(tTable);
        } else {
          tpHandler = new TPHandler(tTable);
        }
      }


    }
    if (useCProbs) {

      aHandler = new CHandler(pTable, false);

    } else {

      aHandler = new AlHandler();

    }


    while ((sentPair = corpus.getNextPair(inTrain)) != null) {
      int count = sentPair.getCount();
      int l = sentPair.e.getLength() - 1;
      int m = sentPair.f.getLength() - 1;
      double cross_entropy_sent = 0;
      double viterbi_cross_entropy_sent = 0;
      viterbi_alignment = new int[m + 1];
      double max_prob_total = 1;

      pair_no++;

      tpHandler.setPair(sentPair);
      aHandler.setPair(sentPair);

      for (int j = 1; j <= m; j++) {

        double denom = 0;
        double max_prob = 0;
        int max_i = 0;
        double prob = 0;

        for (int i = 0; i <= l; i++) {
          prob = tpHandler.getProb(i, j);
          prob *= aHandler.getProb(i, j);
          if (prob > max_prob) {
            max_prob = prob;
            max_i = i;
          }
          denom += prob;

        }
        viterbi_alignment[j] = max_i;

        //now again, this time incrementing counts and computing perplexity
        double val = count / denom;

        if (inTrain) {
          for (int i = 0; i <= l; i++) {
            prob = tpHandler.getProb(i, j);
            prob *= aHandler.getProb(i, j);
            tpHandler.incCount(i, j, val * prob);
            aHandler.incCount(i, j, prob * val);
          }

        }
        cross_entropy_sent += Math.log(denom);
        viterbi_cross_entropy_sent += Math.log(max_prob);
        max_prob_total *= max_prob;
        //max_prob_total/=(l+1);
      } //j
      double pml = Perplexity.getProb(m, l);
      numWords += count * m;
      cross_entropy += count * (pml + cross_entropy_sent);
      viterbi_cross_entropy += count * (pml + viterbi_cross_entropy_sent);
      //save the alignment to file if appropriate flag set
      if (GlobalParams.dumpAlignments && (!inTrain)) {

        Reports.printAlignToFile(sentPair, alStream, viterbi_alignment, pair_no, max_prob_total);

      }

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
