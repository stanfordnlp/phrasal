package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.PrintWriter;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.parser.DepDAGParser;
import edu.stanford.nlp.mt.parser.IncrementalTagger;
import edu.stanford.nlp.mt.parser.Structure;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.trees.TypedDependency;

public class DependencyLM implements LanguageModel<IString> {

  // Dependency LM based on words
  private final static String defaultDepWordLMFile = "/scr/heeyoung/corpus/dependencies/depWordSeqCounts.txt";
  // Dependency LM based on POS tags
  private final static String defaultDepPOSTagLMFile = "/scr/heeyoung/corpus/dependencies/depPOSTagSeqCounts.txt";

  private final static String defaultWordUnigramFile = "/scr/heeyoung/corpus/dependencies/wordUnigramCounts.txt";
  private final static String defaultPOSTagUnigramFile = "/scr/heeyoung/corpus/dependencies/posTagUnigramCounts.txt";

  private final Counter<Sequence<String>> wordSeqCounter;
  private final Counter<Sequence<String>> posTagSeqCounter;
  private final Counter<String> wordCounter;
  private final Counter<String> posTagCounter;

  private final static double SMOOTHING_DELTA = 1;

  public DependencyLM() {
    this(defaultDepWordLMFile, defaultDepPOSTagLMFile, defaultWordUnigramFile, defaultPOSTagUnigramFile);
  }

  public DependencyLM(String depWordLMFile, String depPOSTagLMFile, String wordUnigramFile, String posTagUnigramFile) {
    wordSeqCounter = new OpenAddressCounter<Sequence<String>>();
    if(depWordLMFile != null) {
      for(String line : IOUtils.readLines(depWordLMFile)) {
        String[] split = line.split("\t");
        wordSeqCounter.incrementCount(new RawSequence(IStrings.toIStringArray(split[0].split(" "))), Double.parseDouble(split[1]));
      }
    }
    posTagSeqCounter = new OpenAddressCounter<Sequence<String>>();
    if(depPOSTagLMFile != null) {
      for(String line : IOUtils.readLines(depPOSTagLMFile)) {
        String[] split = line.split("\t");
        posTagSeqCounter.incrementCount(new RawSequence(IStrings.toIStringArray(split[0].split(" "))), Double.parseDouble(split[1]));
      }
    }
    wordCounter = new OpenAddressCounter<String>();
    if(wordUnigramFile != null) {
      for(String line : IOUtils.readLines(wordUnigramFile)) {
        String[] split = line.split("\t");
        wordCounter.incrementCount(split[0], Double.parseDouble(split[1]));
      }
    }
    posTagCounter = new OpenAddressCounter<String>();
    if(posTagUnigramFile != null) {
      for(String line : IOUtils.readLines(posTagUnigramFile)) {
        String[] split = line.split("\t");
        posTagCounter.incrementCount(split[0], Double.parseDouble(split[1]));
      }
    }
  }

