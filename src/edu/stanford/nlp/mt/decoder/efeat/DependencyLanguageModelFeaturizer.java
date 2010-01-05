package edu.stanford.nlp.mt.decoder.efeat;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.tagger.maxent.GlobalHolder;

import edu.stanford.nlp.mt.syntax.mst.rmcd.ParserOptions;
import edu.stanford.nlp.mt.syntax.mst.rmcd.DependencyParser;
import edu.stanford.nlp.mt.syntax.mst.rmcd.DependencyDecoder;
import edu.stanford.nlp.mt.syntax.mst.rmcd.Parameters;
import edu.stanford.nlp.mt.syntax.mst.rmcd.DependencyPipe;
import edu.stanford.nlp.mt.syntax.mst.rmcd.DependencyInstance;
import edu.stanford.nlp.mt.syntax.mst.rmcd.DependencyInstanceFeatures;
import edu.stanford.nlp.mt.syntax.mst.rmcd.FeatureVector;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.CONLLWriter;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyWriter;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyReader;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.decoder.feat.RichIncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.StatefulFeaturizer;
import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.tools.PrefixTagger;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.LinkedList;

import gnu.trove.THashMap;

/**
 * @author Michel Galley
 */
public class DependencyLanguageModelFeaturizer extends StatefulFeaturizer<IString,String> implements RichIncrementalFeaturizer<IString,String> {

  // How many words of left context for POS tagging:
  public static final String ORDER_PROPERTY = "leftWords";
  public static final int ORDER = Integer.parseInt(System.getProperty(ORDER_PROPERTY, "3"));

  public static final String TAG_WITH_RIGHT_CONTEXT_PROPERTY = "tagWithRight";
  public static final boolean TAG_WITH_RIGHT_CONTEXT = System.getProperty(TAG_WITH_RIGHT_CONTEXT_PROPERTY) != null;

  public static final String CACHE_PARTIAL_PROPERTY = "cachePartial";
  public static final boolean CACHE_PARTIAL = System.getProperty(CACHE_PARTIAL_PROPERTY) != null;

  public static final String NODELAY_PROPERTY = "noDelay";
  public static final boolean NODELAY = System.getProperty(NODELAY_PROPERTY) != null;

  // Normalization options:
  static public final String NORM_PLUS_UNNORM_OPT = "both";
  static public final String LOCAL_NORM_OPT = "localNorm";
  static public final String LOCAL_INV_NORM_OPT = "localInvNorm";
  static public final String LEN_NORM_OPT = "lenNorm";
  static public final String MST_LEN_NORM_OPT = "mstLenNorm";

  // Feature options:
  static public final String MST_SCORE_OPT = "mstScore";
  static public final String POS_SCORE_OPT = "posScore";

  // Debug options:
  static public final String DEBUG_OPT = "debug";
  static public final String VERBOSE_DEBUG_OPT = "verboseDebug";
  static public final String DEBUG_MATRIX_SCORES_OPT = "matrix";

  static final Set<String> allOpts = new HashSet<String>
    (Arrays.asList
      (LOCAL_NORM_OPT, LOCAL_INV_NORM_OPT, LEN_NORM_OPT, MST_LEN_NORM_OPT,
       NORM_PLUS_UNNORM_OPT, MST_SCORE_OPT, POS_SCORE_OPT,
       DEBUG_OPT, DEBUG_MATRIX_SCORES_OPT));

  private String depFeatureName = ":dep";

  // Stanford Tagger:
  PrefixTagger tagger;

  // McDonald dependency parser:
  Parameters par;
  DependencyParser parser;
  DependencyDecoder decoder;
  DependencyWriter writer;
  DependencyPipe pipe;

  // Dependency parsing options:
  final ParserOptions options = new ParserOptions(new String[] {"trim"});

  // Options:
  private final boolean bilingual;
  private final boolean mstScore, posScore; // scores
  private final boolean localNorm, localInvNorm, lenNorm, mstLenNorm, normAndUnnorm; // normalization
  private final boolean debug, verboseDebug, matrix; // debug

  private final String[] depFeatures;

  // Caches:
  Map<String, DependencyScores> partialParseCache = new THashMap<String, DependencyScores>();
  Map<String,Pair<String,Double>> fullParseCache = new THashMap<String,Pair<String,Double>>();
  private Map<String,String> long2short = new THashMap<String,String>();

