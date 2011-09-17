package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.parser.ActionRecoverer;
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
      for(String line : IOUtils.readLines(depWordLMFile)) {
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

  /** to build dependency LM  */
  public static Counter<String> countDepLM(List<Structure> trainData) {
    Counter<String> counter = new OpenAddressCounter<String>();
    for(Structure s : trainData) {
      for (TypedDependency dep : s.getDependencies()) {
        String unigram = dep.gov().label().get(TextAnnotation.class).toLowerCase();
        String bigram = unigram + " " + dep.dep().label().get(TextAnnotation.class).toLowerCase();

        counter.incrementCount(bigram);
        counter.incrementCount(unigram);
      }
    }
    return counter;
  }

  public static void main(String[] args) throws IOException {

    //    LanguageModel<IString> lm = new DependencyLM();

    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-train-2011-01-13.conll";
    //    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp3.conll";

    List<Structure> trainData = ActionRecoverer.readTrainingData(trainingFile, null);
    Counter<String> counter = countDepLM(trainData);
    PrintWriter wordSeqWriter = IOUtils.getPrintWriter(defaultDepWordLMFile);

    for(String ngram : counter.keySet()) {
      StringBuilder sb = new StringBuilder();
      sb.append(ngram).append("\t").append(counter.getCount(ngram));
      wordSeqWriter.println(sb.toString());
    }
    wordSeqWriter.close();

  }
}
