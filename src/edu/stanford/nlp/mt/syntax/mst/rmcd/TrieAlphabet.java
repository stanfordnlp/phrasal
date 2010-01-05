package mt.syntax.mst.rmcd;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.io.*;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import gnu.trove.THashMap;


/**
 * Index backed-up with a trie. Should eventually move to edu.stanford.nlp.util.
 * 
 * @author Michel Galley
 */
public class TrieAlphabet implements Index<String>, Serializable {

  private Trie root;
  private int lastId;
  private boolean growthStopped;

  TrieAlphabet() {
    this("default");
  }

  TrieAlphabet(String id) {
    root = Trie.root(id);
    lastId = -1;
  }

  Trie root() {
    return root;
  }

  void setFinal(Trie trie) {
    if(!growthStopped && trie.id < 0)
      trie.id = ++lastId;
  }

  public int indexOf(String str) {
    return indexOf(str, false);
  }

  public int indexOf(String str, boolean add) {
    Trie n = lookup(root, str, add);
    if(n == null)
      return Trie.unkId();
    setFinal(n);
    return n.id;
  }

  Trie lookup(Trie trie, String nextToken, boolean add) {
    if(trie == null)
      return null;
    Trie nextTrie = trie.get(nextToken);
    if(growthStopped || !add || nextTrie != null)
      return nextTrie;
    Trie newTrie = new Trie();
    trie.put(nextToken, newTrie);
    return newTrie;
  }

  public int size() {
    return lastId+1;
  }

  public int trueSize(boolean countInner) {
    return root.trueSize(countInner);
  }

  public void trim() {
    System.err.println("Trimming alphabet...");
    root.trim();
  }

  /**
   * Reindex alphabet, dropping keys whose corresponding weights are zero.
   * @param mixParams Weights determining whether keys are dropped.
   * A key j is dropped if weights[i][j] == 0 for all i.
   */
  public void reindex(Parameters params, Map<String,Parameters> mixParams) {

    // Merge all params into tmpP:
    double[][] tmpP = new double[mixParams.size()+1][];
    String[] mixIds = new String[mixParams.size()];
    int k=0;
    for(Map.Entry<String,Parameters> e : mixParams.entrySet()) {
      mixIds[k] = e.getKey();
      tmpP[k] = e.getValue().parameters;
      ++k;
    }
    tmpP[tmpP.length-1] = params.parameters;

    // Setup table mapping old to new indices:
    int sz = 0;
    for(double[] w : tmpP)
      if(w.length > sz)
        sz = w.length;
    int[] idx = new int[sz];

    // Reset indexing:
    //int oldLastId = lastId;
    lastId = -1;

    // Prune index:
    prune(root, tmpP, idx);

    // Resize weight vectors:
    for (int i=0; i<tmpP.length; ++i) {
      double[] oldw = tmpP[i];
      double[] neww =  tmpP[i] = new double[lastId+1];
      for (int j=0; j<=lastId; ++j) {
        if(j < neww.length && j < idx.length && idx[j] < oldw.length)
          neww[j] = oldw[idx[j]];
      }
    }

    // Update references:
    for(int i=0; i<mixIds.length; ++i)
      mixParams.get(mixIds[i]).parameters = tmpP[i];
    params.parameters = tmpP[tmpP.length-1];
  }

  /**
   * Prune alphabet, dropping keys whose corresponding weights are all zero.
   * @param t Curent trie node.
   * @param weights Weights determining whether keys are dropped.
   * A key j is dropped if weights[i][j] == 0 for all i.
   * @param idx Mapping between old and new index.
   * @return number of descendants with id >= 0 (if this number is zero, current node can be pruned)
   */
  private int prune(Trie t, double[][] weights, int[] idx) {

    // Determine if current node should be pruned:
    int sz = 0;
    if (t.id >= 0) {
      for(double[] w : weights) {
        if(t.id < w.length && w[t.id] != 0.0) {
          sz = 1;
          break;
        }
      }
    }

    // Determine if each successor nodes should be pruned:
    Set<String> removeSet = new HashSet<String>();
    if(t.map != null) {
      for(Map.Entry<String,Trie> e : t.map.entrySet()) {
        Trie t2 = e.getValue();
        if(t2 != null) {
          int sz2 = prune(t2, weights, idx);
          if(sz2 == 0)
            removeSet.add(e.getKey());
          sz += sz2;
        }
      }
      for(String s : removeSet)
        t.map.remove(s);
    }

    // Give new index to node:
    if(sz == 0) {
      t.id = Trie.unkId();
    } else if(t.id >= 0) {
      int oldid = t.id;
      t.id = ++lastId;
      idx[t.id] = oldid;
    }

    return sz;
  }

  void stopGrowth() {
    growthStopped = true;
    System.err.println("TrieAlphabet: stopGrowth: "+this);
  }
  
  void allowGrowth() {
    growthStopped = false;
    System.err.println("TrieAlphabet: allowGrowth: "+this);
  }

  boolean growthStopped() {
    return growthStopped;
  }

  public boolean isLocked() {
    return growthStopped;
  }

  public void lock() {
    stopGrowth();
  }

  public void unlock() {
    allowGrowth();
  }

  public String get(int idx) {
    throw new UnsupportedOperationException();
  }

  Map<Integer, List<String>> toMap() {
    Map<Integer, List<String>> map = new HashMap<Integer, List<String>>(lastId);
    addToMap(root, map, new ArrayList<String>());
    return map;
  }

