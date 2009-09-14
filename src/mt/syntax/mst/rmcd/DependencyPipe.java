package mt.syntax.mst.rmcd;

import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.util.StringUtils;

import gnu.trove.TIntArrayList;
import gnu.trove.THashMap;

import mt.syntax.mst.rmcd.io.*;

public class DependencyPipe implements Cloneable {

  private Alphabet typeAlphabet;
  private TrieAlphabet dataAlphabet;
  private Map<Integer, List<String>> dataReverseAlphabet;

  private String STR, END, MID;

  private Map<String,Parameters> mixParams;

  Set<String> inBetweenPOS, inBetweenCPOS;

  private DependencyReader depReader;
  private DependencyWriter depWriter;

  private ParserOptions opt;

  private String[] types;

  private boolean labeled = false;
  private boolean isCONLL = true;

  private TrieKey key;
  private StringBuilder sb;

  private static int[] distBin;

  private Map<DependencyPair, Double> cache = new THashMap<DependencyPair, Double>();

  public DependencyPipe(ParserOptions options) throws Exception {
    this.opt = options;

    if (!options.format.equalsIgnoreCase("conll") &&
        !options.format.equalsIgnoreCase("plain") &&
        !options.format.equalsIgnoreCase("tagged"))
      isCONLL = false;
    assert(isCONLL); // currently disabled

    typeAlphabet = new Alphabet();
    dataAlphabet = new TrieAlphabet();

    if(options.trim) {
      STR = "STR"; MID = "MID"; END = "END";
    } else {
      STR = "STR "; MID = "MID "; END = "END ";
    }
    System.err.println("Special tags: "+Arrays.toString(new String[] {STR,MID,END}));

    key = new TrieKey(dataAlphabet);
    sb = new StringBuilder(50);

    if(opt.inBetweenPOS != null)
      inBetweenPOS = new TreeSet<String>(Arrays.asList(posSeq(opt.inBetweenPOS)));
    if(opt.inBetweenCPOS != null)
      inBetweenCPOS = new TreeSet<String>(Arrays.asList(posSeq(opt.inBetweenCPOS)));

    depReader = DependencyReader.createDependencyReader(this, options.format, options);
    options.printFeatureOptions();
    createDistBinArray(ParserOptions.distBinStr);
  }

  private static void createDistBinArray(String distBinStr) {
    List<Integer> els = new ArrayList<Integer>();
    
    for(String str : distBinStr.split(","))
      els.add(Integer.parseInt(str));

    distBin = new int[els.get(els.size()-1)+1];
    
    for(int i=1; i<els.size(); ++i)
      for(int j=els.get(i-1); j<els.get(i); ++j)
        distBin[j] = els.get(i-1)-1;

    distBin[distBin.length-1] = els.get(els.size()-1)-1;
    System.err.println("distBin: "+Arrays.toString(distBin));
  }

  private String[] posSeq(String s) {
    if(!opt.trim) {
      s = s + " ";
      s = s.replace("~"," ~");
    }
    System.err.println("POS: "+Arrays.toString(s.split("~")));
    return s.split("~");
  }

  public void readMixtureModels() throws IOException, ClassNotFoundException {
    
    if(opt.mixModelNames != null) {
      mixParams = new THashMap<String,Parameters>();

      for(String fileName : opt.mixModelNames.split("~")) {

        System.err.printf("Pipe (%s) reading %s...\n", this, fileName);

        // Add mixture feature to alphabet:
        key.clear().add("MX=").add(fileName).stop();

        // Deserialize:
        boolean gz = fileName.endsWith("gz");
        InputStream is = gz ? new GZIPInputStream(new FileInputStream(fileName)) : new FileInputStream(fileName);
        ObjectInputStream in = new ObjectInputStream(is);
        double[] W = (double[]) in.readObject();
        int[] ids = new int[W.length];
        TrieAlphabet da = (TrieAlphabet) in.readObject();
        if(opt.trim)
          da.trim();
        Object ta = in.readObject();
        assert(ta instanceof Alphabet);

        // Map old indices to new ones:
        Map<Integer, List<String>> map = da.toMap();
        for(Map.Entry<Integer, List<String>> e : map.entrySet()) {
          int oldId = e.getKey();
          key.clear();
          for(String s : e.getValue())
            key.add(s);
          key.stop();
          int newId = key.id();
          ids[oldId] = newId;
        }

        // Create new weight vector:
        double[] newW = new double[dataAlphabet.size()];
        for(int oldId=0; oldId<ids.length; ++oldId) {
          int newId = ids[oldId];
          if(newId >= 0)
            newW[newId] = W[oldId];
        }

        Parameters params = new Parameters(0);
        params.parameters = newW;
        mixParams.put(fileName, params);
      }
    }
  }

