package edu.stanford.nlp.mt.train.hmmalign;

import java.util.StringTokenizer;

/**
 * Reads in the corpus both training and test and stores them in buffers
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class SentenceHandler {
  private String trainFilename;
  private String testFilename;
  private SentencePair[] bufferTrain;
  private SentencePair[] bufferTest;
  private int numPairsTrain;
  private int numPairsTest;
  private int currentPairTrain;
  private int currentPairTest;
  private boolean fTags; //are there tags in the target strings, by default no
  private boolean eTags; // are there tags in the source string, by default no
  private boolean useETags = false;
  private boolean useFTags = false;
  static char UNDRSCR = '_';
  static SymbolTable sTableE;
  static SymbolTable sTableF;
  private int MAX_LENGTH;
  int mask;

  public SentenceHandler(String trainFilename, String testFilename) {
    this.trainFilename = trainFilename;
    this.testFilename = testFilename;
    init();
  }


  public SentenceHandler(String trainFilename, String testFilename, boolean eTags, boolean fTags, boolean useETags, boolean useFTags) {
    this.trainFilename = trainFilename;
    this.testFilename = testFilename;
    this.useETags = useETags;
    this.useFTags = useFTags;
    this.eTags = eTags;
    this.fTags = fTags;
    init();
  }

  /*Read the sentences from the corpus into the Buffer*/

  public void init() {

    sTableE = new SymbolTable();
    sTableF = new SymbolTable();
    try {

      //count how many pairs are there first in the training set, to make the buffer
      InFile in = new InFile(trainFilename);
      while (in.readLine() != null) {
        numPairsTrain++;
        in.readLine();
        in.readLine();
      }

      System.out.println("Number of pairs train" + numPairsTrain);
      bufferTrain = new SentencePair[numPairsTrain];
      read(true);

      // count how many pairs are there first in the test set, to make the buffer
      in = new InFile(testFilename);
      while (in.readLine() != null) {
        numPairsTest++;
        in.readLine();
        in.readLine();
      }

      System.out.println("Number of pairs test" + numPairsTest);
      bufferTest = new SentencePair[numPairsTest];
      read(false);

      sTableE.reorganizeTable();
      sTableF.reorganizeTable();
      if (!GlobalParams.windows) {
        sTableE.readDictionary(trainFilename.substring(0, trainFilename.lastIndexOf('/') + 1) + "e.vcb");
        sTableF.readDictionary(trainFilename.substring(0, trainFilename.lastIndexOf('/') + 1) + "f.vcb");
      } else {
        sTableE.readDictionary(trainFilename.substring(0, trainFilename.lastIndexOf('\\') + 1) + "e.vcb");
        sTableF.readDictionary(trainFilename.substring(0, trainFilename.lastIndexOf('\\') + 1) + "f.vcb");

      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }


  public boolean eHasTags() {
    return useETags;
  }

  public boolean fHasTags() {
    return useFTags;
  }


  private void read(boolean train) {
    try {

      int currentPair = 0;
      String filename = this.testFilename;
      if (train) {
        filename = this.trainFilename;
      }
      String cLine, eLine, fLine, token; //count, english sentence, french sentence, next token
      InFile in = new InFile(filename);
      currentPair = 0;
      Sentence e, f;
      WordEx[] wordsE;
      WordEx[] wordsF;
      WordEx wordTmp;
      int wordId, tagId, index, numWE, numWF, count;
      StringTokenizer stF, stE;
      while ((cLine = in.readLine()) != null) {
        //System.out.println(currentPair);
        eLine = in.readLine();
        fLine = in.readLine();
        count = Integer.parseInt(cLine);
        stE = new StringTokenizer(eLine);
        numWE = stE.countTokens();
        stF = new StringTokenizer(fLine);
        numWF = stF.countTokens();
        wordsE = new WordEx[numWE + 1];
        if (numWE > MAX_LENGTH) {
          MAX_LENGTH = numWE;
        }
        wordsE[0] = sTableE.empty;
        wordsE[0].incCount(count);
        for (int i = 1; i <= numWE; i++) {
          wordId = 0;
          tagId = 0;
          token = stE.nextToken();
          if (eTags) {
            index = token.indexOf(UNDRSCR);
            wordId = Integer.parseInt(token.substring(0, index));
            tagId = Integer.parseInt(token.substring(index + 1));
            if (!useETags) {
              tagId = 0;
            }
            wordTmp = new WordEx(wordId, tagId);

          } else {
            wordId = Integer.parseInt(token);
            wordTmp = new WordEx(wordId, 0);
          }

          wordTmp = sTableE.getEntry(wordTmp);
          if (train) {
            wordTmp.incCount(count);
          }
          wordsE[i] = wordTmp;
          // if this was a word with a tag, make sure we create an entry for the word only and increment its count

          if (!wordTmp.isSimple()) {
            wordTmp = new WordEx(wordId, 0);
            wordTmp = sTableE.getEntry(wordTmp);
            if (train) {
              wordTmp.incCount(count);
            }

            //now create an entry for the tag only, if the word was not simple
            wordTmp = new WordEx(0, tagId);
            wordTmp = sTableE.getEntry(wordTmp);
            if (train) {
              wordTmp.incCount(count);
            }
          }// if not simple

        }//end for i

        wordsF = new WordEx[numWF + 1];
        wordsF[0] = sTableF.empty;
        wordsF[0].incCount(count);
        for (int i = 1; i <= numWF; i++) {
          token = stF.nextToken();
          wordId = tagId = 0;
          if (fTags) {
            index = token.indexOf(UNDRSCR);
            wordId = Integer.parseInt(token.substring(0, index));
            tagId = Integer.parseInt(token.substring(index + 1));
            if (!useFTags) {
              tagId = 0;
            }
            wordTmp = new WordEx(wordId, tagId);
          } else {
            wordId = Integer.parseInt(token);
            wordTmp = new WordEx(wordId, 0);
          }

          wordTmp = sTableF.getEntry(wordTmp);
          if (train) {
            wordTmp.incCount(count);
          }
          wordsF[i] = wordTmp;
          // if this was a word with a tag, make sure we create an entry for the word only and increment its count

          if (!wordTmp.isSimple()) {
            wordTmp = new WordEx(wordId, 0);
            wordTmp = sTableF.getEntry(wordTmp);
            if (train) {
              wordTmp.incCount(count);
            }

            //now create an entry for the tag only, if the word was not simple
            wordTmp = new WordEx(0, tagId);
            wordTmp = sTableF.getEntry(wordTmp);
            if (train) {
              wordTmp.incCount(count);
            }
          }// if not simple

        }// for i on the french side

        e = new Sentence(wordsE);
        f = new Sentence(wordsF);
        if (train) {
          bufferTrain[currentPair] = new SentencePair(count, e, f);
        } else {
          bufferTest[currentPair] = new SentencePair(count, e, f);
        }
        currentPair++;

      }

      rewind(train);


    } catch (Exception e) {
      e.printStackTrace();
    }

  }


  public void rewind(boolean train) {
    if (train) {
      currentPairTrain = 0;
    } else {
      currentPairTest = 0;
    }
  }


  public SentencePair getNextPair(boolean train) {
    if (train) {
      return getNextPairTrain();
    } else {
      return getNextPairTest();
    }

  }

  public SentencePair getNextPairTrain() {
    if (currentPairTrain == (numPairsTrain)) {
      rewind(true);
      return null;
    } else {
      currentPairTrain++;
      return bufferTrain[currentPairTrain - 1];
    }

  }


  public SentencePair getNextPairTest() {
    if (currentPairTest == (numPairsTest)) {
      rewind(false);
      return null;
    } else {
      currentPairTest++;
      return bufferTest[currentPairTest - 1];
    }

  }


  public int getNumPairs(boolean train) {
    return (train ? numPairsTrain : numPairsTest);
  }


  public void print(boolean train) {
    rewind(train);
    SentencePair next;
    if (train) {
      while ((next = getNextPairTrain()) != null) {
        next.print();
      }
    } else {
      while ((next = getNextPairTest()) != null) {
        next.print();
      }

    }
  }


  public int getMaxLength() {
    return MAX_LENGTH;
  }


  public static void main(String[] args) {

    //parse the arguments
    String train = null, test = null;
    boolean fTags = false, eTags = false;
    int MODEL1_ITERS = 4, HMM_ITERS = 5;
    int startArgs = 0;
    int mask = 0;
    boolean useFTags = false, useETags = false;
    String ttFile = null;
    String atFile = null;
    HMM modelHMM;
    TTable startTTable;
    ATable startATable = null;
    Model1 model1;

    while (startArgs < args.length) {

      if (args[startArgs].equals("-train")) {
        train = args[++startArgs];
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-test")) {
        test = args[++startArgs];
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-m1")) {
        MODEL1_ITERS = Integer.parseInt(args[++startArgs]);
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-hmm")) {
        HMM_ITERS = Integer.parseInt(args[++startArgs]);
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-st")) {
        eTags = true;
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-tt")) {
        fTags = true;
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-utt")) {
        useFTags = true;
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-ust")) {
        useETags = true;
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-m")) {
        mask = Integer.parseInt(args[++startArgs]);
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-ttfile")) {
        ttFile = args[++startArgs];
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-atfile")) {
        atFile = args[++startArgs];
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-sg")) {
        HMM.useStayGoProbs = true;
        startArgs++;
        continue;
      }
      if (args[startArgs].equals("-tri")) {
        HMM.trigram = true;
        startArgs++;
        continue;
      }


    }


    SentenceHandler sH = new SentenceHandler(train, test, eTags, fTags, useETags, useFTags);
    sH.mask = mask;
    startTTable = new TTable(true);
    if (ttFile != null) {
      startTTable.read(ttFile);
      model1 = new Model1(sH, startTTable, MODEL1_ITERS);
    } else {
      model1 = new Model1(sH, MODEL1_ITERS);
    }

    if (atFile != null) {

      if (HMM.trigram) {
        if (HMM.eQClasses) {
          startATable = new ATableHMM2EQ();
          ((ATableHMM2EQ) startATable).read(atFile);
        } else {
          startATable = new ATableHMM2();
          ((ATableHMM2) startATable).read(atFile);
        }
      }//trigram
      else {
        startATable = new ATableHMM();
        ((ATableHMM) startATable).read(atFile);


      }

    }


    if (atFile != null) {
      modelHMM = new HMM(sH, HMM_ITERS, model1.getTTable(), model1.getFTable(), startATable, mask, 0);

    } else {
      modelHMM = new HMM(sH, HMM_ITERS, model1.getTTable(), model1.getFTable(), mask, 0);

    }


    new Model1(sH, model1.getTTable(), 1);


    String transprobsFile = GlobalParams.resultPath + "tt.final";
    model1.getTTable().save(transprobsFile);
    transprobsFile = GlobalParams.resultPath + "tt.nm.final";
    model1.getTTable().saveNames(transprobsFile);
    if (modelHMM.useFNull) {
      modelHMM.getFTable().saveNames(GlobalParams.resultPath + "ft.nm.final");
    }

  }


}