  // Source instances:
  List<DependencyInstance> srcInstances = new ArrayList<DependencyInstance>();

  boolean reranking;

  @SuppressWarnings("unchecked")
  public DependencyLanguageModelFeaturizer(String... args) throws Exception {

    DependencyInstance.incremental = true;

    if(args.length != 4 && args.length != 5)
      throw new RuntimeException("Wrong number of arguments: "+args.length+"\nUsage: DependencyLanguageModelFeaturizable (id) (type) (serialized tagger) (serialized dparser)");

    String featurePrefix = args[0];
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

    // Make sure caching is turned off with bilingual features:
    System.err.println("cache partial: "+CACHE_PARTIAL);
    if(bilingual && CACHE_PARTIAL) {
      throw new RuntimeException("Can't cache target side dependency structure when dependencies also depend on the source!!");
    } else if(!bilingual && !CACHE_PARTIAL) {
      System.err.println("WARNING: depLM without caching is going to be inefficient!");
    }

    // Parsing options:
    System.err.println("options: "+optionStr);
    Set<String> opts = new HashSet<String>();
    if(!optionStr.isEmpty())
      opts.addAll(Arrays.asList(optionStr.split(":")));
    if(!allOpts.containsAll(opts))
      throw new UnsupportedOperationException("Some unknown option in: "+optionStr);
    localNorm = opts.contains(LOCAL_NORM_OPT);
    localInvNorm = opts.contains(LOCAL_INV_NORM_OPT);
    normAndUnnorm = opts.contains(NORM_PLUS_UNNORM_OPT);
    mstScore = opts.contains(MST_SCORE_OPT);
    posScore = opts.contains(POS_SCORE_OPT);
    lenNorm = opts.contains(LEN_NORM_OPT);
    mstLenNorm = opts.contains(MST_LEN_NORM_OPT);
    verboseDebug = opts.contains(VERBOSE_DEBUG_OPT);
    debug = opts.contains(DEBUG_OPT) || verboseDebug;
    matrix = opts.contains(DEBUG_MATRIX_SCORES_OPT);
    depFeatures = getLocalFeatureNames();

    // Load tagger:
    MaxentTagger tagger = new MaxentTagger(taggerFile);

    // Load McDonald MST model:
    options.modelName = dparserFile;
    options.decodeType = "non-proj";
    pipe = new DependencyPipe(options);
    decoder = new DependencyDecoder(pipe);
    parser = new DependencyParser(pipe, options);
    par = parser.getParams();
    System.err.print("\tLoading model...");
    parser.loadModel(options.modelName);
    pipe.closeAlphabets();
    System.err.println("done.");

    writer = DependencyWriter.createDependencyWriter("CONLL");
    CONLLWriter.skipRoot(true);
    writer.setStdErrWriter();
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    /*if(reranking) {
      System.err.printf("@(%d)", f.translationPosition);
      try { throw new Exception(); } catch(Exception e) { e.printStackTrace(); }
    }*/
    return NODELAY ? getFeatures(f) : getDelayedFeatures(f, reranking);
  }

  @Override
  public void rerankingMode(boolean r) {
    reranking = r;
  }

  /**
   * Add MST feature once decoding is complete.
   *
   */
  private void addMSTFeature(DependencyInstance instance, List<FeatureValue<String>> features) {
    if(this.reranking && this.mstScore) {
      // Get mstScore dependency score (with loop removal):
      String sentence = StringUtils.join(instance.getForms());
      Pair<String,Double> cached = fullParseCache.get(sentence);

      double exactDepScore;
      if(cached == null) {
        DependencyInstanceFeatures dfeatures =
          new DependencyInstanceFeatures(instance.length(), pipe.getTypes().length);
        pipe.fillFeatureVectors(instance, dfeatures, par);
        Object[][] d = decoder.decodeNonProjective(instance, dfeatures, 1, true);
        exactDepScore = par.getScore((FeatureVector) d[0][0]);
        if(mstLenNorm)
          exactDepScore /= instance.length();
        String parse = (String) d[0][1];
        fullParseCache.put(sentence, new Pair<String,Double>(parse, exactDepScore));
        System.err.printf("sent: %s\nscore: %.3f parse: %s\n", sentence, exactDepScore, parse);
      } else {
        exactDepScore = cached.second();
        //System.err.printf("cached: %s\nscore: %.3f\n", sentence, exactDepScore);
      }
      features.add(new FeatureValue<String>(depFeatureName+":mst", exactDepScore));
    }
  }

