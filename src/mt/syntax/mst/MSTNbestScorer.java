package mt.syntax.mst;

import mt.base.MosesNBestList;
import mt.base.ScoredFeaturizedTranslation;
import mt.base.FeatureValue;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.tagger.exptag.MaxentTagger;
import edu.stanford.nlp.parser.mst.rmcd.ParserOptions;
import edu.stanford.nlp.parser.mst.rmcd.DependencyPipe;
import edu.stanford.nlp.parser.mst.rmcd.DependencyParser;
import edu.stanford.nlp.parser.mst.rmcd.io.CONLLWriter;
import edu.stanford.nlp.parser.mst.rmcd.io.DependencyWriter;
import edu.stanford.nlp.util.IString;

public class MSTNbestScorer {

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.err.printf("Usage:\n\tjava ...(Moses style nbest list) (tagger model) (dependency parser model)\n");
			System.exit(-1);
		}
		String nbestListFilename = args[0];
    System.err.println("Loading nbest list...");
		MosesNBestList nbestList = new MosesNBestList(nbestListFilename);
		addMSTFeatures(nbestList, args[1], args[2]);
		System.out.print(nbestList.printMosesFormat());
	}

  public static void addMSTFeatures(MosesNBestList nbest, String taggerFile, String dparserFile)
       throws Exception {

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
    DependencyParser dp = new DependencyParser(pipe, options);
    System.err.print("\tLoading model...");
    dp.loadModel(options.modelName);
    pipe.closeAlphabets();
    System.err.println("done.");

    // Writer:
    DependencyWriter writer = DependencyWriter.createDependencyWriter("CONLL");
    CONLLWriter.skipRoot(true);
    writer.setStdErrWriter();

    // Add dependency parsing score:
    Map<String,Double> cache = new HashMap<String,Double>();
    List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLL = nbest.nbestLists();
    int i=0;
    for(List<ScoredFeaturizedTranslation<IString,String>> nbestL : nbestLL) {
      int j=0;
      for(ScoredFeaturizedTranslation<IString,String> el : nbestL) {
        String input = el.translation.toString();
        int len = input.length();
        if(cache.containsKey(input)) {
          System.err.printf("%d %d: c\n", i, j);
          addFeatures(el.features, cache.get(input), len);
        } else {
          System.err.printf("%d %d: p\n", i, j);
          dp.parse(el.translation.toString());
          double score = dp.getScore();
          addFeatures(el.features, score, len);
          cache.put(input, score);
        }
        ++j;
      }
      ++i;
    }
  }

  private static void addFeatures(List<FeatureValue<String>> f, double score, int len) {
    f.add(new FeatureValue<String>("mst",score));
    f.add(new FeatureValue<String>("sm-mst",sm(score/len)));
    f.add(new FeatureValue<String>("n-sm-mst",sm(-score/len)));
    f.add(new FeatureValue<String>("log-sm-mst",Math.log(sm(score/len))));
    f.add(new FeatureValue<String>("log-n-sm-mst",Math.log(sm(-score/len))));
    if(len > 0)
      f.add(new FeatureValue<String>("norm-mst",score/len));
  }

  private static double sm(double v) {
    double e = Math.exp(v);
    return e/(1+e);
  }
}
