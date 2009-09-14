package mt.syntax.mst.rmcd;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import gnu.trove.THashMap;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DependencyInstance implements Cloneable {

  private static int INIT_SZ = 5;
  public static int LEMMA_LEN = 4;
  public static int CPOS_LEN = 2;

  private static final String[] nil_1d = new String[0];

  public static boolean incremental = false;

  private FeatureVector fv;
  private String actParseTree;
  private DependencyPipe pipe;
  private WordAlignment wa;
  
  private List<Dependent> deps;

  private DependencyInstance sourceInstance;

  // HEAD: the IDs of the heads for each element
  private ShortArrayList heads;

  // HEAD SCORES: headScores[i] is the score of taking head[i] as head
  private FloatArrayList headScores;

  class Dependent {

    // The various data types. Here's an example from Portuguese:
    //
    // 3  eles ele   pron       pron-pers M|3P|NOM 4    SUBJ   _     _
    // ID FORM LEMMA COURSE-POS FINE-POS  FEATURES HEAD DEPREL PHEAD PDEPREL
    //
    // We ignore PHEAD and PDEPREL for now.

    // FORM: the forms - usually words, like "thought"
    String form;

    // LEMMA: the lemmas, or stems, e.g. "think"
    String lemma;

    // COURSE-POS: the coarse part-of-speech tags, e.g. "V"
    String cpostag;

    // FINE-POS: the fine-grained part-of-speech tags, e.g. "VBD"
    String postag;

    // FEATURES: some features associated with the current words.
    String[] feats;
    String[][] pfeats;

    // ALIGNMENT: the set of source words aligning to forms[i]:
    int[] alignment;

    // DEPREL: the dependency relations, e.g. "SUBJ"
    String depRel;

    // POS BACKPOINTERS: pointers to previous POS tags of each kind
    Map<String,Integer> posprev, cposprev;

		// RELATIONAL FEATURE: relational features that hold between items
		private RelationalFeature relFeats;

    Dependent(Dependent backPtr, String form, String lemma, String cpos, String pos, String[] f, int[] alignment) {

      this.form = form;
      this.lemma = lemma;
      this.cpostag = cpos;
      this.postag = pos;
      this.feats = f;
      this.alignment = alignment;
      
      if(deps.size() == 0) {
        this.posprev = new THashMap<String,Integer>();
        this.cposprev = new THashMap<String,Integer>();
      } else {
        if(pipe != null) {
          int ipos = deps.size();
          this.posprev = (pipe.inBetweenPOS != null) ? getPOSBackPointers(ipos, backPtr.postag, pipe.inBetweenPOS, backPtr.posprev) : null;
          this.cposprev = (pipe.inBetweenCPOS != null) ? getPOSBackPointers(ipos, backPtr.cpostag, pipe.inBetweenCPOS, backPtr.cposprev) : null;
        } else {
          posprev = cposprev = null;
        }
      }
    }
  }

  private Dependent getLast() {
    if(deps.isEmpty())
      return null;
    return deps.get(deps.size()-1);
  }

  public DependencyInstance(DependencyPipe pipe) {
    this.pipe = pipe;
    this.deps = new ArrayList<Dependent>(INIT_SZ);
    this.heads = new ShortArrayList(INIT_SZ);
    this.headScores = new FloatArrayList(INIT_SZ);
    this.sourceInstance = null;
  }

  public DependencyInstance(DependencyPipe pipe, String[] forms, String[] lemmas, String[] cpostags, String[] postags) {
    this(pipe);
    for (int i=0; i<forms.length;++i)
      add(forms[i], lemmas[i], cpostags[i], postags[i], null);
  }

  public DependencyInstance(DependencyPipe pipe,
                            String[] forms, String[] lemmas, String[] cpostags, String[] postags,
                            String[][] feats, String[][][] pfeats,
                            String[] labs, int[] heads, RelationalFeature[] relFeats) {
    this(pipe);
    for (int i=0; i<forms.length;++i) {
      add(forms[i], lemmas[i], cpostags[i], postags[i], null);
      Dependent lastDep = getLast();
      lastDep.feats = feats[i];
      lastDep.pfeats = pfeats[i];
      lastDep.depRel = labs[i];
      this.heads.set(i, (short) heads[i]);
      lastDep.relFeats = (relFeats != null && relFeats.length > 0) ? relFeats[i] : null;
    }
  }

  public void add(String form, String pos) {
    add(form, pos, null);
  }

  public void add(String form, String pos, int[] alignment) {
    String lemma = form.length() > LEMMA_LEN ? form.substring(0,LEMMA_LEN) : form;
    String cpos = pos.length() > CPOS_LEN ? pos.substring(0,CPOS_LEN).intern() : pos;
    add(form, lemma, cpos, pos, alignment);
  }

  public void add(String form, String lemma, String cpos, String pos, int[] alignment) {
    add(form, lemma, cpos, pos, alignment, nil_1d);
  }

  public void add(String form, String lemma, String cpos, String pos, int[] alignment, String[] f) {
    deps.add(new Dependent(getLast(), form, lemma, cpos, pos, f, alignment));
    heads.add((short)-1);
    headScores.add(-Float.MAX_VALUE);
  }

  @SuppressWarnings("unchecked")
  Map<String,Integer> getPOSBackPointers(int position, String previousTag, Set<String> inb, Map<String,Integer> backPtr) {
    Map<String,Integer> map;
    if(inb.contains(previousTag)) {
      map = ((THashMap<String,Integer>)backPtr).clone();
      map.put(previousTag,position-1);
    } else {
      map = backPtr;
    }
    return map;
  }

  public String[] inBetweenPOS(int i, int j, boolean coarse) {
    assert(j < deps.size());
    TreeSet<String> s = new TreeSet<String>();
    if(coarse) {
      for(String cpos : pipe.inBetweenCPOS)
        if(inBetweenPOS(deps.get(j).cposprev, i, cpos))
          s.add(cpos);
    } else {
      for(String pos : pipe.inBetweenPOS)
        if(inBetweenPOS(deps.get(j).posprev, i, pos))
          s.add(pos);
    }
    return s.toArray(new String[s.size()]);
  }

  private boolean inBetweenPOS(Map<String, Integer> map, int i, String postag) {
    Integer pj = map.get(postag);
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
    return deps.size();
  }

  public int relFeatLength() {
    return 0;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    toString(sb);
    return sb.toString();
  }

  private void toString(StringBuilder sb) {
    for(Dependent dep : deps)
      sb.append(dep.form).append("\n");
  }

  public String prettyPrint() {
    StringBuilder sb = new StringBuilder();
    prettyPrint(sb);
    return sb.toString();
  }

  private void prettyPrint(StringBuilder sb) {
    for(int i=0; i<deps.size(); ++i) {
      if(i>0)
        sb.append(' ');
      int h = heads.get(i);
      sb.append(deps.get(i).form).append('/').append(deps.get(i).postag);
      if(h >= 0)
        sb.append('(').append(deps.get(h).form).append(')');
    }
  }

  public void setHeads(int[] h) {
    assert(deps.size() == h.length);
    for(int i=0; i<h.length; ++i)
      heads.set(i, (short) h[i]);
  }

  public boolean hasForms() { return deps != null; }


  public DependencyInstance getSourceInstance() {
    return sourceInstance;
  }

  public void setSourceInstance(DependencyInstance i) {
    this.sourceInstance = i;
  }

  public void setWordAlignment(WordAlignment wa) { this.wa = wa; }

  public int[] getSource(int i) { return wa.e2f(i); }

  /////////////////////////////////////////////////////////////
	// Functions not recommended in incremental (MT) mode:
  /////////////////////////////////////////////////////////////

  public String[] getForms() {
    List<String> forms = new ArrayList<String>();
    getFormsOrLemmas(forms, true);
    return forms.toArray(new String[length()]);
  }

  public String[] getLemms() {
    List<String> forms = new ArrayList<String>();
    getFormsOrLemmas(forms, false);
    return forms.toArray(new String[length()]);
  }

  public String[] getPOSTags() {
    List<String> tags = new ArrayList<String>();
    getPOSTags(tags);
    return tags.toArray(new String[length()]);
  }

  private void getFormsOrLemmas(List<String> list, boolean doForms) {
    for(Dependent d : deps) {
      list.add(doForms ? d.form : d.lemma);
    }
  }

  private void getPOSTags(List<String> list) {
    for(Dependent d : deps) {
      list.add(d.postag);
    }
  }

  public Object clone() throws CloneNotSupportedException {
    DependencyInstance newO = (DependencyInstance) super.clone();
    int len = newO.length()+5;
    newO.deps = new ArrayList<Dependent>(deps); // read-only => shallow copy
    newO.heads = new ShortArrayList(len); newO.heads.addAll(heads);
    newO.headScores = new FloatArrayList(len); newO.headScores.addAll(headScores);
    return newO;
  }

  public void setDepRels(String[] d) {
    for(int i=0; i<d.length; ++i)
      deps.get(i).depRel = d[i];
  }

  public DependencyInstance getPrefixInstance(int sz) {
    DependencyInstance p;
    try {
      p = (DependencyInstance)this.clone();
      if(sz >= length())
        return p;
      p.deps = deps.subList(0,sz);
    } catch(CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    return p;
  }

  public String getForm(int i) { return deps.get(i).form; }
  public String getLemma(int i) { return deps.get(i).lemma; }
  public String getCPOSTag(int i) { return deps.get(i).cpostag; }
  public String getPOSTag(int i) { return deps.get(i).postag; }

  public int getHead(int i) { return heads.get(i); }
  public void setHead(int i, int h) { heads.set(i, (short) h); }

  public float getHeadScore(int i) { return headScores.get(i); }
  public void setHeadScore(int i, float s) { headScores.set(i, s); }

  public String getFeat(int i, int j) { return deps.get(i).feats[j]; }
  public String[] getFeats(int i) { return deps.get(i).feats; }

  public String[] getPairwiseFeats(int i, int j) {
    String[][] pfeats = deps.get(i).pfeats;
    if(pfeats == null) return null; return pfeats[j];
  }

  public String getDepRel(int i) { String depRel = deps.get(i).depRel; return depRel != null ? depRel : "<no-type>"; }

  public RelationalFeature getRelFeat(int i) { return deps.get(i).relFeats; }

  public void setFeats(int i, String[] f) { deps.get(i).feats = f; }
}