  private List<FeatureValue<String>> getFeatures(Featurizable<IString, String> f) {

    DependencyScores sd;
    f.setState(this, sd = getDependencies(f));
    float[] localScores = sd.localScores;

    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>(1+depFeatures.length);

    if(f.done)
      addMSTFeature(sd.dep,features);

    for(int i=0; i<localScores.length; ++i) {
      double ds = localScores[i];
      if(ds != 0.0)
        features.add(new FeatureValue<String>(depFeatureName+depFeatures[i], ds));
    }
    return features;
  }

  private List<FeatureValue<String>> getDelayedFeatures(Featurizable<IString, String> f, boolean reranking) {

    // If first phrase, skip:
    if(f.prior == null)
      return new ArrayList<FeatureValue<String>>(0);

    // Find/score dependencies of prior phrase if not done already:
    DependencyScores sd = (DependencyScores) f.prior.getState(this);
    if(sd == null) {
      sd = getDependencies(f.prior);
      f.prior.setState(this, sd);
    }
    float[] localScores = sd.localScores;

    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>(1+depFeatures.length);

    if(reranking && f.done) {

      DependencyScores sd_done = (DependencyScores) f.getState(this);

      // Score last phrase using approximate dependency features (dependencies with loops):
      // Find/score dependencies of current phrase if last one:
      if(sd_done == null) {
        sd_done = getDependencies(f);
        f.setState(this, sd_done);
      }
      localScores = sd.localScores.clone();
      for(int i=0; i<localScores.length; ++i)
        localScores[i] += sd_done.localScores[i];

      addMSTFeature(sd_done.dep, features);
    }

    for(int i=0; i<localScores.length; ++i) {
      double ds = localScores[i];
      if(ds != 0.0)
        features.add(new FeatureValue<String>(depFeatureName+depFeatures[i], ds));
    }
    return features;
  }

