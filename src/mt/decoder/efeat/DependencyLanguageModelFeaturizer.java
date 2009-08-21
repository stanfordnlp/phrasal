package mt.decoder.efeat;

import edu.stanford.nlp.tagger.maxent.*;
import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import mt.syntax.mst.rmcd.ParserOptions;
import mt.syntax.mst.rmcd.DependencyParser;
import mt.syntax.mst.rmcd.DependencyDecoder;
import mt.syntax.mst.rmcd.Parameters;
import mt.syntax.mst.rmcd.DependencyPipe;
import mt.syntax.mst.rmcd.DependencyInstance;
import mt.syntax.mst.rmcd.IncrementalDependencyInstance;
import mt.syntax.mst.rmcd.DependencyInstanceFeatures;
import mt.syntax.mst.rmcd.FeatureVector;
import mt.syntax.mst.rmcd.io.CONLLWriter;
import mt.syntax.mst.rmcd.io.DependencyWriter;
import mt.syntax.mst.rmcd.io.DependencyReader;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.PhraseAlignment;
import mt.decoder.feat.RichIncrementalFeaturizer;
import mt.PseudoMoses;

import java.util.*;
import java.io.IOException;

/**
 * @author Michel Galley
 */
public class DependencyLanguageModelFeaturizer implements RichIncrementalFeaturizer<IString,String> {

  // How many words of left context for POS tagging:
  public static final String ORDER_PROPERTY = "leftWords";
  public static final int ORDER = Integer.parseInt(System.getProperty(ORDER_PROPERTY, "3"));


  private String featurePrefix;
  private String posFeatureName = ":pos";
  private String depFeatureName = ":dep";

  // Stanford Tagger:
  PrefixTagger ts;

  // McDonald dependency parser:
  Parameters par;
  DependencyParser mst_dp;
  DependencyDecoder decoder;
  DependencyWriter writer;
  DependencyPipe pipe;

  // Dependency parsing options:
  final ParserOptions options = new ParserOptions(new String[] {"trim"});
  final boolean bilingual, localNorm, exact, pos, lenNormalize, matrix, debug;
  final String[] depFeatures;

  // Cache for dependency parses of full sentence:
  Map<String,Pair<String,Double>> depCache = new HashMap<String,Pair<String,Double>>();

  // Source instances:
  List<DependencyInstance> srcInstances = new ArrayList<DependencyInstance>();

  boolean reranking;

  public DependencyLanguageModelFeaturizer() {
    featurePrefix = "mst";
    bilingual = exact = pos = localNorm = lenNormalize = matrix = debug = false;
    depFeatures = null;
  }

  @SuppressWarnings("unchecked")
  public DependencyLanguageModelFeaturizer(String... args) throws Exception {

    if(args.length != 4 && args.length != 5)
      throw new RuntimeException("Wrong number of arguments: "+args.length+"\nUsage: DependencyLanguageModelFeaturizable (id) (type) (serialized tagger) (serialized dparser)");

    featurePrefix = args[0];
    posFeatureName = featurePrefix + posFeatureName;
    depFeatureName = featurePrefix + depFeatureName;
    String optionStr = args[1];
    String taggerFile = args[2];
    String dparserFile = args[3];

    // Load source-language instances:
    if(args.length == 5) {
      bilingual = true;

      options.bilingualH2C=true;
      options.bilingualC=true;
      options.bilingualH=true;

      String srcFile = args[4];
      DependencyReader depReader = DependencyReader.createDependencyReader(pipe, "CONLL", options);
      System.err.println("Bilingual features. Reading source instances from: "+srcFile);
      depReader.startReading(srcFile, null, null);

      DependencyInstance instance = depReader.getNext();
      int num1 = 0;
      while (instance != null) {
        System.err.print(num1++ + " ");
        srcInstances.add(instance);
        instance = depReader.getNext();
      }
    } else {
      bilingual = false;
      System.err.println("Monolingual features.");
    }

    // Parsing options:
    boolean _localNorm=false, _exact=false, _lenNormalize=false, _debug=false, _matrix=false, _pos=false;
    System.err.println("options: "+optionStr);
    if(!optionStr.isEmpty())
      for(String opt : optionStr.split(":")) {
        if(opt.equals("local"))  { _localNorm = true; }
        else if(opt.equals("exact")) { _exact = true; }
        else if(opt.equals("pos")) { _pos = true; }
        else if(opt.equals("normalize")) { _lenNormalize = true; }
        else if(opt.equals("debug")) { _debug = true; }
        else if(opt.equals("matrix")) { _matrix = true; }
        else { throw new UnsupportedOperationException(); }
      }
    localNorm = _localNorm; exact = _exact; lenNormalize = _lenNormalize;
    pos = _pos; debug = _debug; matrix = _matrix;
    if(localNorm) depFeatures = new String[] { ":argmax", ":largmax", ":nlargmax" };
    else depFeatures = new String[] { ":argmax" };

    // Load tagger:
    TaggerConfig config = new TaggerConfig(new String[] {"-model",taggerFile});
    MaxentTagger.init(config.getModel(),config);

    // Load McDonald MST model:
    options.modelName = dparserFile;
    options.decodeType = "non-proj";
    pipe = new DependencyPipe(options);
    decoder = new DependencyDecoder(pipe);
    mst_dp = new DependencyParser(pipe, options);
    par = mst_dp.getParams();
    System.err.print("\tLoading model...");
    mst_dp.loadModel(options.modelName);
    pipe.closeAlphabets();
    System.err.println("done.");

    writer = DependencyWriter.createDependencyWriter("CONLL");
    CONLLWriter.skipRoot(true);
    writer.setStdErrWriter();
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    return getFeatures(f, reranking);
  }

