package edu.stanford.nlp.mt.parser;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.mt.parser.Actions.ActionType;
import edu.stanford.nlp.mt.parser.DAGFeatureExtractor.RightSideFeatures;
import edu.stanford.nlp.parser.Parser;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.trees.DependencyScoring;
import edu.stanford.nlp.trees.DependencyScoring.Score;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

public class DepDAGParser implements Parser, Serializable {
  public static final boolean DEBUG = false;

  private static final long serialVersionUID = -5534972476741917367L;

  private LinearClassifier<ActionType,List<String>> actClassifier;
  private LinearClassifier<GrammaticalRelation,List<String>> labelClassifier;
  private static final boolean VERBOSE = false;

  //to reduce the total number of features for training, remove features appear less than 3 times
  private static final boolean REDUCE_FEATURES = true;

  private static RightSideFeatures rightFeatures;

  @Override
  public boolean parse(List<? extends HasWord> sentence) {
    return true;  // accept everything for now.
  }

  public static DepDAGParser trainModel(
      List<Structure> rawTrainData) {
    DepDAGParser parser = new DepDAGParser();

    // to reduce the total number of features for training, remove features appear less than 3 times
    Counter<List<String>> featureCounter = null;
    if(REDUCE_FEATURES) featureCounter = countFeatures(rawTrainData);

    GeneralDataset<ActionType, List<String>> actTrainData = new Dataset<ActionType, List<String>>();
    GeneralDataset<GrammaticalRelation, List<String>> labelTrainData = new Dataset<GrammaticalRelation, List<String>>();
    extractTrainingData(rawTrainData, actTrainData, labelTrainData, featureCounter);

    LinearClassifierFactory<ActionType,List<String>> actFactory = new LinearClassifierFactory<ActionType,List<String>>();
    LinearClassifierFactory<GrammaticalRelation,List<String>> labelFactory = new LinearClassifierFactory<GrammaticalRelation,List<String>>();
    // TODO: check options

    featureCounter = null;

    // Build a classifier
    parser.labelClassifier = labelFactory.trainClassifier(labelTrainData);
    parser.actClassifier = actFactory.trainClassifier(actTrainData);
    if(VERBOSE) {
      parser.actClassifier.dump();
      parser.labelClassifier.dump();
    }

    return parser;
  }

  private static Counter<List<String>> countFeatures(List<Structure> rawTrainData) {
    // TODO fix this!!
    Counter<List<String>> counter = new OpenAddressCounter<List<String>>();

    for(Structure struc : rawTrainData) {
      LinkedStack<Action> actions = struc.getActionTrace();
      struc.actionTrace = new LinkedStack<Action>();

      int offset = struc.input.size();
      Object[] acts = actions.peekN(actions.size());
      for(int i = acts.length-1 ; i >= 0 ; i--){
        Action act = (Action)acts[i];
        Datum<ActionType, List<String>> actDatum = extractActFeature(act.action, struc, null, offset);
        Datum<GrammaticalRelation, List<String>> labelDatum = extractLabelFeature(act.relation, act.action, actDatum, struc, null, offset);

        for(List<String> feature : labelDatum.asFeatures()) {
          counter.incrementCount(feature);
        }
        try {
          Actions.doAction(act, struc, offset);
          if(act.action==ActionType.SHIFT) offset--;
        } catch (RuntimeException e) {
          throw e;
        }
      }
      struc.dependencies = new LinkedStack<TypedDependency>();
      struc.stack = new LinkedStack<CoreLabel>();
    }
    return counter;
  }

