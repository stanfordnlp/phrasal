package mt.syntax.mst;

import mt.base.MosesNBestList;
import mt.base.ScoredFeaturizedTranslation;
import mt.base.FeatureValue;
import mt.base.IString;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;

import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import mt.syntax.mst.rmcd.ParserOptions;
import mt.syntax.mst.rmcd.DependencyPipe;
import mt.syntax.mst.rmcd.DependencyParser;
import mt.syntax.mst.rmcd.DependencyInstance;
import mt.syntax.mst.rmcd.io.CONLLWriter;
import mt.syntax.mst.rmcd.io.DependencyWriter;
import edu.stanford.nlp.util.MutableInteger;

public class MSTNbestScorer {

  private static final Map<String,Double> cache = new HashMap<String,Double>();
  
  private final MutableInteger curSent, lastSent, doneThreads;

  public MSTNbestScorer() {
    curSent = new MutableInteger();
    lastSent = new MutableInteger();
    doneThreads = new MutableInteger();
  }

  public void score(final String taggerFile, final String dparserFile, String extension, String[] args) throws IOException {
    
    for(int i=3; i<args.length; ++i) {
      String nbestListFile = args[i];
      System.err.println("Loading nbest list: "+nbestListFile);
      final MosesNBestList nbestList = new MosesNBestList(nbestListFile, true);

      doneThreads.set(0);
      curSent.set(0);
      lastSent.set(nbestList.nbestLists().size()-1);

      DependencyParser dp = init(taggerFile, dparserFile);
      for(int j=0; j<=lastSent.intValue(); ++j)
        addMSTFeatures(dp, nbestList, j);
      
      String outFile = nbestListFile.replaceAll(".gz$","."+extension+".gz");
      if(outFile.equals(nbestListFile))
        throw new RuntimeException("Wrong file format: "+nbestListFile);
      System.err.printf("Saving to %s ...\n", outFile);

      OutputStreamWriter writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outFile)));
      writer.append(nbestList.printMosesFormat());
      writer.close();
    }
  }

  public final DependencyParser init(String taggerFile, String dparserFile) {
    try {
      // Load tagger:
      System.err.println("Loading tagger...");
      TaggerConfig config = new TaggerConfig(new String[] {"-model",taggerFile});
      MaxentTagger.init(config.getModel(),config);

      // Load McDonald MST model:
      String[] opts = new String[] {
        "decode-type:non-proj", "labeled:false", "format:plain", "trim", "ignore-loops",
        "model-name:"+dparserFile, "tagger:"+taggerFile };
      ParserOptions options = new ParserOptions(opts);
      DependencyPipe pipe = new DependencyPipe(options);
      DependencyParser dp = new DependencyParser(pipe, options);
      System.err.print("\tLoading model...");
      dp.loadModel(options.modelName);
      pipe.closeAlphabets();
      System.err.println("done.");

      // Writer:
      DependencyWriter writer = DependencyWriter.createDependencyWriter("CONLL");
      CONLLWriter.skipRoot(true);
      writer.setStdErrWriter();
      return dp;
    } catch(Exception e) {
      e.printStackTrace(); 
    }
    return null;
  }

  public void addMSTFeatures(DependencyParser dp, MosesNBestList nbest, int i) throws IOException {
    // Add dependency parsing score:
    List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLL = nbest.nbestLists();
    List<ScoredFeaturizedTranslation<IString,String>> nbestL = nbestLL.get(i);
    for(int j=0; j<nbestL.size(); ++j) {
      ScoredFeaturizedTranslation<IString,String> el = nbestL.get(j);
      String input = el.translation.toString();
      if(cache.containsKey(input)) {
        System.err.printf("%d %d: cached.\n", i, j);
        addFeatures(el.features, cache.get(input));
      } else {
        DependencyInstance di = dp.parse(el.translation.toString(), true);
        System.err.printf("%d %d: parsed:\n%s\n", i, j, di.prettyPrint());
        double score = dp.getScore();
        addFeatures(el.features, score);
        cache.put(input, score);
      }
    }
  }

  private void addFeatures(List<FeatureValue<String>> f, double score) {
    f.add(new FeatureValue<String>("mst",score));
  }

  public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.printf("Usage:\n\tjava ... (extension) (tagger model) (dependency parser model) (nbest list1) ... (nbest listN)\n");
			System.exit(-1);
		}

    String extension = args[0];
    String taggerFile = args[1];
    String dparserFile = args[2];

    new MSTNbestScorer().score(taggerFile, dparserFile, extension, args);
  }
}