  @Override
  public void rerankingMode(boolean r) {
    reranking = r;
  }

  private List<FeatureValue<String>> getFeatures(Featurizable<IString, String> f, boolean reranking) {

    // If first phrase, skip:
    if(f.prior == null)
      return new ArrayList<FeatureValue<String>>(0);

    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>(1+depFeatures.length);

    float tagScore = 0.0f;
    float[] depScores = new float[depFeatures.length];

    // Find/score dependencies of prior phrase if not done already:
    ScoredDependencies sd = (ScoredDependencies) f.prior.extra;
    if(sd == null)
      f.prior.extra = sd = getDependencies(f.prior);
    tagScore += sd.tagScore;
    for(int i=0; i<depScores.length; ++i)
      depScores[i] += sd.depScores[i];

    if(reranking && f.done) {

      ScoredDependencies sd_done = (ScoredDependencies) f.extra;

      // Score last phrase using approximate dependency features (dependencies with loops):
      // Find/score dependencies of current phrase if last one:
      if(sd_done == null)
        f.extra = sd_done = getDependencies(f);
      tagScore += sd_done.tagScore;
      for(int i=0; i<depScores.length; ++i)
        depScores[i] += sd_done.depScores[i];

      if(this.exact) {
        // Get exact dependency score (with loop removal):
        DependencyInstance instance = sd_done.dep;
        String sentence = StringUtils.join(instance.getForms());
        Pair<String,Double> cached = depCache.get(sentence);

        double exactDepScore;
        if(cached == null) {
          DependencyInstanceFeatures dfeatures =
            new DependencyInstanceFeatures(instance.length(), pipe.getTypes().length);
          pipe.fillFeatureVectors(instance, dfeatures, par);
          Object[][] d = decoder.decodeNonProjective(instance, dfeatures, 1, true);
          exactDepScore = par.getScore((FeatureVector) d[0][0]);
          if(lenNormalize)
            exactDepScore /= instance.length();
          String parse = (String) d[0][1];
          depCache.put(sentence, new Pair<String,Double>(parse, exactDepScore));
          System.err.printf("sent: %s\nscore: %.3f parse: %s\n", sentence, exactDepScore, parse);
        } else {
          exactDepScore = cached.second();
          //System.err.printf("cached: %s\nscore: %.3f\n", sentence, exactDepScore);
        }
        features.add(new FeatureValue<String>(depFeatureName+":exact", exactDepScore));
      }
    }

    if(pos)
      features.add(new FeatureValue<String>(posFeatureName, tagScore));
    for(int i=0; i<depScores.length; ++i) {
      double ds = depScores[i];
      if(ds != 0.0)
        features.add(new FeatureValue<String>(depFeatureName+depFeatures[i], ds));
    }
    return features;
  }