  @SuppressWarnings("unchecked")
  private DependencyScores getDependencies(Featurizable<IString, String> f) {

    DependencyScores currentNode = (f.prior != null) ? (DependencyScores)f.prior.getState(this) : null;
    assert(f.getState(this) == null);

    // Determine if surface string was seen before:
    String partialTranslation = CACHE_PARTIAL ? (f.partialTranslation.toString()+" | "+f.done) : null;
    if(CACHE_PARTIAL) {
      if(debug)
        System.err.println("cache: "+partialTranslation);
      DependencyScores equiv_sd = partialParseCache.get(partialTranslation);
      if(equiv_sd != null) {
        try {
          DependencyScores copy_sd = equiv_sd.clone();
          copy_sd.updateLocalFromTotal(currentNode);
          return copy_sd;
        } catch(CloneNotSupportedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    // Find position where we need to start tagging and parsing:
    int loc = f.translationPosition;
    int sz = f.translatedPhrase.size();

    // POS tagging:
    float tagScore = 0.0f;
    Pair<IString,Float>[] tags = new Pair[sz];
    if(TAG_WITH_RIGHT_CONTEXT) {
      // faster, better POS accuracy, lower BLEU
      int sp = Math.max(0, loc- tagger.getOrder());
      int ep = loc+sz;
      Sequence<IString> seq = f.partialTranslation.subsequence(sp, ep);
      IString[] context = new IString[seq.size()];
      for(int j=0; j<context.length; ++j)
        context[j] = seq.get(j);
      for(int i=0; i<sz; ++i) {
        tags[i] = tagger.getBestTag(context, -sz+i+1);
        tagScore += tags[i].second;
      }
    } else {
      for(int i=0; i<sz; ++i) {
        int s = Math.max(0, loc+i- tagger.getOrder());
        int e = loc+Math.min(sz, i+1);
        Sequence<IString> seq = f.partialTranslation.subsequence(s, e);
        IString[] context = new IString[seq.size()];
        for(int j=0; j<context.length; ++j)
          context[j] = seq.get(j);
        tags[i] = tagger.getBestTag(context, 0);
        tagScore += tags[i].second;
      }
    }

    // Create new dependency instance:
    DependencyInstance dep;
    if(f.prior != null) {
      assert(f.prior.getState(this) != null);
      try {
        DependencyInstance prior_dep =
             ((DependencyScores) f.prior.getState(this)).dep;
        dep = (DependencyInstance) prior_dep.clone();
      } catch(CloneNotSupportedException e) {
        e.printStackTrace();
        throw new RuntimeException();
      }
    } else {
      dep = new DependencyInstance(pipe);
      dep.add("<root>","<root-LEMMA>","<root-CPOS>","<root-POS>", new int[0]);
      if(!srcInstances.isEmpty()) {
        int transId = f.translationId + (Phrasal.local_procs > 1 ? 2 : 0);
        assert(transId >= 0);
        assert(transId < srcInstances.size());
        DependencyInstance instance = srcInstances.get(transId);
        dep.setSourceInstance(instance);
      }
    }

    // Dep starts with root token, so its length is up by one:
    assert(loc == dep.length()-1);

    // Phrase alignment:
    PhraseAlignment align = null;
    if(bilingual) {
      align = f.hyp.translationOpt.abstractOption.alignment;
			if(align != null)
				assert(sz == align.size());
    }

    for(int i=0; i<sz; ++i) {
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
      add(dep, f.translatedPhrase.get(i).word(), tags[i].first().word(), pAlign);
    }

    // Dependency parsing:
    float argmaxDepScore = 0.0f, rootDepScore = 0.0f;
    if(pipe != null) {
      if(verboseDebug) {
        System.err.printf("\n===============\ndep of phrase: %s\nin sentence: %s\n",f.translatedPhrase,f.partialTranslation);
        parser.debugHeadScores(dep);
      }
      for(int j=loc; j<loc+sz+(f.done?1:0); ++j) { // 1 word delay, except when processing last word.
        if(verboseDebug) System.err.println("word: "+dep.getForm(j));
        for(int i=0; i<j; ++i) {
          float old_sL = dep.getHeadScore(i);
          float old_sR = dep.getHeadScore(j);
          float sL = (float) pipe.getScore(dep, i, j, false, par); // left modifier attaches to right head
          float sR = (float) pipe.getScore(dep, i, j, true, par); // right modifier attaches to left head
          if(verboseDebug) {
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
        if(debug) {
          int hj = dep.getHead(j);
          if(verboseDebug) System.err.printf("m(%d)=%s h(%d)=%s\n", j, dep.getForm(j), hj, hj >= 0 ? dep.getForm(hj) : null);
        }
      }
      if(debug) {
        System.err.printf("argmax score: %.3f\nroot score: %.3f\n",
            argmaxDepScore, rootDepScore);
      }
    }
    DependencyScores successorNode = new DependencyScores(dep, loc, sz, tagScore, argmaxDepScore, currentNode);

		// Cache successor:
    if (CACHE_PARTIAL)
      partialParseCache.put(partialTranslation, successorNode);
    return successorNode;
  }

  /**
   * Print dependency structure of 1-best hypothesis.
   *
   */
  @Override
  public void debugBest(Featurizable<IString, String> f) {

    DependencyScores sd = (DependencyScores) f.getState(this);
    if(sd == null)
      return;
    DependencyInstance dep = sd.dep;

    synchronized(System.err) {
      // Print best words and POS tags:
      System.err.printf("\nsent:");
      for(int i=1; i<dep.length(); ++i)
        System.err.printf(" %s", dep.getForm(i));
      System.err.printf("\nposScore:");
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
          parser.debugHeadScores(dep, 20);
          System.err.printf("\n");
        }
      }
    }
    Deque<String> phrases = new LinkedList<String>();
    Featurizable<IString, String> curf = f;
    while(curf != null) {
      phrases.addFirst(curf.translatedPhrase.toString());
      curf = curf.prior;
    }
    int i = 0;
    for(String p : phrases)
      System.err.printf("phrase %d: %s\n", i++, p);
    assert(lastIsGood(sd));
  }

  private boolean lastIsGood(DependencyScores sd) {
    int lastDep = sd.dep.getHead(sd.dep.length()-1);
    return lastDep >= 0;
  }

  private String lookupShort(String l, int len) {
    if(l.length() > len) {
      String s = long2short.get(l);
      if(s != null) {
        return s;
      }
      s = l.substring(0,len).intern();
      long2short.put(l,s);
      return s;
    }
    return l;
  }

  private void add(DependencyInstance dep, String form, String pos, int[] pAlign) {
    String lemma = lookupShort(form, DependencyInstance.LEMMA_LEN);
    String cpos = lookupShort(pos, DependencyInstance.CPOS_LEN);
    dep.add(form, lemma, cpos, pos, pAlign);
  }

  @Override
  public void reset() {
    if (tagger == null) tagger = new PrefixTagger(GlobalHolder.getLambdaSolve(),3,0); // TODO: 3,1
    tagger.release();
    pipe.clearCache();
    System.err.printf("Emptying %d keys of partial parse cache.\n", partialParseCache.size());
    fullParseCache.clear();
    partialParseCache.clear();
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
    featurizer.fullParseCache = new THashMap<String,Pair<String,Double>>();
    featurizer.partialParseCache = new THashMap<String, DependencyScores>();
    featurizer.long2short = new THashMap<String,String>();
    featurizer.srcInstances = new ArrayList<DependencyInstance>(featurizer.srcInstances);
    return featurizer;
	}

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
                         Sequence<IString> foreign) { }

  @Override
  public FeatureValue<String> featurize(
       Featurizable<IString, String> f) { return null; }

  private float localNorm(float v) {
    double e = Math.exp(v);
    return (float)Math.log(e/(1+e));
  }

  private float lenNorm(float oldTotalScore, float localScore, int pos, int phraseLen) {
    float lenNormScore = (oldTotalScore+localScore)/(pos+phraseLen);
    if(pos > 0)
      lenNormScore -= oldTotalScore/pos;
    System.err.printf("%f\t", lenNormScore);
    return lenNormScore;
  }

  private String[] getLocalFeatureNames() {
    List<String> names = new ArrayList<String>();
    if(normAndUnnorm || !localNorm || !localInvNorm) names.add(":argmax");
    if(localNorm) names.add(":largmax");
    if(localInvNorm) names.add(":nlargmax");
    if(lenNorm) names.add(":argmax:len");
    if(posScore) names.add(":pos");
    return names.toArray(new String[names.size()]);
  }

  private float[] getLocalFeatures(DependencyScores d, int pos, int phraseLen, float tagScore, float depScore) {
    float[] scores = new float[depFeatures.length];
    int i=-1;
    if(normAndUnnorm || !localNorm || !localInvNorm) scores[++i] = depScore;
    if(localNorm) scores[++i] = localNorm(depScore);
    if(localInvNorm) scores[++i] = localNorm(-depScore);
    boolean hasPrev = d != null && d.totalScores != null;
    if(lenNorm) scores[++i] = lenNorm(hasPrev ? d.totalScores[0] : 0.0f, depScore, pos, phraseLen);
    if(posScore) scores[++i] = tagScore;
    assert(++i == depFeatures.length);
    return scores;
  }

  public static void main(String[] args) throws Exception {
    if(args.length != 3)
      System.err.println
       ("Usage: edu.stanford.nlp.mt.decoder.efeat.DependencyLanguageModelFeaturizer (serialized tagger) (serialized dparser) (text to tag)");
    DependencyLanguageModelFeaturizer feat = new DependencyLanguageModelFeaturizer("mst","",args[0],args[1]);
    feat.reset();
    feat.tagger.tagFile(args[2]);
  }

  class DependencyScores implements Cloneable {

    final DependencyInstance dep;
    float[] localScores;
    float[] totalScores;

    DependencyScores(DependencyInstance dep, int pos, int phraseLen, float tagScore, float depScore, DependencyScores prior) {
      this.dep = dep;
      localScores = getLocalFeatures(prior, pos, phraseLen, tagScore, depScore);
      totalScores = (prior == null) ? localScores : ArrayMath.pairwiseAdd(localScores,prior.totalScores);
    }

    void updateLocalFromTotal(DependencyScores prior) {
      if(prior != null)
        localScores = ArrayMath.pairwiseSubtract(totalScores,prior.totalScores);
      else
        localScores = totalScores;
    }

    public DependencyScores clone() throws CloneNotSupportedException {
      DependencyScores sd = (DependencyScores) super.clone();
      sd.localScores = localScores.clone();
      sd.totalScores = totalScores.clone();
      return sd;
    }

  }
}
