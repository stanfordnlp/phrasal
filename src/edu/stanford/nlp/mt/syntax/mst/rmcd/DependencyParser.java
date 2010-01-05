package mt.syntax.mst.rmcd;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.math.ArrayMath;

import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.Collection;
import java.util.TreeSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

public class DependencyParser {

  //private static final boolean DEBUG = false;

  public ParserOptions options;

  private DependencyPipe pipe;
  private DependencyDecoder decoder;
  private Parameters params;

  private FeatureVector fv;

  public DependencyParser(DependencyPipe pipe, ParserOptions options) {
    this.pipe = pipe;
    this.options = options;
    // Set up arrays
    params = new Parameters(pipe.getDataAlphabet().size());
    decoder = new DependencyDecoder(pipe);
  }

  public void trainME(double l1reg) throws IOException {

    if(options.labeled)
      throw new RuntimeException("ME training currently not available with typed dependencies.");
    
    initTrainReader();

    long start = System.currentTimeMillis();

    DependencyInstance instance = pipe.nextInstance();
    System.err.println("types: " + Arrays.toString(pipe.getTypes()));

    boolean binary = (options.mixModelNames == null);
    System.err.println("Assuming binary features only: "+binary);

    // Count # of positives/negatives:
    int[] counts = new int[2];

    // Create dataset:
    int cnt = 0;
    GeneralDataset<Boolean,Integer> dataset = binary ?
       new Dataset<Boolean,Integer>() :
       new RVFDataset<Boolean,Integer>();
    while (instance != null) {
      if(cnt++ == options.sents)
        break;
      System.out.print(cnt + " ");
      if(options.debug) System.err.println("sentence: "+Arrays.toString(instance.getForms()));

      int sz = instance.length();
      assert(instance != null);
      assert(pipe.getTypes() != null);

      for(int psz=(options.predictRight ? 3:sz); psz<=sz; ++psz) {

        DependencyInstance pinstance = (psz < sz) ? instance.getPrefixInstance(psz) : instance;

        // Feature extraction:
        DependencyInstanceFeatures f =
         new DependencyInstanceFeatures(pinstance.length(), pipe.getTypes().length);
        pipe.fillFeatureVectors(pinstance, f, null);

        int len = f.length();
        assert(len == sz);

        if(binary) {
          final Set<Integer> empty = new TreeSet<Integer>();
          dataset.add(new BasicDatum<Boolean,Integer>(empty, false));
          dataset.add(new BasicDatum<Boolean,Integer>(empty, true));
        } else {
          final Counter<Integer> empty = new ClassicCounter<Integer>();
          dataset.add(new RVFDatum<Boolean,Integer>(empty, false));
          dataset.add(new RVFDatum<Boolean,Integer>(empty, true));
        }

        // Generate Datum instances:
        for(int i=0; i<len; ++i) {
          int headi = pinstance.getHead(i);
          for(int j=i+1; j<len; ++j) {
            int headj = pinstance.getHead(j);
            for(int k=0; k<2; ++k) {
              boolean attR = (k==0);
              boolean att = attR ? (headj == i) : (headi == j);
              Datum<Boolean,Integer> datum;
              if(binary) {
                Collection<Integer> features = f.getCollection(i, j, attR);
                datum = new BasicDatum<Boolean,Integer>(features, att);
              } else {
                Counter<Integer> features = f.getCounter(i, j, attR);
                datum = new RVFDatum<Boolean,Integer>(features,att);
              }
              dataset.add(datum);
              ++counts[att?1:0];
              if(options.debug) {
                System.err.printf("i(%d)->j(%d) %s->%s attR=%s label=%s\n",
                  i, j, pinstance.getForm(i), pinstance.getForm(j), attR, att);
              }
            }
          }
        }
      }

      instance = pipe.nextInstance();
    }

    closeTrainReader();

    System.err.printf("\nPositive instances: %d", counts[1]);
    System.err.printf("\nNegative instances: %d\n", counts[0]);

    // Training:
    LogisticClassifier<Boolean,Integer> me = new LogisticClassifier<Boolean,Integer>();
    me.train(dataset, l1reg, 1e-4);
    params.setWeights(me.getFeatureIndex(), me.getWeights());

    long end = System.currentTimeMillis();
    System.out.printf("Took %d milliseconds\n",end - start);
  }

