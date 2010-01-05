package edu.stanford.nlp.mt.reranker;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author cer (daniel.cer@colorado.edu) 
 * @author Pi-Chuan Chang
 */

public class DataSet implements Serializable  {

  private static final long serialVersionUID = 1L;

  static final boolean DEBUG = false;
  static final double DEFAULT_TRAIN_PERCENT = 0.80;
  static final double DEFAULT_DEV_PERCENT   = 0.10;
  static final String AUTO_UNIFY_TO_PROP = "DataSet.unifyTo";
  public static final String N_THRESH_PROP = "nThresh";
  public static final String DEFAULT_N_THRESH= null;
  static int nThresh = -1;

  transient long loadTime; // How long it took to load this DataSet

  public DataSet() { ; }

  int[] trainRange;
  int[] devRange;
  int[] evalRange;

  int loadOffset = 0;

  //List<CompactHypothesisList> lchl;
  //Map<Integer, CompactHypothesisList> lchl;
  CompactHypothesisList[] lchl;
  NBestLists nbests;
  NBestLists refs;

  public int getOffset() {
    return loadOffset;
  }

  public String getFromNBest(int sentId, int hypId) {
    if (nbests == null) throw new RuntimeException("Sentences from Nbest lists don't exist!");    
    Map<Integer, String> sent = nbests.nbestMap.get(sentId);
    if (sent == null) return null;
    return sent.get(hypId);
  }

  public String[] getFromRefs(int sentId) {
    if (refs == null) throw new RuntimeException("Sentences from Ref lists don't exist!");
    Map<Integer, String> sent = refs.nbestMap.get(sentId);
    if (refs == null) return null;
    return sent.values().toArray(new String[0]);
  }

  /*
  public MyList<CompactHypothesisList> getSubset(int[] range, int[] exclude) {
    List<CompactHypothesisList> sublchl =
      new ArrayList<CompactHypothesisList>();
    //System.err.println("DEBUG: getSubset");
    for (int i = range[0]; i <= range[1]; i++) {
      if (i>=exclude[0] && i<=exclude[1]) {
        //System.err.println("\texcluding "+i);
        continue;
      }
      //System.err.println("\tincluding "+i);
      if (lchl[i]==null)
        throw new RuntimeException("nothing in compact hypothesis list "+i+"??");
      sublchl.add(lchl[i]);
    }
    return sublchl;
  }
  */

  /*
  public List<CompactHypothesisList> getSubset(int[] range) {
    List<CompactHypothesisList> sublchl = 
      new ArrayList<CompactHypothesisList>();
    //System.err.println("DEBUG: getSubset (only incl)");
    for (int i = range[0]; i <= range[1]; i++) {
      if (range.length==4 && i>=range[2] && i<=range[3]) {
        continue;
      }
      //System.err.println("\t"+i);
      if (lchl[i]==null) 
        throw new RuntimeException("nothing in compact hypothesis list "+i+"??");
      sublchl.add(lchl[i]);
    }
    return sublchl;
  }
  */


  public MyList<CompactHypothesisList> getSubset(int[] range) {
    int r[] = new int[4];
    r[0] = range[0]; r[1] = range[1];
    if (range.length==4) {
      r[2] = range[2]; r[3] = range[3];
    } else if (range.length==2) {
      r[2] = -1; r[3] = -1;
    } else {
      throw new RuntimeException("");
    }
    return new MyList<CompactHypothesisList>(lchl, range);
  }

  public int[] getTrainRange() { 
    int[] trainRange_exclude = {trainRange[0], trainRange[1], devRange[0], devRange[1]};
    return trainRange_exclude;
  }
  public int[] getDevRange() { 
    int[] trainRange_exclude = {devRange[0], devRange[1], -1, -1}; // not exclud anything
    return trainRange_exclude;
  }
  public int[] getEvalRange() { return evalRange; }

  public MyList<CompactHypothesisList> getTrainingSet() {
    //return getSubset(trainRange, devRange);
    return getSubset(getTrainRange());
  }
 
  public MyList<CompactHypothesisList> getDevSet() {
    return getSubset(devRange); }

  public MyList<CompactHypothesisList> getEvalSet() {
    return getSubset(evalRange); }

  static public String getMandatoryProperty(Properties p, String name) {
    if (!p.containsKey(name)) {
       throw new RuntimeException("Error: descriptor is missing mandatory "+
        "property '"+name+"'");
    }
    return p.getProperty(name).replaceAll("\\s+$", "");
  }

  static public int[] newRange(int start, int end) {
    int[] r = new int[2];
    r[0] = start; r[1] = end; 
    return r;
  }

