package mt.syntax.mst.rmcd;

import edu.stanford.nlp.util.ErasureUtils;

import java.io.*;
import java.util.*;

public class FixedSizeDependencyInstance implements Serializable, DependencyInstance, Cloneable {

  private static final long serialVersionUID = 1672845172466149527L;

  private static final String NULL = "<null>";

  private static final boolean VERBOSE = false;

  private DependencyInstance sourceInstance = null;
  private WordAlignment wa = null;

  private FeatureVector fv;
  private String actParseTree;
  private DependencyPipe pipe = null;

  // The various data types. Here's an example from Portuguese:
  //
  // 3  eles ele   pron       pron-pers M|3P|NOM 4    SUBJ   _     _
  // ID FORM LEMMA COURSE-POS FINE-POS  FEATURES HEAD DEPREL PHEAD PDEPREL
  //
  // We ignore PHEAD and PDEPREL for now.

  // FORM: the forms - usually words, like "thought"
  private String[] forms;

  // LEMMA: the lemmas, or stems, e.g. "think"
  private String[] lemmas;

  // COURSE-POS: the coarse part-of-speech tags, e.g."V"
  private String[] cpostags=null;

  // FINE-POS: the fine-grained part-of-speech tags, e.g."VBD"
  private String[] postags;

  // FEATURES: some features associated with the elements separated by "|", e.g. "PAST|3P"
  private String[][] feats;

  // PAIRWISE FEATURES: some features associated with the elements containing "_" and separated by "|", e.g. "P_3|S_2"
  private String[][][] pfeats;

  // HEAD: the IDs of the heads for each element
  private int[] heads;

  // DEPREL: the dependency relations, e.g. "SUBJ"
  private String[] deprels;

  // RELATIONAL FEATURE: relational features that hold between items
  private RelationalFeature[] relFeats;

  private Map<String,Integer>[] posprev, cposprev;

  public FixedSizeDependencyInstance() {
  }

  public FixedSizeDependencyInstance(FixedSizeDependencyInstance source) {
    this.fv = source.fv;
    this.actParseTree = source.actParseTree;
    this.pipe = source.pipe;
  }

  public FixedSizeDependencyInstance(DependencyPipe pipe, String[] forms, FeatureVector fv) {
    this.pipe = pipe;
    this.forms = forms;
    this.fv = fv;
  }

  @SuppressWarnings("unchecked")
  public FixedSizeDependencyInstance(DependencyPipe pipe, String[] forms, String[] postags, FeatureVector fv) {
    this(pipe, forms, fv);
    assert(forms.length == postags.length);
    this.postags = postags;
    this.posprev = new Map[this.postags.length];
    if(pipe != null && pipe.inBetweenPOS != null)
      setPOSBackPointers(this.postags, this.posprev, pipe.inBetweenPOS);
  }

  public FixedSizeDependencyInstance(DependencyPipe pipe,
                            String[] forms, String[] postags,
                            String[] labs, FeatureVector fv) {
    this(pipe, forms, postags, fv);
    assert(forms.length == deprels.length);
    this.deprels = labs;
  }

  @SuppressWarnings("unchecked")
  public FixedSizeDependencyInstance(DependencyPipe pipe,
                            String[] forms, String[] postags,
                            String[] labs, int[] heads) {
    this.pipe = pipe;
    this.forms = forms;
    this.postags = postags;
    this.posprev = new Map[this.postags.length];
    this.deprels = labs;
    this.heads = heads;
    assert(forms.length == postags.length);
    assert(forms.length == labs.length);
    assert(forms.length == heads.length);
    if(pipe != null && pipe.inBetweenPOS != null)
      setPOSBackPointers(this.postags, this.posprev, pipe.inBetweenPOS);
  }

  @SuppressWarnings("unchecked")
  public FixedSizeDependencyInstance(DependencyPipe pipe,
                            String[] forms, String[] lemmas, String[] cpostags,
                            String[] postags, String[][] feats, String[][][] pfeats,
                            String[] labs, int[] heads) {
    this(pipe, forms, postags, labs, heads);
    assert(forms.length == lemmas.length);
    assert(forms.length == cpostags.length);
    assert(forms.length == feats.length);
    assert(forms.length == pfeats.length);
    this.lemmas = lemmas;
    this.cpostags = cpostags;
    this.cposprev = new Map[this.cpostags.length];
    this.feats = feats;
    this.pfeats = pfeats;
    if(pipe != null && pipe.inBetweenCPOS != null)
      setPOSBackPointers(this.cpostags, this.cposprev, pipe.inBetweenCPOS);
  }

