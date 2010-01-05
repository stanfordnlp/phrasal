package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.ChineseTreebankLanguagePack;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * A standalone feature extractor.<br>
 * Usage: <code>java edu.stanford.nlp.mt.reranker.LegacyFeatureExtractor (datadescriptor) (outputFeatureFile) [options for what features to extract]</code><br>
 * example:<br>
 * <code>java -mx3g edu.stanford.nlp.mt.reranker.LegacyFeatureExtractor scr/features/datadescriptors/0.txt   scr/features/sets/extractAlignedWordPairsBigram/0.feats -extractAlignedWordPairsBigram</code>
 * <h3>Data Descriptor</h3>
 * The data descriptor should look like:<br>
 * <code>/u/nlp/data/gale/n-best-reranking/reranker/mt03/scr/features/datadescriptors/0.txt</code><br>
 * Valid fields in the data descriptor:
 * <ul>
 * <li> LoadOffset: by default, the data number starts at 0. If there's an offset, use "LoadOffset" to override it.
 * For example: <code>LoadOffset: 100</code>
 * <li> TrainRange: For example: <code>Trainrange: 0-89</code>.
 * If LoadOffset was set to 100, then the traning examples are actually 100 to 189
 * <li> SourceTrees: where the source trees are. For example: /u/nlp/data/gale/n-best-reranking/reranker/mt03/scr/srcSents/trees/
 * <li> TargetTrees: where the target trees are. For example: /u/nlp/data/gale/n-best-reranking/reranker/mt03/scr/tgtSents/trees/
 * <li> Alignments: the location of the alignments. For example: /u/nlp/data/gale/n-best-reranking/reranker/mt03/scr/tgtSents/alignments/
 * </ul>
 * <h3>Options for features to extract</h3>
 * <ul>
 * <li> -extractEnZhPathFeatures
 * <li> -extractEnZhPathFeatures_ZhEn
 * <li> -extractAlignedWordPairFeatures
 * <li> -extractAlignedTagPairFeatures
 * <li> -extractAlignedWordPairsBigram
 * <li> -extractAlignedWordPairsBigram_ZhEn
 * <li> -extractPathFeatures
 * </ul>
 *
 * @author cer (daniel.cer@colorado.edu)
 * @author Pi-Chuan Chang
 */

public class LegacyFeatureExtractor implements Serializable {

  private static final long serialVersionUID = 1L;

  static final boolean DEBUG = false; 
  static final double DEFAULT_TRAIN_PERCENT = 0.80;
  static final double DEFAULT_DEV_PERCENT = 0.10;
  static final String AUTO_UNIFY_TO_PROP = "lfe.unifyTo";

  transient long loadTime;

  public LegacyFeatureExtractor() {
    ;
  }

  int[] trainRange;
  int[] devRange;
  int[] evalRange;

  List<CompactHypothesisList> lchl;

  public List<CompactHypothesisList> getSubset(int[] range) {
    List<CompactHypothesisList> sublchl = new ArrayList<CompactHypothesisList>();
    for (int i = range[0]; i <= range[1]; i++) {
      sublchl.add(lchl.get(i));
    }
    return sublchl;
  }

  public int[] getTrainRange() {
    return trainRange;
  }

  public int[] getDevRange() {
    return devRange;
  }

  public int[] getEvalRange() {
    return evalRange;
  }

  public List<CompactHypothesisList> getTrainingSet() {
    return getSubset(trainRange);
  }

  public List<CompactHypothesisList> getDevSet() {
    return getSubset(devRange);
  }

  public List<CompactHypothesisList> getEvalSet() {
    return getSubset(evalRange);
  }

  static public String getMandatoryProperty(Properties p, String name) {
    if (!p.containsKey(name)) {
      throw new RuntimeException("Error: descriptor is missing mandatory " + "property '" + name + "'");
    }
    return p.getProperty(name).replaceAll("\\s+$", "");
  }

  static public int[] newRange(int start, int end) {
    int[] r = new int[2];
    r[0] = start;
    r[1] = end;
    return r;
  }

