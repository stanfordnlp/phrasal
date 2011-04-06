package edu.stanford.nlp.mt.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.mt.parser.Actions.ActionType;
import edu.stanford.nlp.parser.Parser;
import edu.stanford.nlp.trees.DependencyScoring;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.DependencyScoring.Score;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;

public class DepDAGParser implements Parser {

  private LinearClassifier<Action,String> classifier;
  private static final boolean VERBOSE = false;
  
  @Override
  public boolean parse(List<? extends HasWord> sentence) {
    return true;  // accept everything for now.
  }

  public static DepDAGParser trainModel(
      List<Structure> rawTrainData) {
    DepDAGParser parser = new DepDAGParser();

    List<Datum<Action, String>> extTrainData = extractTrainingData(rawTrainData);

    LinearClassifierFactory<Action,String> factory = new LinearClassifierFactory<Action,String>();
    // TODO: check options

    // Build a classifier
    parser.classifier = factory.trainClassifier(extTrainData);
    if(VERBOSE) parser.classifier.dump();
    
    return parser;
  }
  
  private static List<Datum<Action, String>> extractTrainingData(List<Structure> rawTrainData) {
    List<Datum<Action, String>> extracted = new ArrayList<Datum<Action, String>>();
    for(Structure struc : rawTrainData) {
      List<Action> actions = struc.getActionTrace();
      struc.resetIndex();
      for(Action act : actions) {
        Datum<Action, String> datum = extractFeature(act, struc);
        if(datum.asFeatures().size() > 0) {
          extracted.add(datum);
        }
        Actions.doAction(act, struc);
      }
    }
    return extracted;
  }

  private static Datum<Action, String> extractFeature(Action act, Structure s){
    // if act == null, test data
    if(s.getCurrentInputIndex() >= s.getInput().size()) return null;  // end of sentence
    List<String> features = DAGFeatureExtractor.extractFeatures(s);
    return new BasicDatum<Action, String>(features, act);
  }
  
  // for extracting features from test data (no gold Action given)
  private static Datum<Action, String> extractFeature(Structure s){
    return extractFeature(null, s);
  }
  
  public SemanticGraph getDependencyGraph(Structure s){    
    Datum<Action, String> d;
    while((d=extractFeature(s))!=null){
      Action nextAction;
      if(s.getStack().size()==0) nextAction = new Action(ActionType.SHIFT); 
      else nextAction = classifier.classOf(d);
      Actions.doAction(nextAction, s);
    }
    return s.dependencies;    
  }
  
  public static void main(String[] args) throws IOException{
    boolean testScorer = false;
    if(testScorer) {
      testScorer();
      return;
    }
    
    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-train-2011-01-13.conll";
    String devFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
//    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
//    String devFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/small_train.conll";
//    String devFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp2.conll";
//    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp.conll";
    
    System.err.println("read data....!");
    List<Structure> devData = ActionRecoverer.readTrainingData(devFile);
    List<Structure> trainData = ActionRecoverer.readTrainingData(trainingFile);

    System.err.println("train model...");
    DepDAGParser parser = trainModel(trainData);
    
    List<Collection<TypedDependency>> goldDeps = new ArrayList<Collection<TypedDependency>>();
    List<Collection<TypedDependency>> systemDeps = new ArrayList<Collection<TypedDependency>>();
    
    System.err.println("testing...");
    for(Structure s : devData){
      goldDeps.add(s.getDependencyGraph().typedDependencies());
      s.resetIndex();
      SemanticGraph graph = parser.getDependencyGraph(s);
      systemDeps.add(graph.typedDependencies());
    }
    
    System.err.println("scoring...");
    DependencyScoring goldScorer = DependencyScoring.newInstanceStringEquality(goldDeps);
    Score score = goldScorer.score(DependencyScoring.convertStringEquality(systemDeps));
    System.out.println(score.toString(true));
    System.err.println("done");
  }

  public static void testScorer() throws IOException {
    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
    String devFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
    List<Structure> devData = ActionRecoverer.readTrainingData(devFile);
//    List<Structure> trainData = ActionRecoverer.readTrainingData(trainingFile);

    List<Collection<TypedDependency>> goldDeps = new ArrayList<Collection<TypedDependency>>();
    List<Collection<TypedDependency>> systemDeps = new ArrayList<Collection<TypedDependency>>();
    Collection<TypedDependency> temp = new ArrayList<TypedDependency>();

    for(Structure s : devData){
      temp = s.getDependencyGraph().typedDependencies();
      goldDeps.add(s.getDependencyGraph().typedDependencies());
//      systemDeps.add(temp);
      temp = s.getDependencyGraph().typedDependencies();
      systemDeps.add(temp);
    }

    DependencyScoring goldScorer = DependencyScoring.newInstanceStringEquality(goldDeps);
    Score score = goldScorer.score(DependencyScoring.convertStringEquality(systemDeps));
    System.out.println(score.toString(true));
  }
  
}