  public FixedSizeDependencyInstance(DependencyPipe pipe,
                            String[] forms, String[] lemmas, String[] cpostags,
                            String[] postags, String[][] feats, String[][][] pfeats,
                            String[] labs, int[] heads, RelationalFeature[] relFeats) {
    this(pipe, forms, lemmas, cpostags, postags, feats, pfeats, labs, heads);
    this.relFeats = relFeats;
    if(VERBOSE) {
      System.err.println("forms: "+Arrays.deepToString(forms));
      System.err.println("lemmas: "+Arrays.deepToString(lemmas));
      System.err.println("postags: "+Arrays.toString(postags));
      System.err.println("cpostags: "+Arrays.toString(cpostags));
      System.err.println("labs: "+Arrays.deepToString(labs));
      System.err.println("feats: "+Arrays.deepToString(feats));
      System.err.println("pfeats: "+Arrays.deepToString(pfeats));
      System.err.println("relfeat: "+Arrays.deepToString(relFeats));
    }
  }


  public void setFeatureVector(FeatureVector fv) {
    this.fv = fv;
  }

  public FeatureVector getFeatureVector() {
    return fv;
  }

  public String getParseTree() {
    return actParseTree;
  }

  public String prettyPrint() {
    StringBuilder sb = new StringBuilder();
    for(int i=0; i<forms.length; ++i) {
      if(i>0)
        sb.append(' ');
      int h = heads[i];
      sb.append(forms[i]).append('/').append(postags[i]);
      if(h >= 0)
        sb.append('(').append(forms[h]).append(')');
    }
    return sb.toString();
  }


  public void setParseTree(String t) {
    actParseTree = t;
  }

  public int length() {
    return forms.length;
  }

  public int relFeatLength() {
    return relFeats.length;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(Arrays.toString(forms)).append("\n");
    return sb.toString();
  }

  public boolean hasForms() { return forms != null; }

  public String[] getForms() { return forms; }
  public String[] getLemmas() { return lemmas; }