  private static void extractTrainingData(
      List<Structure> rawTrainData,
      GeneralDataset<ActionType, List<String>> actTrainData,
      GeneralDataset<GrammaticalRelation, List<String>> labelTrainData,
      Counter<List<String>> featureCounter) {

    for(Structure struc : rawTrainData) {
      LinkedStack<Action> actions = struc.getActionTrace();
      int offset = struc.input.size();
      int successfulActionCnt = 0;
      if (DEBUG) {
        System.err.printf("Input: %s\n", struc.input);
      }
      Object[] acts = actions.peekN(actions.size());
      for(int i = acts.length-1 ; i >= 0 ; i--){
        Action act = (Action)acts[i];
        Datum<ActionType, List<String>> actDatum = extractActFeature(act.action, struc, featureCounter, offset);
        Datum<GrammaticalRelation, List<String>> labelDatum = extractLabelFeature(act.relation, act.action, actDatum, struc, featureCounter, offset);
        if(actDatum.asFeatures().size() > 0) actTrainData.add(actDatum);
        if((act.action==ActionType.LEFT_ARC || act.action==ActionType.RIGHT_ARC)
            && labelDatum.asFeatures().size() > 0) {
          labelTrainData.add(labelDatum);
        }

        if (DEBUG) {
          System.err.printf("State:\n\tAction: %s\n", act);
          System.err.printf("\tPre-stack: %s\n", struc.stack);
        }
        if(offset < 1) throw new RuntimeException("input offset is smaller than 1!!");
        try {
          Actions.doAction(act, struc, offset);
          if(act.action==ActionType.SHIFT) offset--;
        } catch (RuntimeException e) {
          System.err.printf("Runtime exception: %s\n", e);
          System.err.printf("Actions: %s\n", actions);
          System.err.printf("Bad action: %s\n", act);
          System.err.printf("Stack state: %s\n", struc.stack);
          System.err.printf("Successful action cnt: %d\n", successfulActionCnt);
          throw e;
        }
        if (DEBUG) {
          System.err.printf("\tPost-stack: %s\n", struc.stack);
        }
        successfulActionCnt++;
      }
    }
  }

  private static Datum<ActionType, List<String>> extractActFeature(
      ActionType act, Structure s, Counter<List<String>> featureCounter, int offset){
    // if act == null, test data
    if(offset < 1) return null;
    List<List<String>> features = DAGFeatureExtractor.extractActFeatures(s, offset, rightFeatures);
    if(featureCounter!=null) {
      Set<List<String>> rareFeatures = new HashSet<List<String>>();
      for(List<String> feature : features) {
        if(featureCounter.getCount(feature) < 3) rareFeatures.add(feature);
      }
      features.removeAll(rareFeatures);
    }
    return new BasicDatum<ActionType, List<String>>(features, act);
  }
  private static Datum<GrammaticalRelation, List<String>> extractLabelFeature(
      GrammaticalRelation rel, ActionType action,
      Datum<ActionType, List<String>> actDatum, Structure s,
      Counter<List<String>> featureCounter, int offset){
    // if act == null, test data
    List<List<String>> features = DAGFeatureExtractor.extractLabelFeatures(action, actDatum, s, offset);
    if(featureCounter!=null) {
      Set<List<String>> rareFeatures = new HashSet<List<String>>();
      for(List<String> feature : features) {
        if(featureCounter.getCount(feature) < 3) rareFeatures.add(feature);
      }
      features.removeAll(rareFeatures);
    }
    return new BasicDatum<GrammaticalRelation, List<String>>(features, rel);
  }

  // for extracting features from test data (no gold Action given)
  private static Datum<ActionType, List<String>> extractActFeature(Structure s, int offset){
    return extractActFeature(null, s, null, offset);
  }
  private static Datum<GrammaticalRelation, List<String>> extractLabelFeature(
      ActionType action, Structure s, Datum<ActionType, List<String>> actDatum, int offset){
    return extractLabelFeature(null, action, actDatum, s, null, offset);
  }