  /**
   * Prune and reindex data index.
   */
  public void reindex() {
    TrieAlphabet da = pipe.getDataAlphabet();

    System.err.printf("Data alphabet size: %d\n", da.size());
    System.err.printf("reindexing...\n");

    da.reindex(params, pipe.getMixParameters());
    
    System.err.printf("Data alphabet size: %d\n", da.size());
  }

  /**
   * Merge mixtures of log-linear models.
   */

  public void mergeMixtures() {

    TrieKey key = new TrieKey(pipe.getDataAlphabet());

    double[] w = params.parameters;

    for(Map.Entry<String,Parameters> entry : pipe.getMixParameters().entrySet()) {
      String id = entry.getKey();
      key.clear().add("MX=").add(id).stop();
      int mixId = key.id();
      double wi = w[mixId];
      System.err.printf("Mixture model = %s, w = %f.\n", id, wi);
      w[mixId] = 0.0;
      ArrayMath.addMultInPlace(w, entry.getValue().parameters, wi);
      assert(w[mixId] == 0.0);
    }
    pipe.getMixParameters().clear();
  }

  public void train(int[] instanceLengths, File train_forest) throws IOException {
    int i;
    for (i = 0; i < options.numIters; i++) {
      System.out.print(" Iteration " + i);
      System.out.print("[");
      long start = System.currentTimeMillis();
      trainingIter(instanceLengths, train_forest, i + 1);
      long end = System.currentTimeMillis();
      System.out.println("|Time:" + (end - start) + "]");
    }
    params.averageParams(i * instanceLengths.length);
  }