  public Map<String,Parameters> getMixParameters() {
    return (mixParams != null) ? mixParams : new THashMap<String,Parameters>(0);
  }

  public String[] getTypes() {
    return types;
  }

  public boolean isLabeled() {
    return labeled;
  }

  public boolean ignoreLoops() {
    return opt.ignoreLoops;
  }

  public void setDataAlphabet(TrieAlphabet a) {
    dataAlphabet = a;
    key = new TrieKey(dataAlphabet);
  }

  public void setTypeAlphabet(Alphabet a) {
    typeAlphabet = a;
  }

  public Alphabet getTypeAlphabet() {
    return typeAlphabet;
  }

  public TrieAlphabet getDataAlphabet() {
    return dataAlphabet;
  }

  public void setDepReader(BufferedReader r) {
    depReader.setReader(r);
    labeled = opt.labeled;
  }

  public void setDepWriter(BufferedWriter w) {
    depWriter.setWriter(w);
  }
  
  public void initInputFile(String file, String sourceFile, String alignFile) throws IOException {
    labeled = depReader.startReading(file, sourceFile, alignFile) && opt.labeled;
  }

  public void initOutputFile(String file) throws IOException {
    depWriter =
         DependencyWriter.createDependencyWriter(opt.outputformat);
    depWriter.startWriting(file);
  }

  public void outputInstance(DependencyInstance instance) throws IOException {
    depWriter.write(instance);
  }

  public void close() throws IOException {
    if (null != depWriter) {
      depWriter.finishWriting();
    }
  }

  public String getType(int typeIndex) {
    return types[typeIndex];
  }

  protected final DependencyInstance readInstance(String input) throws IOException {
    return initInstance(depReader.readNext(input));
  }

  protected final DependencyInstance nextInstance() throws IOException {
    return initInstance(depReader.getNext());
  }

  private DependencyInstance initInstance(DependencyInstance instance) throws IOException {
    if (instance == null || !instance.hasForms()) return null;
    instance.setFeatureVector(createFeatureVector(instance));
    return instance;
  }