  public LinkedStack<TypedDependency> getDependencyGraph(Structure s){
    return getDependencyGraph(s, s.input.size());
  }
  public LinkedStack<TypedDependency> getDependencyGraph(Structure s, int offset){
    parsePhrase(s, offset);
    return s.dependencies;
  }
  public LinkedStack<TypedDependency> getDependencyGraph(List<CoreLabel> sentence){
    Structure s = new Structure();
    for(CoreLabel w : sentence){
      s.input.push(w);
      parsePhrase(s, 1);
    }
    return s.dependencies;
  }
  /**
   * Parse phrase
   * @param s - previous structure + new input phrase
   * @param offset - the length of new input phrase
   */
  public void parsePhrase(Structure s, int offset){
    Datum<ActionType, List<String>> d;
    while((d=extractActFeature(s, offset))!=null){
      Action nextAction;
      if(s.getStack().size()==0) nextAction = new Action(ActionType.SHIFT);
      else {
        nextAction = new Action(actClassifier.classOf(d));
        if(nextAction.action == ActionType.LEFT_ARC || nextAction.action == ActionType.RIGHT_ARC) {
          nextAction.relation = labelClassifier.classOf(extractLabelFeature(nextAction.action, s, d, offset));
        }
      }
      if(s.actionTrace.size() > 0 && s.actionTrace.peek().equals(nextAction)
          && nextAction.relation != null) {
        nextAction = new Action(ActionType.SHIFT);
      }
      Actions.doAction(nextAction, s, offset);
      if(nextAction.action==ActionType.SHIFT) offset--;
    }
  }
  public void parsePhrase(Structure s, List<CoreLabel> phrase){
    for(CoreLabel w : phrase){
      s.input.push(w);
    }
    parsePhrase(s, phrase.size());
  }