  static public int[] getRange(Properties p, String name) { String r;
    if ((r = p.getProperty(name)) == null) return null;
     
    String[] fields = r.split("-");
    if (fields.length != 2) {
      throw new RuntimeException("Error: invalid range '"+r+"' found "+
        "when parsing data set descriptor");
    }
    return newRange(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]));
  }

  static public DataSet load(String dataSetDescriptor) 
    throws IOException{
    System.out.printf("Loading data set specified by descriptor: '%s'\n",
      dataSetDescriptor);

    String n = System.getProperty(N_THRESH_PROP, DEFAULT_N_THRESH);
    //nThresh = -1;
    if (n!=null) {
      nThresh = Integer.parseInt(n);
      System.err.println("In Dataset.load, setting max #hypotheses to nThresh = "+nThresh);
    }


    Exception priorException = null;
    String priorStackTrace = null;
    DataSet ds = null;
    try {
      // First, attempt to load a serialized DataSet from the filename 
      // given by dataSetDescriptor
      try {
        ds = loadSerialized(dataSetDescriptor);
        System.out.printf(
          "Data set successfully loaded as serialized .gale.DataSet\n");
        System.out.printf("Load time: %.3f s\n", ds.loadTime*1.0e-9);
        return ds;
      } catch (IOException e) {
        // okay, so we're probably not dealing with a serializd DataSet 
        // as this is probably a "Not in GZIP format" IOException
        priorException = e; 
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw); pw.close();
        priorStackTrace = sw.toString();
      } 

      // Now, we'll try treating the file specified by dataSetDescriptor 
      // as a plain text descriptor of a DataSet  
      ds = loadByTextDescriptor(dataSetDescriptor);
      System.out.printf(
        "Data set successfully loaded as plain text data set description\n");
      System.out.printf("Load time: %.3f s\n", ds.loadTime*1.0e-9);

      // If requested, unify the data set to a single binary file 
      String unifyFilename = System.getProperty(AUTO_UNIFY_TO_PROP);
      if (unifyFilename != null) ds.write(unifyFilename);
      else System.out.printf("---\n"+
      "Did you know you could dramatically reduce future load times by \n"+
      "unifying your data sets just by setting the system property '%s'?\n" +
      "However, only do so if you're not actively doing feature engineering."+
      "\n---\n",
      AUTO_UNIFY_TO_PROP);
        
      return ds;
    } catch (Exception e) {
      System.err.printf("Can't load '%s' as either a " +
         "serialized .gale.DataSet or as a plain text descriptor.\n", 
         dataSetDescriptor);
      if (priorException != null) {
        System.err.printf("Deserialization error: %s\n", priorException);
        System.err.println(priorStackTrace);
        System.err.printf("Plain text descriptor error: %s\n", e);
        e.printStackTrace();
      } else {
        System.err.printf("Error: %s\n", e); e.printStackTrace();
      }
      throw new RuntimeException(
        "Error: unable to load data set given by descriptor '"+
        dataSetDescriptor+ "'");
    }
  }
  
  static private DataSet loadSerialized(String filename) 
     throws IOException, ClassNotFoundException {
     
     long loadTime = -System.nanoTime();

     ObjectInputStream oistrm = new ObjectInputStream(
       new BufferedInputStream(new GZIPInputStream(
       new FileInputStream(filename))));
     DataSet ds = (DataSet)oistrm.readObject();
     oistrm.close();

     ds.loadTime = loadTime += System.nanoTime();
     return ds;
  } 

  public void write(String filename) throws IOException {
    ObjectOutputStream oostrm = new ObjectOutputStream(
      new GZIPOutputStream(new BufferedOutputStream(
      new FileOutputStream(filename))));
    oostrm.writeObject(this);
    oostrm.close();
  }


  static private DataSet loadByTextDescriptor(String filename) 
    throws IOException {

    long loadTime = -System.nanoTime();

    DataSet ds = new DataSet();
    
    // read in data set properties
    Properties p = new Properties();
    FileInputStream istrm = new FileInputStream(filename);
    p.load(istrm); istrm.close();

    // get our two mandatory properties
    //  - one or more feature sets to train on 
    //  - a list of sentence level scores
    String scoresFn = getMandatoryProperty(p, "HypothesisScores");
    String featureSetsProp = getMandatoryProperty(p, "FeatureSets");
    String sentsFn = p.getProperty("NBestList");
    // load all the sentences in NBestList, if there's any
    if(sentsFn==null) {
      System.err.println("Warning: NBestList is not provided in the datadescriptor. The corpus BLEU cannot be evaluated.");
      ds.nbests = null;
    } else {
      System.err.println("(Debug) start nbest sentences loading");
      ds.nbests = NBestLists.load(sentsFn);
      System.err.println("(Debug) done nbest sentences loading");
    }
    String refSentsFn = p.getProperty("RefList");
    // load all the sentences in RefList, if there's any
    if(sentsFn==null) {
      System.err.println("Warning: RefList is not provided in the datadescriptor. The corpus BLEU cannot be evaluated.");
      ds.refs= null;
    } else {
      System.err.println("(Debug) start ref sentences loading");
      ds.refs = NBestLists.load(refSentsFn);
      System.err.println("(Debug) done ref sentences loading");
    }
    String strLoadOffset = p.getProperty("LoadOffset");

    if (strLoadOffset != null) {
      ds.loadOffset = Integer.parseInt(strLoadOffset);
    }


    // load feature sets
    String[] featureSetFns = featureSetsProp.split("\\s*,\\s*");
    //ds.lchl = new HashMap<Integer, CompactHypothesisList>();
    FeatureIndex trnFeatureIndex = new FeatureIndex();
    ds.trainRange = getRange(p, "TrainRange");
    ds.devRange = getRange(p, "DevRange");
    ds.evalRange = getRange(p, "EvalRange");
    int max = -1;
    if (ds.trainRange!=null) max = ds.trainRange[1];
    if (ds.devRange!=null && ds.devRange[1] > max) max = ds.devRange[1];
    if (ds.evalRange!=null && ds.evalRange[1] > max) max = ds.evalRange[1];
    int min = Integer.MAX_VALUE;
    if (ds.trainRange!=null) min = ds.trainRange[0];
    if (ds.devRange!=null && ds.devRange[0] < min) min = ds.devRange[0];
    if (ds.evalRange!=null && ds.evalRange[0] < min) min = ds.evalRange[0];

    System.err.println("(pichuan) will be loading from "+min+" "+max);
    ds.lchl = new CompactHypothesisList[max+1];

    List<FeatureSetBank> featureSets = new ArrayList<FeatureSetBank>();
    System.err.println("(Debug) start feature loading");
    System.err.println("(DEBUG) for FeatureSetBank.load, only load up to (sid) max="+max);
    for (String featureSetFn : featureSetFns) {
      featureSets.add(FeatureSetBank.load(featureSetFn, max));
    }
    System.err.println("(Debug) done feature loading");

    System.err.println("(DEBUG) for Scores.load, only load up to (sid) max="+max);
    Scores scores = Scores.load(scoresFn, max);

    // sanity check scores against feature sets
    SortedSet<Integer> scoresDataPts = scores.getDataPointIndices();
    for (FeatureSetBank featureSet : featureSets) {
      if (!scoresDataPts.containsAll(featureSet.getDataPointIndices())) {
        System.err.println("featureSet.getDataPointIndices().size()="+featureSet.getDataPointIndices().size());
        System.err.println("scoresDataPts.size()="+scoresDataPts.size());
         throw new RuntimeException(
           String.format(
           "Error: feature set '%s' describes more data points " +
           "than we have scores for.", featureSet.getName())); 
      }  
    } 
    System.err.println("(Debug) done score loading");
    int numTranslations = scoresDataPts.size();

    // If specified, retrieve optional train, dev, & eval ranges.
    // Otherwise, just set some reasonable defaults
    if (ds.trainRange == null) {
      ds.trainRange=newRange(0,(int)(DEFAULT_TRAIN_PERCENT*numTranslations)-1);
    }
    if (ds.devRange == null) {
      ds.devRange = newRange(ds.trainRange[1],
         Math.min(numTranslations-1,
           ds.trainRange[1]+(int)(DEFAULT_DEV_PERCENT*numTranslations)-1));
    }
    if (ds.evalRange == null) {
      ds.evalRange = newRange(ds.devRange[1], numTranslations-1);
    }

    // go through training set first
    for (int dataPt=min; dataPt <=max; dataPt++) {
      if (dataPt >= ds.devRange[0] && dataPt <= ds.devRange[1]) {
        System.err.println("(Training) Making compact hypothesis list for data point "+dataPt);
        CompactHypothesisList chl 
          = new CompactHypothesisList(trnFeatureIndex,
                                      featureSets, scores, dataPt, false, nThresh);
        ds.lchl[dataPt] = chl;          
        continue;
      }
      // Training!
      if (dataPt>=ds.trainRange[0] && dataPt <= ds.trainRange[1]) {
        if (dataPt >= ds.devRange[0] && dataPt <= ds.devRange[1]) {
          continue; // already made...
        }
        System.err.println("(Training) Making compact hypothesis list for data point "+dataPt);
        CompactHypothesisList chl 
          = new CompactHypothesisList(trnFeatureIndex,
                                      featureSets, scores, dataPt, false, nThresh);
        if (chl==null) {
          throw new RuntimeException("When adding... nothing in compact hypothesis list "+dataPt+"??");
        }
        ds.lchl[dataPt] = chl;
      }
    }
      
    System.err.println("# of feature types="+trnFeatureIndex.size());

    ds.loadTime = loadTime += System.nanoTime();
    return ds;
  } 

  static public void outputPredictedIndices(
    AbstractOneOfManyClassifier classifier,
    MyList<CompactHypothesisList> lchl) throws Exception{
    PrintWriter pr = new PrintWriter(
      new BufferedWriter(new FileWriter("predictedIndices.txt", false)));
    int[]      bestChoices = classifier.getBestPrediction(lchl);
    for (int i : bestChoices) {
      pr.println(i);
    }
    pr.close();
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.printf("Usage:\n\tjava %s (data descriptor)\n", 
         (new DataSet()).getClass().getName());
      System.exit(-1);   
    }
    DataSet.load(args[0]);
  } 
}
