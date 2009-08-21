package mt.syntax.mst.rmcd;

import edu.stanford.nlp.util.ErasureUtils;

import java.util.*;

public class IncrementalDependencyInstance implements DependencyInstance, Cloneable {

  private static int LEMMA_LEN = 4;
  private static int CPOS_LEN = 2;

  private static int SZ = 20;

  private static final String[] nil_1d = new String[0];

  private FeatureVector fv;
  private String actParseTree;

  // The various data types. Here's an example from Portuguese:
  //
  // 3  eles ele   pron       pron-pers M|3P|NOM 4    SUBJ   _     _
  // ID FORM LEMMA COURSE-POS FINE-POS  FEATURES HEAD DEPREL PHEAD PDEPREL
  //
  // We ignore PHEAD and PDEPREL for now.

  // FORM: the forms - usually words, like "thought"
  private List<String> forms; // = new ArrayList<String>();

  // LEMMA: the lemmas, or stems, e.g. "think"
  private List<String> lemmas; // = new ArrayList<String>();

  // COURSE-POS: the coarse part-of-speech tags, e.g. "V"
  private List<String> cpostags; // = new ArrayList<String>();

  // FINE-POS: the fine-grained part-of-speech tags, e.g. "VBD"
  private List<String> postags; // = new ArrayList<String>();

  // FEATURES: some features associated with the current words.
  private List<String[]> feats;

  // POS BACKPOINTERS: pointers to previous POS tags of each kind
  private List<Map<String,Integer>> posprev, cposprev;
  
  // HEAD: the IDs of the heads for each element
  private List<Integer> heads;

  // HEAD SCORES: headScores[i] is the score of taking head[i] as head
  private List<Float> headScores;

  // ALIGNMENT: the set of source words aligning to forms[i]:
  private List<int[]> alignments;

  private DependencyInstance sourceInstance;

  private DependencyPipe pipe;

  public IncrementalDependencyInstance(DependencyPipe pipe) {

    this.pipe = pipe;

    forms = new ArrayList<String>(SZ);
    lemmas = new ArrayList<String>(SZ);
    cpostags = new ArrayList<String>(SZ);
    postags = new ArrayList<String>(SZ);
    feats = new ArrayList<String[]>(SZ);
    heads = new ArrayList<Integer>(SZ);
    headScores = new ArrayList<Float>(SZ);
    posprev = new ArrayList<Map<String,Integer>>(SZ);
    cposprev = new ArrayList<Map<String,Integer>>(SZ);
    alignments = new ArrayList<int[]>();

    this.sourceInstance = null;
  }

  public IncrementalDependencyInstance(DependencyPipe pipe, String[] f, String[] l, String[] c, String[] p) {
    this(pipe);
    for (int i=0; i<f.length;++i)
      add(f[i], l[i], c[i], p[i], null);
  }

  public void add(String form, String pos) {
    add(form, pos, null);
  }

  public void add(String form, String pos, int[] alignment) {
    String lemma = form.length() > LEMMA_LEN ? form.substring(0,LEMMA_LEN) : form;
    String cpos = pos.length() > CPOS_LEN ? pos.substring(0,CPOS_LEN) : pos;
    add(form, lemma, cpos, pos, alignment);
  }

  public void add(String form, String lemma, String cpos, String pos, int[] alignment) {
    add(form, lemma, cpos, pos, alignment, nil_1d);
  }

  public void add(String form, String lemma, String cpos, String pos, int[] alignment, String[] f) {
    forms.add(form);
    lemmas.add(lemma);
    cpostags.add(cpos);
    postags.add(pos);
    heads.add(-1);
    alignments.add(alignment);
    headScores.add(-Float.MAX_VALUE);
    feats.add(f);

    if(posprev.size() == 0) {
      posprev.add(new HashMap<String,Integer>());
      cposprev.add(new HashMap<String,Integer>());
    } else {
      if(pipe != null) {
        if(pipe.inBetweenPOS != null)
          setPOSBackPointers(this.postags, this.posprev, pipe.inBetweenPOS);
        if(pipe.inBetweenCPOS != null)
          setPOSBackPointers(this.cpostags, this.cposprev, pipe.inBetweenCPOS);
      }
    }
  }