  private ScoredDependencies getDependencies(Featurizable<IString, String> f) {

    assert(f.extra == null);

    // Create new dependency instance:
    IncrementalDependencyInstance dep;
    if(f.prior != null) {
      assert(f.prior.extra != null);
      try {
        IncrementalDependencyInstance prior_dep =
             ((ScoredDependencies) f.prior.extra).dep;
        dep = (IncrementalDependencyInstance) prior_dep.clone();
      } catch(CloneNotSupportedException e) {
        e.printStackTrace();
        throw new RuntimeException();
      }
    } else {
      dep = new IncrementalDependencyInstance(pipe);
      dep.add("<root>","<root-LEMMA>","<root-CPOS>","<root-POS>", new int[0]);
      if(!srcInstances.isEmpty()) {
        int transId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);
        assert(transId >= 0);
        assert(transId < srcInstances.size());
        DependencyInstance instance = srcInstances.get(transId);
        dep.setSourceInstance(instance);
      }
    }

    // Find position where we need to start tagging and parsing:
    int loc = f.translationPosition;
    int sz = f.translatedPhrase.size();

    // Dep starts with root token, so its length is up by one:
    assert(loc == dep.length()-1);

    // Phrase alignment:
    PhraseAlignment align = f.hyp.translationOpt.abstractOption.alignment;
    assert(sz == align.size());

    // POS tagging:
    float tagScore = 0.0f;
    for(int i=0; i<sz; ++i) {
      int s = Math.max(0, loc+i-ts.getOrder());
      int e = loc+Math.min(sz, i+1);
      Sequence<IString> seq = f.partialTranslation.subsequence(s, e);
      IString[] context = new IString[seq.size()];
      for(int j=0; j<context.length; ++j)
        context[j] = seq.get(j);
      Pair<IString,Float> tag = ts.getBestTag(context, 0);
      tagScore = tag.second;

      // Add word, POS and alignment to dependency instance:
      int[] pAlign = new int[0];
      if(bilingual) {
        if(align.hasAlignment()) {
          int[] localAlign = align.e2f(i);
          if(align == null || i >= align.size()) {
            System.err.printf("Array index: %d >= %d\n", i, align.size());
            System.err.printf("Phrase pair: [%s] [%s]\n", f.foreignPhrase.toString(), f.translatedPhrase.toString());
            System.err.printf("Alignment: %s\n", align.toString());
            System.err.printf("Alignment (local): %s\n", Arrays.toString(localAlign));
            System.err.printf("Hypothesis: [%s]\n", f.partialTranslation);
            System.err.printf("Position: [%d] [%d]\n", f.foreignPosition, f.partialTranslation.size());
            throw new RuntimeException();
          }
          if(localAlign != null) {
            pAlign = new int[localAlign.length];
            for(int j=0; j<localAlign.length; ++j) {
              pAlign[j] = localAlign[j]+f.foreignPosition+1;
            }
          }
        }
      }
      dep.add(f.translatedPhrase.get(i).word(), tag.first().word(), pAlign);
    }