  @SuppressWarnings("unchecked")
  private void addToMap(Trie trie, Map<Integer, List<String>> index, ArrayList<String> prefix) {
    if(trie==null)
      return;
    if(trie.id >= 0)
      index.put(trie.id,prefix);
    if(trie.map != null)
      for(Map.Entry<String,Trie> e : trie.map.entrySet()) {
        ArrayList<String> newPrefix = (ArrayList<String>) prefix.clone();
        newPrefix.add(e.getKey());
        addToMap(e.getValue(), index, newPrefix);
      }
  }

  // Serialization

  private static final long serialVersionUID = 2;

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeBoolean(growthStopped);
    out.writeInt(lastId);
    out.writeObject(root);
    out.writeInt(-1);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    growthStopped = in.readBoolean();
    lastId = in.readInt();
    root = (Trie) in.readObject();
    int checkInt = in.readInt();
    if(checkInt != -1)
      throw new RuntimeException("Unexpected token: "+checkInt);
  }

  private static void evaluateIndex(String trainFile, String testFile, Index<String> index) {

    Runtime rt = Runtime.getRuntime();
    System.gc(); System.gc(); System.gc();
    long startMemUsed = rt.totalMemory()-rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();

    // Train:
    for(String line : ObjectBank.getLineIterator(new File(trainFile)))
      for(String word : line.split("\\s+"))
        index.indexOf(word,true);
    
    index.lock();

    long endTimeMillis = System.currentTimeMillis();
    System.gc(); System.gc(); System.gc();
    long endMemUsed = rt.totalMemory() - rt.freeMemory();
    System.err.printf("Memory usage: %.3f MB time: %.3f sec.\n",
         (endMemUsed - startMemUsed)*1.0/1024/1024, (endTimeMillis-startTimeMillis)*1.0/1000.0);
    System.err.println("Size after training: "+index.size());
    startMemUsed = endMemUsed;
    startTimeMillis = endTimeMillis;

    // Test:
    int cs = 0;
    for(String line : ObjectBank.getLineIterator(new File(testFile)))
      for(String word : line.split("\\s+"))
        cs += index.indexOf(word);
    System.err.println("checksum: "+cs);

    endTimeMillis = System.currentTimeMillis();
    System.gc(); System.gc(); System.gc();
    endMemUsed = rt.totalMemory() - rt.freeMemory();
    System.err.printf("Memory usage: %.3f MB time: %.3f sec.\n",
         (endMemUsed - startMemUsed)*1.0/1024/1024, (endTimeMillis-startTimeMillis)*1.0/1000.0);
    System.err.println("Size after testing: "+index.size());
  }

	/**
	 * Reads two POS-tagged files.
	 */
  public static void main(String[] args) {
    String trainFile = args[0];
    String testFile = args[1];
    evaluateIndex(trainFile, testFile, new HashIndex<String>());
    evaluateIndex(trainFile, testFile, new TrieAlphabet());
  }


  public List<String> objectsList() {
    throw new UnsupportedOperationException();
  }

  public Collection<String> objects(int[] ints) {
    throw new UnsupportedOperationException();
  }

  public void saveToWriter(Writer out) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void saveToFilename(String s) {
    throw new UnsupportedOperationException();
  }

  public boolean contains(Object o) {
    throw new UnsupportedOperationException();
  }

  public <E> E[] toArray(E[] a) {
    throw new UnsupportedOperationException();
  }

  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  public void clear() {
    throw new UnsupportedOperationException();
  }

  public boolean addAll(Collection<? extends String> c) {
    throw new UnsupportedOperationException();
  }

  public boolean add(String charSequence) {
    throw new UnsupportedOperationException();
  }

  public Iterator<String> iterator() {
    throw new UnsupportedOperationException();
  }
}

class Trie implements Serializable {

  final static int mapType = Integer.parseInt(System.getProperty("mapType", "0"));

  private static final int ROOT_ID = -2;
  private static final int UNK_ID = -1;

  private static final Map<String,Trie> roots = new THashMap<String,Trie>(10, 0.25f);

  Map<String,Trie> map;
  int id;

  static Trie root(String id) {
    Trie t = roots.get(id);
    if(t == null) {
      t = new Trie(ROOT_ID);
      roots.put(id,t);
    }
    return t;
  }

  Trie() {
    this(UNK_ID);
  }

  Trie(int id) {
    map = null;
    this.id = id;
  }

  public int trueSize(boolean countInner) {
    int sz = (countInner || id >= 0) ? 1 : 0;
    if(map != null)
      for(Trie t : map.values())
        if(t != null)
          sz += t.trueSize(countInner);
    return sz;
  }

  public Trie get(String key) {
    return (map == null) ? null : map.get(key);
  }

  public Trie put(String key, Trie trie) {
    if(map == null)
      map = new THashMap<String,Trie>(10, 0.25f);
    return map.put(key, trie);
  }

  public void trim() {
    if(map != null) {
      Map<String,Trie> newMap = new HashMap<String,Trie>();
      for(Map.Entry<String,Trie> e : map.entrySet()) {
        Trie t = e.getValue();
        t.trim();
        newMap.put(e.getKey().replaceAll(" ",""), t);
        //newMap.put(e.getKey().trim(), t);
        //System.err.printf("[%s] -> [%s]\n", e.getKey(), e.getKey().trim());
      }
      map = newMap;
    }
  }

  public static int unkId() { return UNK_ID; }

  private static final long serialVersionUID = 3L;

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeInt(id);
    out.writeObject(map);
  }

  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    id = in.readInt();
    map = (Map<String,Trie>) in.readObject();
  }

}