  static public int[] getRange(Properties p, String name) {
    String r;
    if ((r = p.getProperty(name)) == null) {
      return null;
    }

    String[] fields = r.split("-");
    if (fields.length != 2) {
      throw new RuntimeException("Error: invalid range '" + r + "' found " + "when parsing data set descriptor");
    }
    return newRange(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]));
  }

  static public LegacyFeatureExtractor load(String dataSetDescriptor, PrintStream pstrm, Properties p) throws IOException {
    System.out.printf("Loading data set specified by descriptor: '%s'\n", dataSetDescriptor);

    Exception priorException = null;
    String priorStackTrace = null;
    LegacyFeatureExtractor ds = null;
    try {
      /*
      // First, attempt to load a serialized LegacyFeatureExtractor 
      // from the filename given by dataSetDescriptor
      try {
        ds = loadSerialized(dataSetDescriptor);
        System.out.printf("Data set successfully loaded as "+
          "serialized .mt.reranker.LegacyFeatureExtractor\n");
        System.out.printf("Load time: %.3f s\n", ds.loadTime*1.0e-9);
        return ds;
      } catch (IOException e) {
        // okay, so we're probably not dealing with a serializd 
        // LegacyFeatureExtractor as this is probably a 
        // "Not in GZIP format" IOException
        priorException = e; 
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw); pw.close();
        priorStackTrace = sw.toString();
      } 
      */

      // Now, we'll try treating the file specified by dataSetDescriptor 
      // as a LegacyFeatureExtractor plain text descriptor
      ds = loadByTextDescriptor(dataSetDescriptor, pstrm, p);
      System.out.printf("Data set successfully loaded as plain text data set description\n");
      System.out.printf("Load time: %.3f s\n", ds.loadTime * 1.0e-9);

      // If requested, unify the data set to a single binary file 
      String unifyFilename = System.getProperty(AUTO_UNIFY_TO_PROP);
      if (unifyFilename != null) {
        ds.write(unifyFilename);
      } else {
        System.out.printf("---\n" + "Did you know you could dramatically reduce future load times by \n" + "unifying your data sets just by setting the system property '%s'?\n" + "However, only do so if you're not actively doing feature engineering." + "\n---\n", AUTO_UNIFY_TO_PROP);
      }

      return ds;
    } catch (Exception e) {
      System.err.printf("Can't load '%s' as either a " + "serialized .gale.LegacyFeatureExtractor or as a plain text " + "descriptor.\n", dataSetDescriptor);
      if (priorException != null) {
        System.err.printf("Deserialization error: %s\n", priorException);
        System.err.println(priorStackTrace);
        System.err.printf("Plain text descriptor error: %s\n", e);
        e.printStackTrace();
      } else {
        System.err.printf("Error: %s\n", e);
        e.printStackTrace();
      }
      throw new RuntimeException("Error: unable to load data set given by descriptor '" + dataSetDescriptor + "'");
    }
  }

  static LegacyFeatureExtractor loadSerialized(String filename) throws IOException, ClassNotFoundException {

    long loadTime = -System.nanoTime();

    ObjectInputStream oistrm = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(filename))));
    LegacyFeatureExtractor ds = (LegacyFeatureExtractor) oistrm.readObject();
    oistrm.close();

    ds.loadTime = loadTime += System.nanoTime();
    return ds;
  }

  public void write(String filename) throws IOException {
    ObjectOutputStream oostrm = new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(filename))));
    oostrm.writeObject(this);
    oostrm.close();
  }

  static private void displayMatches(String[] list) {
    System.err.printf("Matches (count: %d):\n", list.length);
    for (String item : list) {
      System.err.printf("\t%s\n", item);
    }
  }

  private static File newFileIfNonNull(String filename) throws IOException {
    if (filename == null) {
      return null;
    }
    return new File(filename);
  }

  static private LegacyFeatureExtractor loadByTextDescriptor(String filename, PrintStream pstrm, Properties prop) throws IOException {

    long loadTime = -System.nanoTime();

    LegacyFeatureExtractor ds = new LegacyFeatureExtractor();

    Properties p = new Properties();
    FileInputStream istrm = new FileInputStream(filename);
    p.load(istrm);
    istrm.close();

    String srcTreesDirName = p.getProperty("SourceTrees");
    String treesDirName = p.getProperty("TargetTrees");
    //String bleuDirName = getMandatoryProperty(p, "TargetBleus");
    String alignmentDirName = p.getProperty("Alignments");
    String posDirName = p.getProperty("POSTags");

    int numTranslations = Integer.parseInt(getMandatoryProperty(p, "Translations"));

    String strLoadOffset = p.getProperty("LoadOffset");
    int loadOffset = 0;
    if (strLoadOffset != null) {
      loadOffset = Integer.parseInt(strLoadOffset);
    }

    if ((ds.trainRange = getRange(p, "TrainRange")) == null) {
      ds.trainRange = newRange(0, (int) (DEFAULT_TRAIN_PERCENT * numTranslations) - 1);
    }
    if ((ds.devRange = getRange(p, "DevRange")) == null) {
      ds.devRange = newRange(ds.trainRange[1], Math.min(numTranslations - 1, ds.trainRange[1] + (int) (DEFAULT_DEV_PERCENT * numTranslations) - 1));
    }
    if ((ds.evalRange = getRange(p, "EvalRange")) == null) {
      ds.evalRange = newRange(ds.devRange[1], numTranslations - 1);
    }

    File srcTreesDir = newFileIfNonNull(srcTreesDirName);
    File treesDir = newFileIfNonNull(treesDirName);
    File posDir = newFileIfNonNull(posDirName);
    //File bleuDir = newFileIfNonNull(bleuDirName);

    ds.lchl = new ArrayList<CompactHypothesisList>();

    for (int i = loadOffset; i < numTranslations + loadOffset; i++) {
      FilenameFilter filter;

      String tname = null;
      String pname = null;
      String[] names;
      if (treesDir != null) {
        filter = new myFilenameFilter(i + ".trees");
        names = treesDir.list(filter);
        if (names.length != 1) {
          displayMatches(names);
          throw new RuntimeException("More than 1 files or no files end with " + i + ".trees: " + StringUtils.join(names, "//"));
        }
        tname = treesDirName + "/" + names[0];
      }
      if (posDir != null) {
        filter = new myFilenameFilter(i + ".sent.pos");
        names = posDir.list(filter);
        if (names.length != 1) {
          displayMatches(names);
          throw new RuntimeException("More than 1 files or no files end with " + i + ".sent.pos: " + StringUtils.join(names, "//"));
        }
        pname = posDirName + "/" + names[0];
      }

//
//      filter = new myFilenameFilter(i + ".");
//      names = bleuDir.list(filter);
//      if (names.length != 1) {
//        displayMatches(names);
//        throw new RuntimeException("More than 1 files or no files end with " + i + ".bleus");
//      }
//      String bname = bleuDir + "/" + names[0];
//
      System.err.println("Start reading " + i);
      // TODO: this is not very robust here, because it assumes the
      // candidate trees and alignments match in number
      List<Candidate> cands = readCandidates(tname, pname);

      // read in src Tree
      Tree srcTree = null;
      if (srcTreesDir != null) {
        filter = new myFilenameFilter(i + ".trees");
        names = srcTreesDir.list(filter);
        if (names.length != 1) {
          throw new RuntimeException("More than 1 files or no files end with " + i + ".trees: " + StringUtils.join(names, "//"));
        }
        tname = srcTreesDirName + "/" + names[0];
        BufferedReader br = new BufferedReader(new FileReader(tname));
        srcTree = readTree(br);
        br.close();
      }

      List<Alignment> als = null;
      if (alignmentDirName != null) {
        String aname = alignmentDirName + "/" + i + ".aligns";
        als = Alignment.readAlignments(aname);
        assert cands.size() == als.size();
      }

      //spstrm.printf("# scores generated during legacy feature extraction of %s\n", filename);
      //spstrm.printf("# Created: %s\n", new Date());
      extractAndWrite(srcTree, cands, als, pstrm, prop, loadOffset);
    }

    ds.loadTime = loadTime += System.nanoTime();
    return ds;
  }

  static int dataPt = 0;


  static PrintWriter depsPW;

  private static void extractAndWrite(Tree chT, List<Candidate> cands, List<Alignment> als, PrintStream pstrm, Properties prop, int loadOffset) throws IOException {
    if (cands.size() != als.size()) {
      throw new RuntimeException("size not match.");
    }

    GrammaticalStructure gs = null;
    Collection<TypedDependency> deps;

    if (depsPW==null && prop.containsKey("DepsFile")) {
      depsPW = new PrintWriter(prop.getProperty("DepsFile"));
    }

    if (depsPW!=null) {
      depsPW.println("Source Sent: "+StringUtils.join(chT.taggedYield(new ArrayList<TaggedWord>()), " "));
      Filter<String> puncWordFilter = Filters.acceptFilter();
      TreebankLanguagePack tlp = new ChineseTreebankLanguagePack();
      GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(puncWordFilter);
      gs = gsf.newGrammaticalStructure(chT);
      if (depsPW!=null) {
        deps = gs.typedDependencies();
        for (TypedDependency d : deps) {
          depsPW.println(d);
        }
        depsPW.println();
      }
    }


    //spstrm.printf("\n# data pt: %d\n", dataPt+loadOffset);
    for (int hypId = 0; hypId < cands.size(); hypId++) {
      System.err.println("DEBUG: hypId="+hypId);
      Tree enT = cands.get(hypId).getTree();
      Alignment alignment = als.get(hypId);

      ClassicCounter<String> feats = new ClassicCounter<String>();
      if(prop.containsKey("extractEnZhPathFeatures")) {
        if (gs == null) {
          //gs = new ChineseGrammaticalStructure(chT);
          Filter<String> puncWordFilter = Filters.acceptFilter();
          TreebankLanguagePack tlp = new ChineseTreebankLanguagePack();
          GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(puncWordFilter);
          gs = gsf.newGrammaticalStructure(chT);

          deps = gs.typedDependencies();
          for (TypedDependency d : deps) {
            System.err.println("DEBUG: chT: " + d);
          }
        }
        Counters.addInPlace(feats, DependencyUtils.extractEnZhPathFeatures(enT, gs, alignment, true));
      }

      if(prop.containsKey("extractEnZhPathFeatures_ZhEn")) {
        if (gs == null) {
          //gs = new ChineseGrammaticalStructure(chT);
          Filter<String> puncWordFilter = Filters.acceptFilter();
          TreebankLanguagePack tlp = new ChineseTreebankLanguagePack();
          GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(puncWordFilter);
          gs = gsf.newGrammaticalStructure(chT);

          deps = gs.typedDependencies();
          for (TypedDependency d : deps) {
            System.err.println("DEBUG: chT: " + d);
          }
        }
        Counters.addInPlace(feats, DependencyUtils.extractEnZhPathFeatures(enT, gs, alignment, false));
      }

      // these feature don't really have directionality...
      if(prop.containsKey("extractAlignedWordPairFeatures"))
        Counters.addInPlace(feats, extractAlignedWordPairFeatures(enT, chT, alignment));
      if(prop.containsKey("extractAlignedTagPairFeatures"))
        Counters.addInPlace(feats, extractAlignedTagPairFeatures(enT, chT, alignment));

      if(prop.containsKey("extractAlignedWordPairsBigram"))
        Counters.addInPlace(feats, extractAlignedWordPairsBigram(enT, chT, alignment, true));
      if(prop.containsKey("extractAlignedWordPairsBigram_ZhEn"))
        Counters.addInPlace(feats, extractAlignedWordPairsBigram(enT, chT, alignment, false));

      if(prop.containsKey("extractContiguousAlignedToFeatures"))
        Counters.addInPlace(feats, extractContiguousAlignedToFeatures(enT, chT, alignment, true));

      if(prop.containsKey("extractPathFeatures") || prop.containsKey("DepsFile")) {

        if (gs == null) {
          Filter<String> puncWordFilter = Filters.acceptFilter();
          TreebankLanguagePack tlp = new ChineseTreebankLanguagePack();
          GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(puncWordFilter);
          gs = gsf.newGrammaticalStructure(chT);
        }

        Filter<String> puncWordFilter;
        puncWordFilter = Filters.acceptFilter();
        TreebankLanguagePack tlp = new PennTreebankLanguagePack();
        GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(puncWordFilter);
        GrammaticalStructure enGs = gsf.newGrammaticalStructure(enT);

        if (depsPW!=null) {
          depsPW.println("Target Sent["+hypId+"]: "+StringUtils.join(enT.taggedYield(new ArrayList<TaggedWord>()), " "));
          deps = enGs.typedDependencies();
          for (TypedDependency d : deps) {
            depsPW.println(d);
          }
          depsPW.println();
        }
        if (prop.containsKey("extractPathFeatures")) {
          int lengLimit = Integer.parseInt(prop.getProperty("extractPathFeatures", ""+Integer.MAX_VALUE));
          Counters.addInPlace(feats, DependencyUtils.extractPathFeatures(enGs, gs, alignment, lengLimit));
        }
      }

      pstrm.printf("%d,%d", dataPt+loadOffset, hypId);
      for (String feat : feats) {
        pstrm.printf(" %s:%f", normFeature(feat), feats.getCount(feat));
      }
      pstrm.println();
//      double score = cands.get(hypId).getBleu();
//      spstrm.printf("%d,%d %f\n", dataPt+loadOffset, hypId, score);
    }
    if (depsPW!=null) {
      depsPW.println("------------------------------");
    }
    dataPt++;
    System.err.println("HIT_COUNT=" + hit_cnt);
    System.err.println("ALL_COUNT=" + all_cnt);
  }


  private static ClassicCounter<String> extractAlignedWordPairFeatures(Tree enT, Tree chT, Alignment align) {
    ClassicCounter<String> features = new ClassicCounter<String>();

    List<TaggedWord> enWords = enT.yield(new ArrayList<TaggedWord>());
    List<TaggedWord> chWords = chT.yield(new ArrayList<TaggedWord>());

    //for (int enIdx = 0; enIdx < align.sizeEnZh(); enIdx++) {
    for(int enIdx : align.get(true).keySet()) {
      String enWord = enWords.get(enIdx).toString();
      List<Integer> al = align.get(enIdx, true);
      for (int chIdx : al) {
        StringBuilder sb = new StringBuilder();
        sb.append("EnZh|");
        sb.append("AWP-");
        sb.append(enWord);
        sb.append("-");
        String chWord = chWords.get(chIdx).toString();
        sb.append(chWord);
        features.incrementCount(sb.toString());
      }
    }

    return features;
  }


  private static ClassicCounter<String> extractAlignedTagPairFeatures(Tree enT, Tree chT, Alignment align) {
    ClassicCounter<String> features = new ClassicCounter<String>();
    //List<List<Integer>> alignments = align.alignments;
    //System.err.println(alignments);

    List<TaggedWord> enWords = enT.taggedYield(new ArrayList<TaggedWord>());
    List<TaggedWord> chWords = chT.taggedYield(new ArrayList<TaggedWord>());

    //for (int enIdx = 0; enIdx < align.sizeEnZh(); enIdx++) {
    for(int enIdx : align.get(true).keySet()) {
      String enWord = enWords.get(enIdx).tag().toString();
      List<Integer> al = align.get(enIdx, true);
      for (int chIdx : al) {
        StringBuilder sb = new StringBuilder();
        sb.append("EnZh|");
        sb.append("ATP-");
        sb.append(enWord);
        sb.append("-");
        String chWord = ((TaggedWord) chWords.get(chIdx)).tag().toString();
        sb.append(chWord);
        features.incrementCount(sb.toString());
      }
    }

    return features;
  }


  private static ClassicCounter<String> extractAlignedWordPairsBigram(Tree enT, Tree chT, Alignment align, boolean EnZh) {
    ClassicCounter<String> features = new ClassicCounter<String>();
    //List<List<Integer>> alignments = align.alignments;
    //System.err.println(alignments);

    List<TaggedWord> enWords = enT.taggedYield(new ArrayList<TaggedWord>());
    List<TaggedWord> chWords = chT.taggedYield(new ArrayList<TaggedWord>());

    //for (int enIdx = 0; enIdx < align.sizeEnZh(); enIdx++) {
    for(int enIdx : align.get(EnZh).keySet()) {
      int nextEnIdx = enIdx + 1;

      List<Integer> cur_al = align.get(enIdx, EnZh);
      List<Integer> next_al = align.get(nextEnIdx, EnZh);

      if (next_al == null) {
        break;
      }


      // NOTE: for now, consider only English words that mapped to 1 Chinese word
      if (cur_al.size() == 1 && next_al.size() == 1) {
        hit_cnt++;
        int chPos = cur_al.get(0);
        int nextChPos = next_al.get(0);
        TaggedWord chTW = ((TaggedWord) chWords.get(chPos));
        TaggedWord nextChTW = ((TaggedWord) chWords.get(nextChPos));

        StringBuilder sbW = new StringBuilder();
        if (EnZh) sbW.append("EnZh|"); else sbW.append("ZhEn|");
        sbW.append("AWPB-");
        sbW.append(enWords.get(enIdx).word());
        sbW.append("-");
        sbW.append(enWords.get(nextEnIdx).word());
        sbW.append("-");
        sbW.append(chTW.word());
        sbW.append("-");
        sbW.append(nextChTW.word());
        features.incrementCount(sbW.toString());

        StringBuilder sbT = new StringBuilder();
        if (EnZh) sbT.append("EnZh|"); else sbT.append("ZhEn|");
        sbT.append("ATPB-");
        sbW.append(enWords.get(enIdx).tag());
        sbW.append("-");
        sbW.append(enWords.get(nextEnIdx).tag());
        sbW.append("-");
        sbT.append(chTW.tag());
        sbT.append("-");
        sbT.append(nextChTW.tag());
        features.incrementCount(sbT.toString());
      }
      all_cnt++;
    }
    return features;
  }


  static int hit_cnt = 0;
  static int all_cnt = 0;

  private static ClassicCounter<String> extractContiguousAlignedToFeatures(Tree enT, Tree chT, Alignment align, boolean EnZh) {
    ClassicCounter<String> features = new ClassicCounter<String>();

    List<TaggedWord> enWords = enT.taggedYield(new ArrayList<TaggedWord>());
    List<TaggedWord> chWords = chT.taggedYield(new ArrayList<TaggedWord>());

    //for (int enIdx = 0; enIdx < align.sizeEnZh(); enIdx++) {
    for(int enIdx : align.get(EnZh).keySet()) {
      int nextEnIdx = enIdx + 1;

      List<Integer> cur_al = align.get(enIdx, EnZh);
      List<Integer> next_al = align.get(nextEnIdx, EnZh);

      if (next_al == null) {
        break;
      }

      if (cur_al.size() == 1 && next_al.size() == 1) {
        hit_cnt++;
        int chPos = cur_al.get(0);
        int nextChPos = next_al.get(0);
        TaggedWord enTW = ((TaggedWord) enWords.get(enIdx));
        TaggedWord nextEnTW = ((TaggedWord) enWords.get(nextEnIdx));
        TaggedWord chTW = ((TaggedWord) chWords.get(chPos));
        TaggedWord nextChTW = ((TaggedWord) chWords.get(nextChPos));

        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        if (EnZh) {
          sb1.append("EnZh|");
          sb2.append("EnZh|");
        } else {
          sb1.append("ZhEn|");
          sb2.append("ZhEn|");
        }
        // contiguous tags: [eT0 eT1] --> [cT0'] [cT1'] (with distance pos(cT1')-pos(cT0'))
        sb1.append("CEAD-T-");
        sb1.append("CEAD-W-");
        features.incrementCount(decideFeature(sb1.toString(), enTW.tag(), nextEnTW.tag(), chTW.tag(), nextChTW.tag(), nextChPos - chPos));
        // contiguous words: [eW0 eW1] --> [cW0'] [cW1'] (with distance pos(cT1')-pos(cT0'))
        features.incrementCount(decideFeature(sb2.toString(), enTW.word(), nextEnTW.word(), chTW.word(), nextChTW.word(), nextChPos - chPos));
      }
      all_cnt++;
    }
    return features;
  }


  // this is used in extractContiguousAlignedToFeatures only
  private static String decideFeature(String prefix, String en0, String en1, String ch0, String ch1, int distance) {
    StringBuilder sb = new StringBuilder(prefix);
    sb.append("[").append(en0).append(" ").append(en1).append("]");
    sb.append("}=>").append("[").append(ch0).append("]");
    sb.append("[").append(ch1).append("]").append(" ");
    sb.append("(").append(distance).append(")");
    return sb.toString();
  }


  public static class myFilenameFilter implements FilenameFilter {
    String pat;

    myFilenameFilter(String pat) {
      this.pat = pat;
    }

    public boolean accept(File dir, String name) {
      //return (name.endsWith("."+pat) || name.matches(pat));
      return name.startsWith(pat);
    }
  }

  /**
   * only one of tname or pname will be non null!!
   * @param tname file name of the tree file
   * @param pname file name of the POS file
   * @throws IOException
   */
  public static List<Candidate> readCandidates(String tname, String pname) throws IOException {
    BufferedReader br = (tname != null ? new BufferedReader(new FileReader(tname)) : null);
    BufferedReader posBr = (pname != null ? new BufferedReader(new FileReader(pname)) : null);

    List<Candidate> cands = new ArrayList<Candidate>();
    Tree t = null;
    List<TaggedWord> tws = null;

    if (br!=null) {
      while ((t = readTree(br)) != null) {
        tws = readPOS(posBr);
         Candidate newc = new Candidate(t, tws);
         cands.add(newc);
       }
    } else {
      while((tws=readPOS(br))!=null) {
        Candidate newc = new Candidate(null, tws);
        cands.add(newc);
      }
    }

    if (br != null) br.close();
    if (posBr!=null) posBr.close();
    return cands;
  }

  static List<TaggedWord> readPOS(BufferedReader br){
    if (br==null) return null;
    
    List<TaggedWord> tws = new ArrayList<TaggedWord>();
    String inline;
    try {
      inline = br.readLine();
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("in readPOS");
    }
    if (inline==null) return null;
    String[] strs = inline.split("\\s+");
    for (String s : strs) {
      TaggedWord tw = new TaggedWord();
      tw.setFromString(s);
      tws.add(tw);
    }
    return tws;
  }

  static Double readBLEU(BufferedReader bleusBr) throws IOException {
    String inline = bleusBr.readLine();
    if (inline == null) {
      return null;
    }
    return Double.valueOf(inline);
  }


  static Tree readTree(BufferedReader br) throws IOException {
    Tree t = null;
    StringBuilder tree = new StringBuilder("(");
    String line = null;
    while ((line = br.readLine()) != null) {
      if (line.matches("<tree style=\"penn\">")) {
      } else if (line.matches("</tree>")) {
        try {
          tree.append(")");
          t = Tree.valueOf(tree.toString());
          return t;
        } catch (Throwable e) {
          e.printStackTrace();
        }
      } else {
        //tree += line;
        tree.append(line);
      }
    }
    return t;
  }

  static public void outputPredictedIndices(AbstractOneOfManyClassifier classifier, List<CompactHypothesisList> lchl) throws Exception {
    PrintWriter pr = new PrintWriter(new BufferedWriter(new FileWriter("predictedIndices.txt", false)));
    int[] bestChoices = classifier.getBestPrediction(lchl);
    for (int i : bestChoices) {
      pr.println(i);
    }
    pr.close();
  }

  static private String normFeature(String feat) {
    return feat.replaceAll("\\s", "-").replaceAll(":", "|");
  }

  static public void main(String[] args) throws Exception {
    Map<String, Integer> flagsToNumArgs = new HashMap<String, Integer>();
    flagsToNumArgs.put("extractEnZhPathFeatures", 0);
    flagsToNumArgs.put("extractEnZhPathFeatures_ZhEn", 0);
    flagsToNumArgs.put("extractAlignedWordPairFeatures", 0);
    flagsToNumArgs.put("extractAlignedTagPairFeatures", 0);
    flagsToNumArgs.put("extractAlignedWordPairsBigram", 0);
    flagsToNumArgs.put("extractAlignedWordPairsBigram_ZhEn", 0);
    flagsToNumArgs.put("extractPathFeatures", 1);
    flagsToNumArgs.put("DepsFile", 1);
    Properties p = StringUtils.argsToProperties(args, flagsToNumArgs);
    String[] regargs = p.getProperty("").split(" ");

    if (regargs.length != 2) {
      System.err.printf("Usage:\n\tjava %s (data descriptor) " + "(feature set file) [features]\n\n", (new LegacyFeatureExtractor()).getClass().getName());
      System.exit(-1);
    }
    String dataDescrFn = regargs[0];
    String featureSetFn = regargs[1];
    //String scoresFn = regargs[2];

    System.out.println("Doing legacy feature extraction...");

    PrintStream pstrm = new PrintStream(new GZIPOutputStream(new FileOutputStream(featureSetFn)));
    pstrm.printf("# feature set name: legacy feature extraction of %s\n", dataDescrFn);
    pstrm.printf("# feature set specified:\n");


    Set<String> names = p.stringPropertyNames();
    for (String name : names) {
      if (!name.equals("")) {
        pstrm.printf("#                      : %s\n", name);
      }
    }

    pstrm.printf("# Created: %s\n", new Date());

    //PrintStream spstrm = new PrintStream(new FileOutputStream(scoresFn));

    LegacyFeatureExtractor.load(dataDescrFn, pstrm, p);
    System.out.println("Done.\nWriting out feature set...");
    //System.out.println("Done.\nWriting out scores");
    pstrm.close();
    //spstrm.close();
  }
}