    // Dependency parsing:
    float argmaxDepScore = 0.0f, rootDepScore = 0.0f;
    if(pipe != null) {
      if(debug) {
        System.err.printf("\n===============\ndep of phrase: %s\nin sentence: %s\n",f.translatedPhrase,f.partialTranslation);
        mst_dp.debugHeadScores(dep);
      }
      for(int j=loc; j<loc+sz+(f.done?1:0); ++j) { // 1 word delay, except when processing last word.
        if(debug) System.err.println("word: "+dep.getForm(j));
        for(int i=0; i<j; ++i) {
          float old_sL = dep.getHeadScore(i);
          float old_sR = dep.getHeadScore(j);
          float sL = (float) pipe.getScore(dep, i, j, false, par); // left modifier attaches to right head
          float sR = (float) pipe.getScore(dep, i, j, true, par); // right modifier attaches to left head
          if(debug) {
            if(i > 0) {
              printDep(dep,i,j,sL,j-i,"   L score",false);
              printDep(dep,i,dep.getHead(i),old_sL,j-i,"   old L score"+(sL>old_sL?" (outperformed)":""), false);
            }
            printDep(dep,i,j,sR,j-i,"   R score",true);
            printDep(dep,dep.getHead(j),j,old_sR,j-i,"   old R score"+(sR>old_sR?" (outperformed)":""), true);
          }
          if(sL > old_sL) {
            if(i>0) {
              if(old_sL > -Float.MAX_VALUE) {
                float delta = sL - old_sL;
                argmaxDepScore += delta;
              } else {
                argmaxDepScore += sL;
              }
            }
            dep.setHeadScore(i, sL);
            dep.setHead(i,j);
          }
          if(sR > old_sR) {
            if(old_sR > -Float.MAX_VALUE) {
              float delta =  sR - old_sR;
              argmaxDepScore += delta;
            } else {
              argmaxDepScore += sR;
            }
            dep.setHeadScore(j, sR);
            dep.setHead(j,i);
          }
        }
      }
      if(debug) {
        System.err.printf("argmax score: %.3f\nroot score: %.3f",
            argmaxDepScore, rootDepScore);
      }
    }
    return new ScoredDependencies(dep, tagScore, localNorm ?
        new float[] { argmaxDepScore, trans(argmaxDepScore), trans(-argmaxDepScore) } :
        new float[] {argmaxDepScore}
    );
  }

  public float trans(float v) {
    double e = Math.exp(v);
    return (float)Math.log(e/(1+e));
  }

  @Override
  public void debugBest(Featurizable<IString, String> f) {

    ScoredDependencies sd = (ScoredDependencies) f.extra;
    if(sd == null)
      return;
    DependencyInstance dep = sd.dep;

    synchronized(System.err) {
      // Print best words and POS tags:
      System.err.printf("\nsent:");
      for(int i=1; i<dep.length(); ++i)
        System.err.printf(" %s", dep.getForm(i));
      System.err.printf("\npos:");
      for(int i=1; i<dep.length(); ++i)
        System.err.printf(" %s/%s", dep.getForm(i), dep.getPOSTag(i));
      System.err.printf("\n");

      // Print best dependencies:
      if(writer != null) {
        System.err.printf("dep scores:\n");
        try {
          writer.write(dep);
          writer.flush();
        } catch(IOException ioe) {
          // no big deal
          ioe.printStackTrace();
        }
        if(matrix) {
          System.err.printf("head scores:\n");
          mst_dp.debugHeadScores(dep, 20);
          System.err.printf("\n");
        }
      }
    }
  }
  
  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
                         Sequence<IString> foreign) { }

  @Override
  public FeatureValue<String> featurize(
       Featurizable<IString, String> f) { return null; }

  @Override
  public void reset() {
    if(ts == null) ts = new PrefixTagger(GlobalHolder.getLambdaSolve(),3,0); // TODO: 3,1
    ts.release();
    depCache.clear();
  }

  private void printDep(DependencyInstance dep, int i, int j, double score, int dist, String prefix, boolean attR) {
    int len = dep.length();
    System.err.printf("%s: %s/%s %s %s/%s score=%f dist=%d\n",
         prefix,
         i < 0 ? "<root>"     : (i < len ? dep.getForm(i)   : "<error>"),
         i < 0 ? "<root-POS>" : (i < len ? dep.getPOSTag(i) : "<error>"),
         (attR ? " <- " : " -> "),
         j < 0 ? "<root>"     : (j < len ? dep.getForm(j)   : "<error>"),
         j < 0 ? "<root-POS>" : (j < len ? dep.getPOSTag(j) : "<error>"),
         score, dist);
  }

  @Override
  public DependencyLanguageModelFeaturizer clone() throws CloneNotSupportedException {
    System.err.println("cloned: "+this);
    DependencyLanguageModelFeaturizer featurizer = (DependencyLanguageModelFeaturizer)super.clone();
    featurizer.pipe = (DependencyPipe) pipe.clone();
    featurizer.decoder = new DependencyDecoder(featurizer.pipe);
    featurizer.depCache = new HashMap<String,Pair<String,Double>>();
    featurizer.srcInstances = new ArrayList<DependencyInstance>(featurizer.srcInstances);
    return featurizer;
	}

  public static void main(String[] args) throws Exception {
    if(args.length != 3)
      System.err.println
       ("Usage: mt.decoder.efeat.DependencyLanguageModelFeaturizer (serialized tagger) (serialized dparser) (text to tag)");
    DependencyLanguageModelFeaturizer feat = new DependencyLanguageModelFeaturizer("id","mstparser",args[0],args[1]);
    feat.ts.tagFile(args[2]);
  }
}

class ScoredDependencies {

  final IncrementalDependencyInstance dep;
  final float tagScore;
  final float[] depScores;

  ScoredDependencies(IncrementalDependencyInstance dep, float ts, float[] ds) {
    this.dep = dep;
    tagScore = ts;
    depScores = ds;
  }
}