  public int[] createInstances(String file, String sourceFile, String alignFile, File featFileName) throws IOException, ClassNotFoundException {

    createAlphabet(file, sourceFile, alignFile);
    System.err.println("Num Features (dataAlphabet): " + dataAlphabet.size());

    labeled = depReader.startReading(file, sourceFile, alignFile) && opt.labeled;

    TIntArrayList lengths = new TIntArrayList();

    ObjectOutputStream out = opt.createForest
         ? new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(featFileName)))
         : null;

    DependencyInstance instance = depReader.getNext();

    System.err.println("Creating Feature Vector Instances: ");
    int num1 = 0;
    while (instance != null) {
      System.out.print(num1++ + " ");
      if(num1 == opt.sents)
        break;

      instance.setFeatureVector(createFeatureVector(instance));

      sb.setLength(0);
      for (int i = 1; i < instance.length(); i++) {
        sb.append(instance.getHead(i)).append("|").append(i).append(":").append(typeAlphabet.lookupIndex(instance.getDepRel(i))).append(" ");
      }
      instance.setParseTree(sb.toString());

      lengths.add(instance.length());

      if (opt.createForest)
        writeInstance(instance, out);
      instance = depReader.getNext();
    }
    System.err.println("\nCreating Feature Vector Instances: done.");
    System.out.println();

    if (opt.createForest && out != null)
      out.close();

    return lengths.toNativeArray();
  }

  void createAlphabet(String file, String sourceFile, String alignFile) throws IOException, ClassNotFoundException {

    System.out.print("Creating Alphabet ... ");

    labeled = depReader.startReading(file, sourceFile, alignFile) && opt.labeled;

    DependencyInstance instance = depReader.getNext();

    int num1 = 0;
    while (instance != null) {
      if(num1++ == opt.sents)
        break;

      for(int i=0; i<instance.length(); ++i)
        typeAlphabet.lookupIndex(instance.getDepRel(i));
      createFeatureVector(instance);
      instance = depReader.getNext();
    }

    readMixtureModels();

    closeAlphabets();

    if(opt.debugFeatures)
      initReverseAlphabet();

    System.out.println("Done.");
    System.err.printf("Data alphabet size: %d/%d\n",
      dataAlphabet.trueSize(false), dataAlphabet.size());
  }

  public void initReverseAlphabet() {
    if(dataReverseAlphabet != null)
      return;
    dataReverseAlphabet = dataAlphabet.toMap();
    System.err.println("Num Features (dataReverseAlphabet): " + dataReverseAlphabet.size());
  }

  public void closeAlphabets() {
    dataAlphabet.stopGrowth();
    typeAlphabet.stopGrowth();

    types = new String[typeAlphabet.size()];
    Object[] keys = typeAlphabet.toArray();
    for(Object key : keys) {
      int indx = typeAlphabet.lookupIndex(key);
      types[indx] = (String) key;
    }

    KBestParseForest.rootType = typeAlphabet.lookupIndex("<root-type>");
  }

  // add with default 1.0
  public final void add(TrieKey key, FeatureVector fv) {
    key.stop();
    int num = key.id();
    if (num >= 0)
      fv.add(num, 1.0);
  }

  public final void add(TrieKey key, double val, FeatureVector fv) {
    key.stop();
    int num = key.id();
    if (num >= 0) {
      fv.add(num, val);
    }
  }

  public FeatureVector createFeatureVector(DependencyInstance instance) {

    final int instanceLength = instance.length();

    FeatureVector fv = new FeatureVector();

    if(opt.debug && dataAlphabet.growthStopped()) {
      System.err.printf("sentence: %s\n", Arrays.toString(instance.getForms()));
      DependencyInstance srcInstance = instance.getSourceInstance();
      if(srcInstance != null) {
        System.err.printf("src-sentence: %s\nalignment:", Arrays.toString(srcInstance.getForms()));
        for(int i=1; i<instance.length(); ++i) {
          for(int j : instance.getSource(i))
            System.err.printf(" %d(%s)-%d(%s)", i, instance.getForm(i), j, srcInstance.getForm(j));
        }
        System.err.println();
      }
    }

    for (int i = 0; i < instanceLength; i++) {
      int hi = instance.getHead(i);
      if (hi == -1)
        continue;
      
      int small = i < hi ? i : hi;
      int large = i > hi ? i : hi;
      boolean attR = i >= hi;
      
      if(opt.debugFeatures && dataAlphabet.growthStopped()) {
        if(attR) {
          System.err.printf("dependency: %d_%s_%s %d_%s_%s attR=%s\n",
              hi, instance.getForm(hi), instance.getPOSTag(hi),
              i, instance.getForm(i), instance.getPOSTag(i),
              attR);
        } else {
          System.err.printf("dependency: %d_%s_%s %d_%s_%s attR=%s\n",
              i, instance.getForm(i), instance.getPOSTag(i),
              hi, instance.getForm(hi), instance.getPOSTag(hi),
              attR);
        }
      }

      addCoreFeatures(instance, small, large, attR, fv);
      addMixFeatures(fv);

      if (labeled) {
        String li = instance.getDepRel(i);
        addLabeledFeatures(instance, i, li, attR, true, fv);
        addLabeledFeatures(instance, hi, li, attR, false, fv);
      }
    }

    return fv;
  }

  public void fillFeatureVectors(DependencyInstance instance,
                                 DependencyInstanceFeatures f,
                                 Parameters params) {

    //if(opt.debug)
    //  System.err.printf("sentence: %s\n", Arrays.toString(instance.getForms()));

    // Get production crap.
    final int instanceLength = instance.length();
    for (int w1 = 0; w1 < instanceLength; w1++) {
      for (int w2 = w1 + 1; w2 < instanceLength; w2++) {
        for (int ph = 0; ph < 2; ph++) {
          boolean attR = ph == 0;

          if(opt.debugFeatures) {
            System.err.printf("testing dependency: %d_%s_%s %d_%s_%s attR=%s\n",
              w1, instance.getForm(w1), instance.getPOSTag(w1),
              w2, instance.getForm(w2), instance.getPOSTag(w2),
              attR);
          }

          FeatureVector prodFV = new FeatureVector();
          addCoreFeatures(instance, w1, w2, attR, prodFV);
          addMixFeatures(prodFV);
          f.setFVS(w1, w2, ph, prodFV);

          if(opt.debugFeatures)
            debugFeatures(prodFV, params);

          if(params != null) {
            double prodProb = params.getScore(prodFV);
            f.probs[w1][w2][ph] = prodProb;
            if(opt.debugFeatures) {
              System.err.printf("score = %.3f\n", prodProb);
            }
          }
        }
      }
    }

    if (labeled) {
      for (int w1 = 0; w1 < instanceLength; w1++) {
        for (int t = 0; t < types.length; t++) {
          String type = types[t];
          for (int ph = 0; ph < 2; ph++) {

            boolean attR = ph == 0;
            for (int ch = 0; ch < 2; ch++) {

              boolean child = ch == 0;

              FeatureVector prodFV = new FeatureVector();
              addLabeledFeatures(instance, w1,
                   type, attR, child, prodFV);
              f.setNT_FVS(w1, t, ph, ch, prodFV);
              if(params != null) {
                double nt_prob = params.getScore(prodFV);
                f.nt_probs[w1][t][ph][ch] = nt_prob;
              }
            }
          }
        }
      }
    }
  }

  public double getScore(DependencyInstance instance, int w1, int w2, boolean attR, Parameters params) {
    Double cachedScore;
    DependencyPair dp = null;
    if(!opt.bilingualC) {
      dp = new DependencyPair(instance, w1, w2, attR);
      cachedScore = cache.get(dp);
      if(cachedScore != null)
        return cachedScore;
    }

    FeatureVector prodFV = new FeatureVector();
    addCoreFeatures(instance, w1, w2, attR, prodFV);
    addMixFeatures(prodFV);
    if(opt.debugFeatures)
      debugFeatures(prodFV, params);
    cachedScore = params.getScore(prodFV);

    if(!opt.bilingualC)
      cache.put(dp, cachedScore);
    return cachedScore;
  }

  private void debugFeatures(FeatureVector prodFV, Parameters params) {
    if(dataReverseAlphabet != null) {
      for(int k : prodFV.keys()) {
        List<String> featL = dataReverseAlphabet.get(k);
        String feat = StringUtils.join(featL,"");
        if(params != null)
          System.err.printf("  feat [%s] id [%d] val [%f]\n", feat, k, params.parameters[k]);
        else
          System.err.printf("  feat [%s] id [%d]\n", feat, k);
      }
    }
  }

  // Add mixture of models features:
  public void addMixFeatures(FeatureVector fv) {
    if(mixParams != null)
      for(Map.Entry<String,Parameters> e : mixParams.entrySet()) {
        String name = e.getKey();
        Parameters p = e.getValue();
        double score = p.getScore(fv);
        if(opt.debug && dataAlphabet.growthStopped())
          System.err.printf("  model=%s sz=%d sz=%d score=%.3f\n", name, p.parameters.length, fv.size(), score);
        key.clear().add("MX=").add(name).stop();
        add(key,score,fv);
      }
  }

  static int distBin(int dist) {
    assert(dist > 0);
    if(dist >= distBin.length)
      dist = distBin.length-1;
    return distBin[dist];
  }

  public void addCoreFeatures(DependencyPair dp, FeatureVector fv) {
    addCoreFeatures(dp.inst, dp.i, dp.j, dp.attR, fv);
  }

  public void addCoreFeatures(DependencyInstance instance,
                              int small,
                              int large,
                              boolean attR,
                              FeatureVector fv) {

    String att = attR ? "RA&" : "LA&";

    int dist = Math.abs(large - small);
    String distBool = Integer.toString(distBin(dist));

    sb.setLength(0);
    sb.append('&').append(att).append(distBool);
    String attDist = sb.toString();

    if(opt.posLinearFeatures)
      addLinearPOSFeatures(instance, small, large, attDist, false, fv);
    
    if(opt.cposLinearFeatures)
      addLinearPOSFeatures(instance, small, large, attDist, true, fv);

    //////////////////////////////////////////////////////////////////////

    int headIndex = small;
    int childIndex = large;
    if (!attR) {
      headIndex = large;
      childIndex = small;
    }

    String lemma_headIndex = instance.getLemma(headIndex);
    String lemma_childIndex = instance.getLemma(childIndex);

    String forms_headIndex = instance.getForm(headIndex);
    String forms_childIndex = instance.getForm(childIndex);

    String pos_headIndex = instance.getPOSTag(headIndex);
    String pos_childIndex = instance.getPOSTag(childIndex);

    String posA_headIndex = instance.getCPOSTag(headIndex);
    String posA_childIndex = instance.getCPOSTag(childIndex);

    /////////////////////////////////////////////////////////////////////
    
    if(opt.coreFeatures)
      addTwoObsFeatures("HC", forms_headIndex, pos_headIndex,
           forms_childIndex, pos_childIndex, attDist, fv);

    if (isCONLL) {

      if(opt.cposFeatures)
        addTwoObsFeatures("HCA", forms_headIndex, posA_headIndex,
             forms_childIndex, posA_childIndex, attDist, fv);

      if(opt.lemmaFeatures)
        addTwoObsFeatures("HCC", lemma_headIndex, pos_headIndex,
             lemma_childIndex, pos_childIndex,
             attDist, fv);

      if(opt.cposFeatures && opt.lemmaFeatures)
        addTwoObsFeatures("HCD", lemma_headIndex, posA_headIndex,
             lemma_childIndex, posA_childIndex,
             attDist, fv);

      // Bilingual featres:

      if((opt.bilingualH2C)) {
        DependencyInstance srcInstance = instance.getSourceInstance();
        //System.err.printf("pipe(%s) instance(%s) srcInstance(%s)\n", this, instance, srcInstance);

        int[] childSource = instance.getSource(childIndex);
        int[] headSource = instance.getSource(headIndex);

        //for(int c : childSource)
        //  System.err.printf("pipe-align: [%s] [%s]\n", forms_childIndex, srcInstance.getForm(c));

        // Source unigram features:
        if(opt.bilingualC) {
          for(int sci : childSource) {
            for (int j = 0; j < srcInstance.getFeats(sci).length; ++j) {
              sb.setLength(0);
              sb.append("FF").append(j);
              addTwoObsFeatures(sb.toString(),
                   instance.getForm(headIndex),
                   instance.getPOSTag(headIndex),
                   srcInstance.getForm(sci),
                   srcInstance.getFeat(sci,j),
                   attDist, fv);

              sb.setLength(0);
              sb.append("LF").append(j);
              addTwoObsFeatures(sb.toString(),
                   instance.getLemma(headIndex),
                   instance.getPOSTag(headIndex),
                   srcInstance.getLemma(sci),
                   srcInstance.getFeat(sci,j),
                   attDist, fv);
            }
          }
        }

        if(opt.bilingualH) {
          for (int shi : headSource) {
            //System.err.printf("pipe(%s) instance(%s) srcInstance(%s) srcInstanceLen(%d) headSource(%d)\n",
            //  this, instance, srcInstance, srcInstance.length(), shi);
            for (int j = 0; j < srcInstance.getFeats(shi).length; ++j) {
              sb.setLength(0);
              sb.append("FF").append(j);
              addTwoObsFeatures(sb.toString(),
                   instance.getForm(childIndex),
                   instance.getPOSTag(childIndex),
                   srcInstance.getForm(shi),
                   srcInstance.getFeat(shi,j),
                   attDist, fv);

              sb.setLength(0);
              sb.append("LF").append(j);
              addTwoObsFeatures(sb.toString(),
                   instance.getLemma(childIndex),
                   instance.getPOSTag(childIndex),
                   srcInstance.getLemma(shi),
                   srcInstance.getFeat(shi,j),
                   attDist, fv);
            }
          }
        }

        boolean hFeature = false;
        boolean sameAlign = false;

        boolean hUnaligned = headSource.length == 0;
        boolean cUnaligned = childSource.length == 0;

        // Source bigram features:
        for(int sci : childSource) {
          for(int shi : headSource) {
            if(sci == shi)
              sameAlign = true;
            String[] hpf = srcInstance.getPairwiseFeats(shi, sci);
            if(hpf != null) {
              hFeature = true;
              for (String fName : hpf) {
                sb.setLength(0);
                sb.append("CFh").append(fName);
                String id = sb.toString();
                boolean conjFeature = Character.isUpperCase(id.charAt(0));
                switch(opt.bilingualDetail) {
                  case 2: case 1:
                    if(conjFeature)
                      addTwoObsFeatures(id,
                         forms_headIndex, posA_headIndex,
                         forms_childIndex, posA_headIndex, attDist, fv);
                  case 0:
                    key.clear();
                    key.add(id);
                    add(key,fv);
                }
              }
            }
          }
        }
        // Head and child align to the same word:
        if(sameAlign) {
          key.clear(); key.add("CFsame"); add(key, fv);
        }
        // Both head and child word unaligned:
        if(hUnaligned && cUnaligned) {
          key.clear(); key.add("U2CF"); add(key, fv);
        }
        // One of the two words unaligned:
        if(hUnaligned || cUnaligned) {
          if(hUnaligned) { key.clear(); key.add("UCFh"); add(key, fv); }
          if(cUnaligned) { key.clear(); key.add("UCFc"); add(key, fv); }
        }
        // Both words unaligned, but with no path linking them in source:
        else {
          if(!hFeature) {
            key.clear(); key.add("NCF"); add(key, fv);
          }
        }

        // Add in features from the feature lists. It assumes
        // the feature lists can have different lengths for
        // each item. For example, nouns might have a
        // different number of morphological features than
        // verbs.

        // Get |H| x |C| features, where H is a set of attributes of the head
        // and C is a set of attributes of the child.
        /*
        for (int i = 0; i < instance.getFeats(headIndex).length; i++) {
          for (int j = 0; j < instance.getFeats(childIndex).length; j++) {
            sb.setLength(0);
            sb.append("FF").append(i).append("*").append(j);
            addTwoObsFeatures(sb.toString(),
                 instance.getForm(headIndex),
                 instance.getFeat(headIndex,i),
                 instance.getForm(childIndex),
                 instance.getFeat(childIndex,j),
                 attDist, fv);

            sb.setLength(0);
            sb.append("LF").append(i).append("*").append(j);
            addTwoObsFeatures(sb.toString(),
                 instance.getLemma(headIndex),
                 instance.getFeat(headIndex,i),
                 instance.getLemma(childIndex),
                 instance.getFeat(childIndex,j),
                 attDist, fv);
          }
        }
        */
      }
    }
  }

  private void addLinearPOSFeatures(DependencyInstance inst,
                                    int first, int second,
                                    String attachDistance, boolean coarse,
                                    FeatureVector fv) {

    int len = inst.length();

    String pLeft, pRight, pLeftRight, pRightLeft;
    if(coarse) {
      pLeft = first > 0 ? inst.getCPOSTag(first - 1) : STR;
      pRight = second < len - 1 ? inst.getCPOSTag(second + 1) : END;
      pLeftRight = first < second - 1 ? inst.getCPOSTag(first + 1) : MID;
      pRightLeft = second > first + 1 ? inst.getCPOSTag(second - 1) : MID;
      if(opt.prefixParser) pRight = END;
    } else {
      pLeft = first > 0 ? inst.getPOSTag(first - 1) : STR;
      pRight = second < len - 1 ? inst.getPOSTag(second + 1) : END;
      pLeftRight = first < second - 1 ? inst.getPOSTag(first + 1) : MID;
      pRightLeft = second > first + 1 ? inst.getPOSTag(second - 1) : MID;
      if(opt.prefixParser) pRight = END;
    }

    String firstPOS = coarse ? inst.getCPOSTag(first) : inst.getPOSTag(first);
    String secondPOS = coarse ? inst.getCPOSTag(second) : inst.getPOSTag(second);

    // feature posL posR posMid:
    sb.setLength(0);
    String id = coarse ? "CPOSPC=" : "POSPC=";
    sb.append(id).append(firstPOS).append(secondPOS);
    String featPos = sb.toString();

    if(opt.english) {
      
      // Fast version:
      Set<String> tags = coarse ? inBetweenCPOS : inBetweenPOS;
      if(tags != null) {
        for(String pos : inst.inBetweenPOS(first, second, coarse)) {
          key.clear();
          //System.err.printf("check: [%s] [%s]\n",featPos,pos);
          //key.add(id).add(firstPOS).add(secondPOS).add(pos);
          key.add(featPos).add(pos);
          add(key, fv);
          key.add(attachDistance);
          add(key, fv);
        }
      }
      
    } else {

      // Slow version:
      // Looks only at a small window of posWindowSize right to the first word
      // and at a window of the same size to the left of the second word.
      int mid = (second+first+1)/2;
      int mid1 = Math.min(mid,first+opt.posWindowSize+1);
      int mid2 = Math.max(mid,second-opt.posWindowSize);
      for (int i = first + 1; i < mid1; i++) {
        int offset = i-first;
        String pos = coarse ? inst.getCPOSTag(i) : inst.getPOSTag(i);
        key.clear();
        //key.add(id).add(firstPOS).add(secondPOS).add(pos);
        key.add(featPos).add(pos);
        add(key, fv);
        key.add(attachDistance);
        add(key, fv);
        if(offset < opt.offsetWindowSize) {
          key.add("l").add(Integer.toString(offset));
          add(key, fv);
        }
      }
      for (int i = mid2; i < second; i++) {
        int offset = i-second;
        String pos = coarse ? inst.getCPOSTag(i) : inst.getPOSTag(i);
        key.clear();
        //key.add(id).add(firstPOS).add(secondPOS).add(pos);
        key.add(featPos).add(pos);
        add(key, fv);
        key.add(attachDistance);
        add(key, fv);
        if(-offset < opt.offsetWindowSize) {
          key.add("r").add(Integer.toString(-offset));
          add(key, fv);
        }
      }
    }

    addCorePosFeatures(coarse, pLeft, firstPOS, pLeftRight,
         pRightLeft, secondPOS, pRight, attachDistance, fv);
  }


  private void
  addCorePosFeatures(boolean coarse,
                     String leftOf1, String one, String rightOf1,
                     String leftOf2, String two, String rightOf2,
                     String attachDistance,
                     FeatureVector fv) {

    // feature posL-1 posL posR posR+1
    String prefix = coarse ? "CPOSPT" : "POSPT";

    key.clear();
    key.add(prefix);
    Trie prT = key.n;
    key.add("=").add(leftOf1).add(one).add(two).add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("1=").add(leftOf1).add(one).add(two);
    add(key, fv);
    key.add(rightOf2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2=").add(leftOf1).add(two).add(rightOf2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("3=").add(leftOf1).add(one).add(rightOf2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("4=").add(one).add(two).add(rightOf2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    /////////////////////////////////////////////////////////////
    //sb.setLength(0);
    //sb.append('A').append(prefix);
    //prefix = sb.toString();
    prefix = coarse ? "ACPOSPT" : "APOSPT";

    // feature posL posL+1 posR-1 posR
    key.clear();
    key.add(prefix);
    prT = key.n;
    key.add("1=").add(one).add(rightOf1).add(leftOf2);
		Trie OneEq12 = key.n;
		key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(OneEq12);
    add(key, fv);
    key.add(two);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2=").add(one).add(rightOf1).add(two);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.clear();
    key.reset(prT).add("3=").add(one).add(leftOf2).add(two);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.clear();
    key.reset(prT).add("4=").add(rightOf1).add(leftOf2).add(two);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    ///////////////////////////////////////////////////////////////
    //sb.setLength(0);
    //sb.append('B').append(prefix);
    //prefix = sb.toString();
    prefix = coarse ? "BACPOSPT" : "BAPOSPT";

    //// feature posL-1 posL posR-1 posR
    key.clear();
    key.add(prefix);
    prT = key.n;
    key.add("1=").add(leftOf1).add(one).add(leftOf2).add(two);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    //// feature posL posL+1 posR posR+1
    key.reset(prT).add("2=").add(one).add(rightOf1).add(two).add(rightOf2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);
  }


  // Add features for two items, each with two observations, e.g. head,
  // head pos, child, and child pos.
  // The use of StringBuilders is not yet as efficient as it could
  // be, but this is a start. (And it abstracts the logic so we can
  // add other features more easily based on other items and
  // observations.)
  private void addTwoObsFeatures(String prefix,
                                       String item1F1, String item1F2,
                                       String item2F1, String item2F2,
                                       String attachDistance,
                                       FeatureVector fv) {

    key.clear();
    key.add(prefix);
    Trie prT = key.n;
    key.add("2FF1=").add(item1F1);
		Trie twoFF1 = key.n;
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);
    
    key.reset(twoFF1).add(item1F2);
    Trie twoFF1p1F2 = key.n;
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(twoFF1p1F2).add(item2F2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(twoFF1p1F2).add(item2F2).add(item2F1);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2FF2=").add(item1F1).add(item2F1);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2FF3=").add(item1F1).add(item2F2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2FF4=").add(item1F2).add(item2F1);
		Trie twoFF4 = key.n;
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(twoFF4).add(item2F2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2FF5=").add(item1F2).add(item2F2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2FF6=").add(item2F1).add(item2F2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2FF7=").add(item1F2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2FF8=").add(item2F1);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);

    key.reset(prT).add("2FF9=").add(item2F2);
    add(key, fv);
    key.add("*").add(attachDistance);
    add(key, fv);
  }

  public void addLabeledFeatures(DependencyInstance instance,
                                 int word,
                                 String type,
                                 boolean attR,
                                 boolean childFeatures,
                                 FeatureVector fv) {

    if (!labeled)
      return;

    String att;
    if (attR)
      att = "RA&";
    else
      att = "LA&";

    att += childFeatures;

    String w = instance.getForm(word);
    String wP = instance.getPOSTag(word);

    String wPm1 = word > 0 ? instance.getPOSTag(word - 1) : STR;
    String wPp1 = word < instance.length() - 1 ? instance.getPOSTag(word + 1) : END;
    if(opt.prefixParser) wPp1 = END;

    key.clear().add("NTS1=").add(type).add("&").add(att);
    add(key, fv);
    key.clear().add("ANTS1=").add(type);
    add(key, fv);
    for (int i = 0; i < 2; i++) {
      String suff = i < 1 ? "&" + att : "";
      suff = "&" + type + suff;

      key.clear().add("NTH=").add(w).add(wP).add(suff);
      add(key, fv);
      key.clear().add("NTI=").add(wP).add(suff);
      add(key, fv);
      key.clear().add("NTIA=").add(wPm1).add(wP).add(suff);
      add(key, fv);
      key.clear().add("NTIB=").add(wP).add(wPp1).add(suff);
      add(key, fv);
      key.clear().add("NTIC=").add(wPm1).add(wP).add(wPp1).add(suff);
      add(key, fv);
      key.clear().add("NTJ=").add(w).add(suff); //this
      add(key, fv);
    }
  }

  // Write an instance to an output stream for later reading.
  protected void writeInstance(DependencyInstance instance, ObjectOutputStream out) {

    int instanceLength = instance.length();

    try {

      for (int w1 = 0; w1 < instanceLength; w1++) {
        for (int w2 = w1 + 1; w2 < instanceLength; w2++) {
          for (int ph = 0; ph < 2; ph++) {
            boolean attR = ph == 0;
            FeatureVector prodFV = new FeatureVector();
            addCoreFeatures(instance, w1, w2, attR, prodFV);
            out.writeObject(prodFV.keys());
          }
        }
      }
      out.writeInt(-3);

      if (labeled) {
        for (int w1 = 0; w1 < instanceLength; w1++) {
          for (String type : types) {
            for (int ph = 0; ph < 2; ph++) {
              boolean attR = ph == 0;
              for (int ch = 0; ch < 2; ch++) {
                boolean child = ch == 0;
                FeatureVector prodFV = new FeatureVector();
                addLabeledFeatures(instance, w1,
                     type, attR, child, prodFV);
                out.writeObject(prodFV.keys());
              }
            }
          }
        }
        out.writeInt(-3);
      }

      out.writeObject(instance.getFeatureVector().keys());
      out.writeInt(-4);

      out.writeObject(instance);
      out.writeInt(-1);

      out.reset();

    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  // Get product FeatureVector
  // warning: does not support labeled features
  protected void getProdFV(DependencyInstance instance) {

    int len = instance.length();
    FeatureVector[][] fvs = new FeatureVector[len][len];
    
    for (int w1 = 0; w1 < len; w1++) {
      for (int w2 = w1 + 1; w2 < len; w2++) {
        for (int ph = 0; ph < 2; ph++) {
          boolean attR = ph == 0;
          FeatureVector prodFV = new FeatureVector();
          addCoreFeatures(instance, w1, w2, attR, prodFV);
          fvs[w1][w2] = prodFV;
        }
      }
    }
  }

  // Read an instance from an input stream.
  public DependencyInstance readInstance(ObjectInputStream in,
                                         DependencyInstanceFeatures f,
                                         Parameters params) throws IOException {

    int length = f.length();
    try {
      // Get production crap.
      for (int w1 = 0; w1 < length; w1++) {
        for (int w2 = w1 + 1; w2 < length; w2++) {
          for (int ph = 0; ph < 2; ph++) {
            FeatureVector prodFV = new FeatureVector((int[]) in.readObject());
            double prodProb = params.getScore(prodFV);
            f.setFVS(w1, w2, ph, prodFV);
            f.probs[w1][w2][ph] = prodProb;
          }
        }
      }
      int last = in.readInt();
      if (last != -3) {
        throw new RuntimeException("Error reading file.");
      }

      if (labeled) {
        for (int w1 = 0; w1 < length; w1++) {
          for (int t = 0; t < types.length; t++) {
            for (int ph = 0; ph < 2; ph++) {
              for (int ch = 0; ch < 2; ch++) {
                FeatureVector prodFV = new FeatureVector((int[]) in.readObject());
                double nt_prob = params.getScore(prodFV);
                f.setNT_FVS(w1, t, ph, ch, prodFV);
                //[w1][t][ph][ch] = prodFV;
                f.nt_probs[w1][t][ph][ch] = nt_prob;
              }
            }
          }
        }
        last = in.readInt();
        if (last != -3) {
          throw new RuntimeException("Error reading file.");
        }
      }

      FeatureVector nfv = new FeatureVector((int[]) in.readObject());
      last = in.readInt();
      if (last != -4) {
        throw new RuntimeException("Error reading file.");
      }

      DependencyInstance marshalledDI;
      marshalledDI = (DependencyInstance) in.readObject();
      marshalledDI.setFeatureVector(nfv);

      last = in.readInt();
      if (last != -1) {
        throw new RuntimeException("Error reading file.");
      }

      return marshalledDI;

    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Error reading file.");
    }
  }

  public void clearCache() {
    cache.clear();
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    System.err.println("cloned: "+this);
    DependencyPipe pipe = (DependencyPipe)super.clone();
    pipe.sb = new StringBuilder(50);
    pipe.key = new TrieKey(dataAlphabet);
    pipe.cache = new THashMap<DependencyPair, Double>();
    return pipe;
  }
}