  public static void main(String[] args) throws IOException, ClassNotFoundException{

    boolean doTrain = true;
    boolean doTest = true;
    boolean storeTrainedModel = true;

    // temporary code for scorer test
    boolean testScorer = false;
    if(testScorer) {
      testScorer();
      return;
    }

    Properties props = StringUtils.argsToProperties(args);
    DepDAGParser parser = null;

    // set logger

    String timeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-");
    Logger logger = Logger.getLogger(DepDAGParser.class.getName());

    FileHandler fh;
    try {
      String logFileName = props.getProperty("log", "log.txt");
      logFileName.replace(".txt", "_"+ timeStamp+".txt");
      fh = new FileHandler(logFileName, false);
      logger.addHandler(fh);
      logger.setLevel(Level.FINE);
      fh.setFormatter(new SimpleFormatter());
    } catch (SecurityException e) {
      System.err.println("ERROR: cannot initialize logger!");
      throw e;
    } catch (IOException e) {
      System.err.println("ERROR: cannot initialize logger!");
      throw e;
    }

    if(props.containsKey("train")) doTrain = true;
    if(props.containsKey("test")) doTest = true;

    if(REDUCE_FEATURES) logger.fine("REDUCE_FEATURES on");
    else logger.fine("REDUCE_FEATURES off");
    rightFeatures = new RightSideFeatures(props);

    // temporary for debug

    //    String tempTrain = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
    //    String tempTest = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/small_train.conll";
    //    String tempTest = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp2.conll";
    //    String tempTrain = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp.conll";
    // String tempTrain = "C:\\cygwin\\home\\daniel\\temp.conll";
    //    props.put("train", tempTrain);
    //    props.put("test", tempTest);

    if(doTrain) {
      String trainingFile = props.getProperty("train", "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-train-2011-01-13.conll");

      logger.info("read training data from "+trainingFile + " ...");
      List<Structure> trainData = ActionRecoverer.readTrainingData(trainingFile);

      logger.info("train model...");
      DAGFeatureExtractor.printFeatureFlags(logger, rightFeatures);
      Date s1 = new Date();
      parser = trainModel(trainData);
      logger.info((((new Date()).getTime() - s1.getTime())/ 1000F) + "seconds\n");

      if(storeTrainedModel) {
        String defaultStore = "/scr/heeyoung/mt/mtdata/DAGparserModel_"+timeStamp+".ser";
        if(!props.containsKey("storeModel")) logger.info("no option -storeModel : trained model will be stored at "+defaultStore);
        String trainedModelFile = props.getProperty("storeModel", defaultStore);
        IOUtils.writeObjectToFile(parser, trainedModelFile);
      }
      logger.info("training is done");
    }

    if(doTest) {
      String testFile = props.getProperty("test", "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll");
      //      String testFile = props.getProperty("test", "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp.conll");
      //      String defaultLoadModel = "/scr/heeyoung/mtdata/DAGparserModel.reducedFeat_mem5_dataset.ser";

      if(parser==null) {
        String defaultLoadModel = "/scr/heeyoung/mt/mtdata/DAGparserModel_"+timeStamp+".ser";

        if(!props.containsKey("loadModel")) logger.info("no option -loadModel : trained model will be loaded from "+defaultLoadModel);
        String trainedModelFile = props.getProperty("loadModel", defaultLoadModel);
        logger.info("load trained model...");

        Date s1 = new Date();
        parser = IOUtils.readObjectFromFile(trainedModelFile);
        logger.info((((new Date()).getTime() - s1.getTime())/ 1000F) + "seconds\n");
      }
      //      if(true) return;
      logger.info("read test data from "+testFile + " ...");
      List<Structure> testData = ActionRecoverer.readTrainingData(testFile);

      List<Collection<TypedDependency>> goldDeps = new ArrayList<Collection<TypedDependency>>();
      List<Collection<TypedDependency>> systemDeps = new ArrayList<Collection<TypedDependency>>();

      logger.info("testing...");
      int count = 0;
      long elapsedTime = 0;
      for(Structure s : testData){
        count++;
        goldDeps.add(s.getDependencies().getAll());
        s.dependencies = new LinkedStack<TypedDependency>();
        Date startTime = new Date();
        LinkedStack<TypedDependency> graph = parser.getDependencyGraph(s);
        elapsedTime += (new Date()).getTime() - startTime.getTime();
        systemDeps.add(graph.getAll());
      }
      System.out.println("The number of sentences = "+count);
      System.out.printf("avg time per sentence: %.3f seconds\n", (elapsedTime / (count*1000F)));
      System.out.printf("Total elapsed time: %.3f seconds\n", (elapsedTime / 1000F));

      logger.info("scoring...");
      DependencyScoring goldScorer = DependencyScoring.newInstanceStringEquality(goldDeps);
      Score score = goldScorer.score(DependencyScoring.convertStringEquality(systemDeps));
      logger.info(score.toString(false));
      logger.info("done");

      // parse sentence. (List<CoreLabel>)
      String sent = "My dog also likes eating sausage.";
      Properties pp = new Properties();
      pp.put("annotators", "tokenize, ssplit, pos, lemma");
      StanfordCoreNLP pipeline = new StanfordCoreNLP(pp);
      Annotation document = new Annotation(sent);
      pipeline.annotate(document);
      List<CoreMap> sentences = document.get(SentencesAnnotation.class);

      List<CoreLabel> l = sentences.get(0).get(TokensAnnotation.class);
      int index = 1;
      for(CoreLabel t : l){
        t.set(IndexAnnotation.class, index++);
      }
      LinkedStack<TypedDependency> g = parser.getDependencyGraph(l);

      System.err.println(g);
      System.err.println();
    }
  }

  private static void testScorer() throws IOException {
    String trainingFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
    String devFile = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
    List<Structure> devData = ActionRecoverer.readTrainingData(devFile);
    //    List<Structure> trainData = ActionRecoverer.readTrainingData(trainingFile);

    List<Collection<TypedDependency>> goldDeps = new ArrayList<Collection<TypedDependency>>();
    List<Collection<TypedDependency>> systemDeps = new ArrayList<Collection<TypedDependency>>();
    Collection<TypedDependency> temp = new ArrayList<TypedDependency>();

    for(Structure s : devData){
      temp = s.getDependencies().getAll();
      goldDeps.add(temp);
      systemDeps.add(temp);
    }

    DependencyScoring goldScorer = DependencyScoring.newInstanceStringEquality(goldDeps);
    Score score = goldScorer.score(DependencyScoring.convertStringEquality(systemDeps));
    System.out.println(score.toString(true));
  }
}