  @SuppressWarnings("unchecked")
  void setPOSBackPointers(List<String> postags, List<Map<String,Integer>> posprev, Set<String> inb) {
    int i = postags.size()-1;
    Map<String,Integer> map;
    if(inb.contains(postags.get(i-1))) {
      map = (Map<String,Integer>)((HashMap<String,Integer>)posprev.get(i-1)).clone();
      map.put(postags.get(i-1),i-1);
    } else {
      map = posprev.get(i-1);
    }
    posprev.add(map);
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

  private boolean inBetweenPOS(List<Map<String,Integer>> maps, int i, int j, String postag) {
    assert(j >= 0 && j < maps.size());
    Integer pj = maps.get(j).get(postag);
    return (pj != null && i < pj);
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

  public void setParseTree(String t) {
    actParseTree = t;
  }

  public int length() {
    return forms.size();
  }

  public int relFeatLength() {
    return 0;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(forms.toString()).append("\n");
    return sb.toString();
  }

  public String prettyPrint() {
    StringBuilder sb = new StringBuilder();
    for(int i=0; i<forms.size(); ++i) {
      if(i>0)
        sb.append(' ');
      int h = heads.get(i);
      sb.append(forms.get(i)).append('/').append(postags.get(i));
      if(h >= 0)
        sb.append('(').append(forms.get(h)).append(')');
    }
    return sb.toString();
  }

  public void setHeads(int[] h) {
    heads.clear();
    for(int idx : h)
      heads.add(idx);
  }

  public void setFeats(int i, String[] f) { throw new UnsupportedOperationException(); }

  public boolean hasForms() { return forms != null; }
  public String getForm(int i) { return forms.get(i); }
  public String getLemma(int i) { return lemmas.get(i); }
  public String getCPOSTag(int i) { return cpostags.get(i); }
  public String getPOSTag(int i) { return postags.get(i); }
  
  public int getHead(int i) { return heads.get(i); }
  public void setHead(int i, int h) { heads.set(i,h); }

  public float getHeadScore(int i) { return headScores.get(i); }
  public void setHeadScore(int i, float s) { headScores.set(i,s); }

  public String getFeat(int i, int j) { return feats.get(i)[j]; }
  public String[] getFeats(int i) { return feats.get(i); }

  // Slow, usage not recommended:

  public String[] getForms() { return forms.toArray(new String[length()]); }
  public String[] getLemmas() { return lemmas.toArray(new String[length()]); }

  // Unused:

  public String getDepRel(int i) { return "<no-type>"; }
  public String[] getPairwiseFeats(int i, int j) { return nil_1d; }
  public void setDepRels(String[] d) { /* ignore */ }

  public RelationalFeature getRelFeat(int i) { return null; }

  public Object clone() throws CloneNotSupportedException {
    IncrementalDependencyInstance newO = (IncrementalDependencyInstance) super.clone();
    int len = newO.length()+5;

    newO.forms = new ArrayList<String>(len); newO.forms.addAll(forms);
    newO.lemmas = new ArrayList<String>(len); newO.lemmas.addAll(lemmas);
    newO.cpostags = new ArrayList<String>(len);  newO.cpostags.addAll(cpostags);
    newO.postags = new ArrayList<String>(len); newO.postags.addAll(postags);
    newO.feats = new ArrayList<String[]>(len); newO.feats.addAll(feats);
    newO.posprev = new ArrayList<Map<String,Integer>>(len); newO.posprev.addAll(posprev);
    newO.cposprev = new ArrayList<Map<String,Integer>>(len); newO.cposprev.addAll(cposprev);
    newO.heads = new ArrayList<Integer>(len); newO.heads.addAll(heads);
    newO.headScores = new ArrayList<Float>(len); newO.headScores.addAll(headScores);
    newO.alignments = new ArrayList<int[]>(len); newO.alignments.addAll(alignments);
    
    return newO;
  }

  public DependencyInstance getSourceInstance() {
    return sourceInstance;
  }

  public void setSourceInstance(DependencyInstance i) {
    this.sourceInstance = i;
  }
  
  public int[] getSource(int i) {
    return alignments.get(i);
  }

  public DependencyInstance getPrefixInstance(int i) {
    ErasureUtils.noop(i);
    throw new UnsupportedOperationException();
  }
}