  @Override
  public IString getEndToken() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public IString getStartToken() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int order() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public boolean releventPrefix(Sequence<IString> sequence) {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean isUnseen(Sequence<IString> sequence) {
    return (wordSeqCounter.getCount(sequence)==0);
  }
  @Override
  public double score(Sequence<IString> sequence) {
    double countParent = wordSeqCounter.getCount(sequence.subsequence(0, sequence.size()-1));
    double countChildAndParent = wordSeqCounter.getCount(sequence);

    // add-delta smoothing
    return Math.log((countChildAndParent+SMOOTHING_DELTA*getUnigramProb(sequence.get(sequence.size()-1)))/(countParent+SMOOTHING_DELTA));
  }
  private double getUnigramProb(IString str) {
    return (wordCounter.getCount(str) + SMOOTHING_DELTA) / (wordCounter.totalCount() + wordCounter.keySet().size()*SMOOTHING_DELTA);
  }

  public double scorePOSLM(Sequence<IString> sequence) {
    double countParent = posTagSeqCounter.getCount(sequence.subsequence(0, sequence.size()-1));
    double countChildAndParent = posTagSeqCounter.getCount(sequence);
    // add-delta smoothing
    return Math.log((countChildAndParent+SMOOTHING_DELTA*getPOSTagUnigramProb(sequence.get(sequence.size()-1)))/(countParent+SMOOTHING_DELTA));
  }
  private double getPOSTagUnigramProb(IString str) {
    return (posTagSeqCounter.getCount(str) + SMOOTHING_DELTA) / (posTagSeqCounter.totalCount() + posTagSeqCounter.keySet().size()*SMOOTHING_DELTA);
  }

  /** to build dependency LM  */
  public void countDepLM(Structure s) {
    for (TypedDependency dep : s.getDependencies()) {
      String unigram = dep.gov().label().get(TextAnnotation.class).toLowerCase();
      String bigram = unigram + " " + dep.dep().label().get(TextAnnotation.class).toLowerCase();
      wordSeqCounter.incrementCount(new RawSequence(new String[]{unigram, dep.dep().label().get(TextAnnotation.class).toLowerCase()}));
      wordSeqCounter.incrementCount(new RawSequence(new String[]{unigram}));
    }
  }

  public void countDepPOSTagLM(Structure s) {
    for (TypedDependency dep : s.getDependencies()) {
      String unigram = dep.gov().label().get(PartOfSpeechAnnotation.class);
      String bigram = unigram + " " + dep.dep().label().get(PartOfSpeechAnnotation.class);

      posTagSeqCounter.incrementCount(new RawSequence(new String[]{unigram, dep.dep().label().get(PartOfSpeechAnnotation.class)}));
      posTagSeqCounter.incrementCount(new RawSequence(new String[]{unigram}));
    }
  }
  public void countWordUnigram(Structure s) {
    for(CoreLabel c : s.getInput()) {
      wordCounter.incrementCount(c.get(TextAnnotation.class).toLowerCase());
    }
  }

  private void countPOSTagUnigram(Structure s) {
    for(CoreLabel c : s.getInput()) {
      posTagCounter.incrementCount(c.get(PartOfSpeechAnnotation.class));
    }
  }

  public static void main(String[] args) throws IOException, ClassNotFoundException {

    DependencyLM lm2 = new DependencyLM();
    if(true) {
      System.err.println(lm2.wordCounter.getCount(",")/10000);
      System.err.println(lm2.posTagCounter);
      return;
    }

    //    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-train-2011-01-13.conll.transformed";
    //    List<Structure> trainData = ActionRecoverer.readTrainingData(trainingFile, null);

    String trainingFile = "/scr/heeyoung/mt/scr61/mt06.for_heeyoung/bitext/aligned.en";
//    String trainingFile = "/scr/heeyoung/mt/scr61/temp.txt";
    DependencyLM lm = new DependencyLM(null, null, null, null);

    DepDAGParser parser;
    String defaultParserModel = "/scr/heeyoung/mt/mtdata/parser/DAGparserModel.wolemma_lowercase_withQ2Q3.ser";
    parser = IOUtils.readObjectFromFile(defaultParserModel);
    parser.labelRelation = true;
    parser.extractTree = true;
    IncrementalTagger tagger = new IncrementalTagger();

    int sentCount = 0;
    for(String sent : IOUtils.readLines(trainingFile)){

      if(sentCount++ % 10000 == 0) {
        System.err.println(sentCount+" sentences parsed.");
      }
      
      Structure s = parser.parseSentence(sent, tagger);

      lm.countDepLM(s);
      lm.countDepPOSTagLM(s);
      lm.countWordUnigram(s);
      lm.countPOSTagUnigram(s);
    }

    //
    // write word LM
    //
    PrintWriter wordSeqWriter = IOUtils.getPrintWriter(defaultDepWordLMFile);

    for(Sequence<String> ngram : lm.wordSeqCounter.keySet()) {
      StringBuilder sb = new StringBuilder();
      sb.append(ngram).append("\t").append(lm.wordSeqCounter.getCount(ngram));
      wordSeqWriter.println(sb.toString());
    }
    wordSeqWriter.close();

    //
    // write POS tag LM
    //
    PrintWriter posTagSeqWriter = IOUtils.getPrintWriter(defaultDepPOSTagLMFile);

    for(Sequence<String> ngram : lm.posTagSeqCounter.keySet()) {
      StringBuilder sb = new StringBuilder();
      sb.append(ngram).append("\t").append(lm.posTagSeqCounter.getCount(ngram));
      posTagSeqWriter.println(sb.toString());
    }
    posTagSeqWriter.close();

    //
    // write word unigram
    //
    PrintWriter wordUnigramWriter = IOUtils.getPrintWriter(defaultWordUnigramFile);

    for(String unigram : lm.wordCounter.keySet()) {
      StringBuilder sb = new StringBuilder();
      sb.append(unigram).append("\t").append(lm.wordCounter.getCount(unigram));
      wordUnigramWriter.println(sb.toString());
    }
    wordUnigramWriter.close();

    //
    // write pos tag unigram
    //
    PrintWriter posTagUnigramWriter = IOUtils.getPrintWriter(defaultPOSTagUnigramFile);

    for(String tag : lm.posTagCounter.keySet()) {
      StringBuilder sb = new StringBuilder();
      sb.append(tag).append("\t").append(lm.posTagCounter.getCount(tag));
      posTagUnigramWriter.println(sb.toString());
    }
    posTagUnigramWriter.close();

  }

}