  private void trainingIter(int[] instanceLengths, File train_forest, int iter)
       throws IOException {

    ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(train_forest)));
    int numInstances = instanceLengths.length;

    for (int i = 0; i < numInstances; i++) {
      if ((i + 1) % 500 == 0)
        System.out.print((i + 1) + ",");
      int length = instanceLengths[i];
      DependencyInstanceFeatures f = new DependencyInstanceFeatures(length, pipe.getTypes().length);
      DependencyInstance inst = pipe.readInstance(in, f, params);
      double upd = (double) (options.numIters * numInstances - (numInstances * (iter - 1) + (i + 1)) + 1);
      int K = options.trainK;
      Object[][] d = options.proj ?
           decoder.decodeProjective(inst, f, K) :
           decoder.decodeNonProjective(inst, f, K, true);
      params.updateParamsMIRA(inst, d, upd);
    }
    System.out.print(numInstances);
    in.close();
  }

  ///////////////////////////////////////////////////////
  // Saving and loading models
  ///////////////////////////////////////////////////////
  
  public void saveModel(String file) throws IOException {
    boolean gz = file.endsWith("gz");
    OutputStream os = gz ? new GZIPOutputStream(new FileOutputStream(file)) : new FileOutputStream(file);
    ObjectOutputStream out = new ObjectOutputStream(os);
    out.writeObject(params.parameters);
    out.writeObject(pipe.getDataAlphabet());
    out.writeObject(pipe.getTypeAlphabet());
    out.close();
  }

  public void loadModel(String file) throws Exception {
    boolean gz = file.endsWith("gz");
    InputStream is = gz ? new GZIPInputStream(new FileInputStream(file)) : new FileInputStream(file);
    ObjectInputStream in = new ObjectInputStream(is);
    params.parameters = (double[]) in.readObject();
    pipe.setDataAlphabet((TrieAlphabet) in.readObject());
    if(options.trim)
      pipe.getDataAlphabet().trim();
    pipe.setTypeAlphabet((Alphabet) in.readObject());
    in.close();
    pipe.readMixtureModels();
  }

  public void dumpModel() throws IOException {
    System.err.println("parameters: "+params.parameters.length);
    System.err.println("dataAlphabel: "+pipe.getDataAlphabet().size());
    System.err.println("typeAlphabel: "+pipe.getTypeAlphabet().size());
    Writer writer = new BufferedWriter
      (new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(options.txtModelName))));
    for(Map.Entry<Integer, List<String>> e : pipe.getDataAlphabet().toMap().entrySet()) {
      int idx = e.getKey();
      if(idx >= 0) {
        double v = params.parameters[e.getKey()];
        if(v != 0.0) {
          List<String> f = e.getValue();
          writer.append(Double.toString(v)).append("\t").append(StringUtils.join(f,"")).append("\n");
        }
      }
    }
    writer.close();
  }

  //////////////////////////////////////////////////////
  // Input and output //////////////////////////////////
  //////////////////////////////////////////////////////

  void initTrainReader() throws IOException {
    String tFile = options.trainfile;

    if(tFile == null || tFile.equals("")) {
      pipe.setDepReader(new BufferedReader(new InputStreamReader(System.in)));
      System.err.println("Reading from stdin...");
    } else {
      pipe.initInputFile(tFile, options.ftrainfile, options.atrainfile);
    }
  }

  void closeTrainReader() throws IOException {
    pipe.close();
  }

  void initTestReaderAndWriter() throws IOException {

    String tFile = options.testfile;
    String tfFile = options.ftestfile;
    String taFile = options.atestfile;
    String file = options.outfile;

    if(tFile == null || tFile.equals("")) {
      pipe.setDepReader(new BufferedReader(new InputStreamReader(System.in)));
      System.err.println("Reading from stdin...");
    } else {
      pipe.initInputFile(tFile, tfFile, taFile);
    }

    if(file == null || file.equals("")) {
      pipe.setDepWriter(new BufferedWriter(new OutputStreamWriter(System.out)));
      System.err.println("Writing to stdout...");
    } else {
      pipe.initOutputFile(file);
    }
    
  }

  void closeTestReaderAndWriter() throws IOException {
    pipe.close();
  }

  //////////////////////////////////////////////////////
  // Get Best Parses ///////////////////////////////////
  //////////////////////////////////////////////////////

  public DependencyInstance parse(String input) throws IOException {
    return parse(pipe.readInstance(input));
  }

  public DependencyInstance parse(String input, boolean score) throws IOException {
    return parse(pipe.readInstance(input), score);
  }

  public DependencyInstance parse(DependencyInstance instance) throws IOException {
    return parse(instance, false);
  }

  public DependencyInstance parse(DependencyInstance instance, boolean score) throws IOException {
    DependencyInstanceFeatures f =
     new DependencyInstanceFeatures(instance.length(), pipe.getTypes().length);
    pipe.fillFeatureVectors(instance, f, params);

    int K = options.testK;
    assert(K == 1); // n-best output currently not supported
    if(options.debug) debugHeadScores(instance);
    Object[][] d = options.proj ?
         decoder.decodeProjective(instance, f, K) :
         decoder.decodeNonProjective(instance, f, K, options.debug || score);
    // d is a Kx2 matrix:
    // [k][0]: features
    // [k][1]: dependencies

    String[] res = ((String) d[0][1]).split(" ");
    this.fv = (FeatureVector)d[0][0];

    int[] heads = new int[instance.length()];
    String[] labels = new String[heads.length];

    labels[0] = "";
    heads[0] = -1;
    for (int j = 1; j < heads.length; j++) {

      String[] trip = res[j-1].split("[\\|:]");
      labels[j] = pipe.getTypes()[Integer.parseInt(trip[2])];
      heads[j] = Integer.parseInt(trip[0]);
    }
    
    instance.setHeads(heads);
    instance.setDepRels(labels);

    if(options.debug) {
      //double score = getScore(); // params.getScore((FeatureVector) d[0][0]);
      System.err.println("parse: "+instance.prettyPrint());
      System.err.println("score: "+getScore());
    }


    return instance;
  }

  public double getScore() {
    assert(fv != null);
    return params.getScore(fv);
  }

  public void outputParses() throws IOException {

    initTestReaderAndWriter();
    
    if(options.debugFeatures)
      pipe.initReverseAlphabet();

    long start = System.currentTimeMillis();

    DependencyInstance instance = pipe.nextInstance();
    int cnt = 0;

    System.err.println("types: " + Arrays.toString(pipe.getTypes()));

    while (instance != null) {
      cnt++;
      System.out.print(cnt + " ");
      pipe.outputInstance(parse(instance));
      instance = pipe.nextInstance();
    }
    
    closeTestReaderAndWriter();

    long end = System.currentTimeMillis();
    System.out.printf("Took %d milliseconds\n",end - start);
  }

  /////////////////////////////////////////////////////
  // RUNNING THE PARSER
  ////////////////////////////////////////////////////
  public static void main(String[] args) throws Exception {

    ParserOptions options = new ParserOptions(args);

    if (options.train) {
      DependencyParser dp;
      DependencyPipe pipe = new DependencyPipe(options);
      long start = System.currentTimeMillis();
      if (options.trainME) {
        pipe.createAlphabet(options.trainfile, options.ftrainfile, options.atrainfile);
        dp = new DependencyParser(pipe, options);
        dp.trainME(options.l1reg);
        if(!options.noreindex)
          dp.reindex();
      } else {
        int[] instanceLengths =
             pipe.createInstances(options.trainfile, options.ftrainfile, options.atrainfile, options.trainforest);
        dp = new DependencyParser(pipe, options);
        long end = System.currentTimeMillis();
        System.out.printf("Took %d milliseconds\n",end - start);
        int numFeats = pipe.getDataAlphabet().size();
        int numTypes = pipe.getTypeAlphabet().size();
        System.out.print("Num Feats: " + numFeats);
        System.out.println(".\tNum Edge Labels: " + numTypes);
        dp.train(instanceLengths, options.trainforest);
      }
      System.out.print("Saving model...");
      dp.mergeMixtures();
      dp.saveModel(options.modelName);
      if(options.txtModelName != null) {
        dp.dumpModel();
      }
      System.out.print("done.");
    }

    if (options.test) {
      DependencyPipe pipe = new DependencyPipe(options);
      DependencyParser dp = new DependencyParser(pipe, options);
      System.out.print("\tLoading model...");
      dp.loadModel(options.modelName);
      System.out.println("done.");
      pipe.closeAlphabets();
      dp.outputParses();
    }

    if (options.txtModelName != null && !options.train && options.genTextModel && new File(options.modelName).exists()) {
      DependencyPipe pipe = new DependencyPipe(options);
      DependencyParser dp = new DependencyParser(pipe, options);
      dp.loadModel(options.modelName);
      dp.dumpModel();
    }

    System.out.println();
    if (options.eval) {
      System.out.println("\nEVALUATION PERFORMANCE:");
      DependencyEvaluator.evaluate(options.goldfile,
           options.outfile,
           options.goldformat,
           options.outputformat);
    }
  }

  public Parameters getParams() { return params; }

  public void debugHeadScores(DependencyInstance dep) {
    debugHeadScores(dep, 20); 
  }

  public void debugHeadScores(DependencyInstance dep, int maxLen) {

    //pipe.initReverseAlphabet();
    //boolean debugFeatures = options.debugFeatures;
    //options.debugFeatures = true;

    Parameters par = getParams();
    int len = dep.length();
    if (len > maxLen)
      return;
    double[][] scores = new double[len][len];
    for(int i=0; i<len; ++i) {
      for(int j=0; j<len; ++j) {
        int small=i, large=j;
        if(small>large) { small=j; large=i; }
				if(options.debugFeatures)
					System.err.printf("\nsmall: %d/%s large: %d/%s\n", small, dep.getForm(small), large, dep.getForm(large));
        scores[i][j] = (i==j) ? 0 : pipe.getScore(dep, small, large, i<j, par);
      }
    }
    System.err.println("\nDebug head scores(parser): ");
    for (int j = 0; j < len; ++j)
      System.err.printf("\t%s[%d]", dep.getForm(j), j);
    System.err.println();
    for(int i=0; i<len; ++i) {
      System.err.printf("[%d]", i);
      for(int j=0; j<len; ++j) {
        System.err.printf("\t%.2f", scores[i][j]);
      }
      System.err.println();
    }

    //options.debugFeatures = debugFeatures;
  }
}