  public String getForm(int i) { return forms[i]; }
  public String getLemma(int i) { return (lemmas == null) ? null : lemmas[i]; }
  public String getCPOSTag(int i) { return (cpostags == null) ? NULL : cpostags[i]; }
  public String getPOSTag(int i) { return postags[i]; }
  public String getFeat(int i, int j) { return feats[i][j]; }
  public String[] getFeats(int i) { return (feats == null) ? new String[]{"-"} : feats[i]; }
  public String[] getPairwiseFeats(int i, int j) { if(pfeats[i]==null) return null; return pfeats[i][j]; }
  public void setFeats(int i, String[] f) { feats[i] = f; }
  public int getHead(int i) { return heads[i]; }
  public String getDepRel(int i) { return deprels[i]; }
  public RelationalFeature getRelFeat(int i) { return relFeats[i]; }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(forms);
    out.writeObject(lemmas);
    out.writeObject(cpostags);
    out.writeObject(postags);
    out.writeObject(heads);
    out.writeObject(deprels);
    out.writeObject(actParseTree);
    out.writeObject(feats);
    out.writeObject(pfeats);
    out.writeObject(relFeats);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    forms = (String[]) in.readObject();
    lemmas = (String[]) in.readObject();
    cpostags = (String[]) in.readObject();
    postags = (String[]) in.readObject();
    heads = (int[]) in.readObject();
    deprels = (String[]) in.readObject();
    actParseTree = (String) in.readObject();
    feats = (String[][]) in.readObject();
    pfeats = (String[][][]) in.readObject();
    relFeats = (RelationalFeature[]) in.readObject();
  }

  @SuppressWarnings("unchecked")
  void setPOSBackPointers(String[] postags, Map[] posprev, Set<String> inb) {
    assert(postags != null);
    assert(posprev != null);
    posprev[0] = new HashMap<String,Integer>();
    for(int i=1; i<postags.length; ++i) {
      if(inb.contains(postags[i-1])) {
        posprev[i] = (Map<String,Integer>)((HashMap<String,Integer>)posprev[i-1]).clone();
        posprev[i].put(postags[i-1],i-1);
      } else {
        posprev[i] = posprev[i-1];
      }
    }
  }

  @SuppressWarnings("unchecked")
  void setPOSBackPointers_TreeMap(String[] postags, Map[] posprev, Set<String> inb) {
    assert(postags != null);
    assert(posprev != null);
    posprev[0] = new TreeMap<String,Integer>();
    for(int i=1; i<postags.length; ++i) {
      if(inb.contains(postags[i-1])) {
        posprev[i] = (Map<String,Integer>)((TreeMap<String,Integer>)posprev[i-1]).clone();
        posprev[i].put(postags[i-1],i-1);
      } else {
        posprev[i] = posprev[i-1];
      }
    }
  }

  public String[] inBetweenPOS(int i, int j, boolean coarse) {
    TreeSet<String> s = new TreeSet<String>();
    if(coarse) {
      for(String cpos : pipe.inBetweenCPOS)
        if(inBetweenPOS(cposprev, i, j, cpos))
          s.add(cpos);
    } else {
      for(String pos : pipe.inBetweenPOS)
        if(inBetweenPOS(posprev, i, j, pos))
          s.add(pos);
    }
    return s.toArray(new String[s.size()]);
  }

  private boolean inBetweenPOS(Map<String,Integer>[] maps, int i, int j, String postag) {
    assert(j >= 0 && j < maps.length);
    Integer pj = maps[j].get(postag);
    return (pj != null && i < pj);
  }

  public void setHeads(int[] h) {
    heads = h; 
  }

  public void setDepRels(String[] d) {
    deprels = d;
  }

  @SuppressWarnings("unchecked")
  public DependencyInstance getPrefixInstance(int sz) {

    FixedSizeDependencyInstance p = null;
    try {
      
      p = (FixedSizeDependencyInstance)this.clone();
      //p.pipe = (DependencyPipe) pipe.clone();
      if(sz >= length())
        return p;

      p.forms = new String[sz+1]; System.arraycopy(forms, 0, p.forms, 0, sz);
      p.lemmas = new String[sz+1]; System.arraycopy(lemmas, 0, p.lemmas, 0, sz);
      p.cpostags = new String[sz+1]; System.arraycopy(cpostags, 0, p.cpostags, 0, sz);
      p.postags = new String[sz+1]; System.arraycopy(postags, 0, p.postags, 0, sz);
      p.feats = new String[sz+1][]; System.arraycopy(feats, 0, p.feats, 0, sz);
      p.pfeats = new String[sz+1][][]; System.arraycopy(pfeats, 0, p.pfeats, 0, sz);
      p.heads = new int[sz+1]; System.arraycopy(heads, 0, p.heads, 0, sz);
      //p.deprels = new String[sz+1]; System.arraycopy(deprels, 0, p.deprels, 0, sz);
      //p.relFeats = new RelationalFeature[sz+1]; System.arraycopy(relFeats, 0, p.relFeats, 0, sz);
      p.posprev = new Map[sz+1]; System.arraycopy(posprev, 0, p.posprev, 0, sz+1);
      p.cposprev = new Map[sz+1]; System.arraycopy(cposprev, 0, p.cposprev, 0, sz+1);

      p.forms[sz] = "<unk>";
      p.lemmas[sz] = "<unk-LEMMA>";
      p.cpostags[sz] = "<unk-CPOS>";
      p.postags[sz] = "<unk-POS>";
      p.heads[sz] = -1;
      p.feats[sz] = new String[0];
      p.pfeats[sz] = new String[0][0];

      for (int i=0; i<p.heads.length; ++i) {
        if(p.heads[i] > sz)
          p.heads[i] = sz;
      }

    } catch(CloneNotSupportedException e) { ErasureUtils.noop(e); }
    return p;
  }

  public DependencyInstance getSourceInstance() {
    return sourceInstance;
  }

  public void setSourceInstance(DependencyInstance i) {
    this.sourceInstance = i;
  }

  public void setWordAlignment(WordAlignment wa) {
    this.wa = wa;
  }
  
  public int[] getSource(int i) {
    return wa.e2f(i);
  }
}
