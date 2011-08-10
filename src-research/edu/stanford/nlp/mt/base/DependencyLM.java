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

  private final static String defaultDepLMFile = "/scr/heeyoung/corpus/dependencies/depCounts.txt";
  private final Counter<Sequence<String>>  counter;

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
    double countParent = counter.getCount(sequence.subsequence(0, sequence.size()-1));
    double countChildAndParent = counter.getCount(sequence);
    if(countParent!=0 && countChildAndParent!=0) {
      return Math.log(countChildAndParent/countParent);
    } else {
      return 0;   // TODO
    }
  }

  public DependencyLM() {
    this(defaultDepLMFile);
  }

  public DependencyLM(String depLMFile) {
    counter = new OpenAddressCounter<Sequence<String>>();
    for(String line : IOUtils.readLines(depLMFile)) {
      String[] split = line.split("\t");
      counter.incrementCount(new RawSequence(IStrings.toIStringArray(split[0].split(" "))), Double.parseDouble(split[1]));
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

    LanguageModel<IString> lm = new DependencyLM();

    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-train-2011-01-13.conll";
    //    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp3.conll";
    String countFile = "/scr/heeyoung/corpus/dependencies/depCounts.txt";

    List<Structure> trainData = ActionRecoverer.readTrainingData(trainingFile, null);
    Counter<String> counter = countDepLM(trainData);
    PrintWriter writer = IOUtils.getPrintWriter(countFile);

    for(String ngram : counter.keySet()) {
      StringBuilder sb = new StringBuilder();
      sb.append(ngram).append("\t").append(counter.getCount(ngram));
      writer.println(sb.toString());
    }
    writer.close();

  }
}
