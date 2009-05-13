package mt.syntax.mst;

import mt.base.MosesNBestList;
import mt.base.ScoredFeaturizedTranslation;
import mt.base.FeatureValue;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.tagger.exptag.MaxentTagger;
import edu.stanford.nlp.parser.mst.rmcd.ParserOptions;
import edu.stanford.nlp.parser.mst.rmcd.DependencyPipe;
import edu.stanford.nlp.parser.mst.rmcd.DependencyParser;
import edu.stanford.nlp.parser.mst.rmcd.DependencyInstance;
import edu.stanford.nlp.parser.mst.rmcd.io.CONLLWriter;
import edu.stanford.nlp.parser.mst.rmcd.io.DependencyWriter;
import edu.stanford.nlp.util.IString;

public class MSTNbestScorer {

  static Map<String,Double> cache = new HashMap<String,Double>();
  
  static DependencyParser dp;

  public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.printf("Usage:\n\tjava ... (extension) (tagger model) (dependency parser model) (nbest list1) ... (nbest listN)\n");
			System.exit(-1);
		}

    String extension = args[0];
    String taggerFile = args[1];
    String dparserFile = args[2];
    init(taggerFile, dparserFile);

    for(int i=3; i<args.length; ++i) {
      String nbestListFile = args[i];
      System.err.println("Loading nbest list: "+nbestListFile);
      MosesNBestList nbestList = new MosesNBestList(nbestListFile);
      addMSTFeatures(nbestList);
      String outFile = nbestListFile.replaceAll(".gz$","."+extension+".gz");
      if(outFile.equals(nbestListFile))
        throw new RuntimeException("Wrong file format: "+nbestListFile);
      OutputStreamWriter writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outFile)));
      writer.append(nbestList.printMosesFormat());
      writer.close();
    }
  }

  public static void init(String taggerFile, String dparserFile) throws Exception {

    // Load tagger:
    System.err.println("Loading tagger...");
    TaggerConfig config = new TaggerConfig(new String[] {"-model",taggerFile});
    MaxentTagger.init(config.getModel(),config);

    // Load McDonald MST model:
    ParserOptions options = new ParserOptions(new String[0]);
    options.trim = true;
    options.modelName = dparserFile;
    options.tagger = taggerFile;
    options.decodeType = "non-proj";
    options.format = "plain";
    DependencyPipe pipe = new DependencyPipe(options);
    dp = new DependencyParser(pipe, options);
    System.err.print("\tLoading model...");
    dp.loadModel(options.modelName);
    pipe.closeAlphabets();
    System.err.println("done.");

    // Writer:
    DependencyWriter writer = DependencyWriter.createDependencyWriter("CONLL");
    CONLLWriter.skipRoot(true);
    writer.setStdErrWriter();
  }

  public static void addMSTFeatures(MosesNBestList nbest) throws Exception {

    // Add dependency parsing score:
    List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLL = nbest.nbestLists();
    int i=0;
    for(List<ScoredFeaturizedTranslation<IString,String>> nbestL : nbestLL) {
      int j=0;
      for(ScoredFeaturizedTranslation<IString,String> el : nbestL) {
        String input = el.translation.toString();
        //int len = input.length();
        if(cache.containsKey(input)) {
          System.err.printf("%d %d: cached.\n", i, j);
          addFeatures(el.features, cache.get(input)); //, len);
        } else {
          DependencyInstance di = dp.parse(el.translation.toString());
          System.err.printf("%d %d: parsed:\n%s\n", i, j, di.prettyPrint());
          double score = dp.getScore();
          addFeatures(el.features, score); //, len);
          cache.put(input, score);
        }
        ++j;
      }
      ++i;
    }
  }

  private static void addFeatures(List<FeatureValue<String>> f, double score) {
    f.add(new FeatureValue<String>("mst",score));
    /*if(len > 0) {
      f.add(new FeatureValue<String>("sm-mst",sm(score/len)));
      f.add(new FeatureValue<String>("n-sm-mst",sm(-score/len)));
      f.add(new FeatureValue<String>("log-sm-mst",Math.log(sm(score/len))));
      f.add(new FeatureValue<String>("log-n-sm-mst",Math.log(sm(-score/len))));
      f.add(new FeatureValue<String>("norm-mst",score/len));
    }*/
  }

  /*private static double sm(double v) {
    double e = Math.exp(v);
    return e/(1+e);
  }*/
}
