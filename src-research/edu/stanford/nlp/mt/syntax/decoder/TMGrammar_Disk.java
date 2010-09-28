package edu.stanford.nlp.mt.syntax.decoder;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/*Note: this code is originally developed by Chris Dyer at UMD (email: redpony@umd.edu)

 * Zhifei Li, <zhifei.work@gmail.com>
 * Johns Hopkins University
 */

@SuppressWarnings({ "unchecked", "unused" })
public class TMGrammar_Disk extends TMGrammar {
  RandomAccessFile grammarTrieFile;
  RandomAccessFile dataFile;
  Vocabulary terminals;// map terminal symbols to strings
  Vocabulary nonTerminals; // map non-terminal symbols to strings
  TrieNode_Disk root;

  public TMGrammar_Disk(ArrayList<Model> l_models, String default_ow,
      int span_limit_in, String non_terminal_regexp_in,
      String non_terminal_replace_regexp_in) {
    super(l_models, default_ow, span_limit_in, non_terminal_regexp_in,
        non_terminal_replace_regexp_in);
  }

  @Override
  public void read_tm_grammar_from_file(String filenamePrefix) {
    try {
      root = new TrieNode_Disk();
      grammarTrieFile = new RandomAccessFile(filenamePrefix + ".bin.trie", "r");
      dataFile = new RandomAccessFile(filenamePrefix + ".bin.data", "r");
      terminals = new Vocabulary(new BufferedReader(new InputStreamReader(
          new FileInputStream(filenamePrefix + ".voc.t"), "UTF8")));
      nonTerminals = new Vocabulary(new BufferedReader(new InputStreamReader(
          new FileInputStream(filenamePrefix + ".voc.nt"), "UTF8")));
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Caught ioexcept in read_tm_grammar_from_file"
          + e);
    }
  }

  @Override
  public void read_tm_grammar_glue_rules() {
    System.out
        .println("Error: call read_tm_grammar_glue_rules in TMGrammar_Disk, must exit");
    System.exit(0);
  }

  @Override
  public TrieNode get_root() {
    return root;
  }

  private Vocabulary getTerminals() {
    return terminals;
  }

  private Vocabulary getNonTerminals() {
    return nonTerminals;
  }

  private static int getIndex(int nt) {
    return nt & 7;
  }

  private static int clearIndex(int nt) {
    return -(-nt & ~7);
  }

  private static long readLongLittleEndian(RandomAccessFile f)
      throws IOException {
    long a = f.readUnsignedByte();
    a |= (long) f.readUnsignedByte() << 8;
    a |= (long) f.readUnsignedByte() << 16;
    a |= (long) f.readUnsignedByte() << 24;
    a |= (long) f.readUnsignedByte() << 32;
    a |= (long) f.readUnsignedByte() << 40;
    a |= (long) f.readUnsignedByte() << 48;
    a |= (long) f.readUnsignedByte() << 56;
    return a;
  }

  private static int readIntLittleEndian(RandomAccessFile f) throws IOException {
    int a = f.readUnsignedByte();
    a |= f.readUnsignedByte() << 8;
    a |= f.readUnsignedByte() << 16;
    a |= f.readUnsignedByte() << 24;
    return a;
  }

  public class TrieNode_Disk extends TrieNode {
    private boolean loaded = false;
    private long fOff;
    private int[] keys;// disk id for words
    private TrieNode_Disk[] p_child_trienodes;
    private RuleBin rule_bin;

    public TrieNode_Disk() {
      fOff = 0;
    }

    private TrieNode_Disk(long offset) {
      fOff = offset;
    }

    @Override
    public boolean is_no_child_trienodes() {
      return (p_child_trienodes == null);
    }

    @Override
    public RuleBin get_rule_bin() {
      return rule_bin;
    }

    @Override
    public TrieNode_Disk match_symbol(int sym_id) {// looking for the next layer
                                                   // trinode corresponding to
                                                   // this symbol
      int id_disk_voc;
      if (Symbol.is_nonterminal(sym_id))
        id_disk_voc = nonTerminals.convert_lm_index_2_disk_index(sym_id);
      else
        id_disk_voc = terminals.convert_lm_index_2_disk_index(sym_id);

      if (p_child_trienodes == null)
        return null;
      return advance(findKey(id_disk_voc));
    }

    // find the position of the key in the p_child_trienodes array
    private int findKey(int key) {
      if (!loaded)
        load();
      int index = Arrays.binarySearch(keys, key);
      if (index < 0 || index == keys.length || keys[index] != key)
        return keys.length;
      return index;
    }

    private TrieNode_Disk advance(int keyIndex) {
      return p_child_trienodes[keyIndex];
    }

    /*
     * public RuleBin ruleBin(int keyIndex) { return ruleBins[keyIndex]; }
     */

    public int getNumKeys() {
      if (!loaded)
        load();
      return keys.length;
    }

    // size keys dsize pointers-to-rule-bins pointers-to-TrieNodes
    private void load() {
      try {
        if (loaded)
          return;
        System.err.println("TRIE: Seeking to " + fOff);
        grammarTrieFile.seek(fOff);

        // get size: number of children
        int size = (int) TMGrammar_Disk.readLongLittleEndian(grammarTrieFile);
        System.err.println("TRIE: Read size: " + size);
        keys = new int[size];
        RuleBin[] ruleBins = new RuleBin[size];
        p_child_trienodes = new TrieNode_Disk[size];

        // read keys: disk id for words
        ByteBuffer bb = ByteBuffer.allocate(size * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        grammarTrieFile.readFully(bb.array());
        bb.asIntBuffer().get(keys);
        for (int i = 0; i < size; i++)
          System.err.println("k[" + i + "]=" + keys[i]);

        // read rule data ptrs: the offset for each rulebin at next layer
        int dsize = (int) TMGrammar_Disk.readLongLittleEndian(grammarTrieFile);
        bb = ByteBuffer.allocate(dsize * 8).order(ByteOrder.LITTLE_ENDIAN);
        grammarTrieFile.readFully(bb.array());
        LongBuffer lb = bb.asLongBuffer();
        for (int i = 0; i < size; i++)
          if (lb.get(i) > 0)
            ruleBins[i] = new RuleBin_Disk(lb.get(i));
        for (int i = 0; i < size; i++)
          System.err.println("rb[" + i + "]=" + lb.get(i));

        // read ptrs: the offset for each TrieNode at next layer
        bb.clear();
        grammarTrieFile.readFully(bb.array());
        lb = bb.asLongBuffer();
        for (int i = 0; i < size; i++)
          if (lb.get(i) > 0) {
            p_child_trienodes[i] = new TrieNode_Disk(lb.get(i));
            p_child_trienodes[i].rule_bin = ruleBins[i];
          }

        loaded = true;
      } catch (IOException e) {
        e.printStackTrace();
        throw new RuntimeException("Caught " + e);
      }
    }
  }

  public class RuleBin_Disk extends RuleBin {
    private ArrayList<Rule> l_sorted_rules = new ArrayList();
    // ShortRule[] rules;
    // int lhs;
    // private TMGrammar_Disk g;
    private long fOff;
    private boolean loaded = false;

    public RuleBin_Disk(long offset) {
      super();
      this.fOff = offset;
    }

    @Override
    public ArrayList<Rule> get_sorted_rules() {
      if (!loaded)
        load();
      return l_sorted_rules;
    }

    @Override
    public int[] get_french() {
      if (!loaded)
        load();
      return french;
    }

    @Override
    public int get_arity() {
      if (!loaded)
        load();
      return arity;
    }

    /*
     * public int getLHS() { if (!loaded) load(); return lhs; }
     */

    // we should convert the disk id to decoder lm id
    private void load() {
      try {
        if (loaded)
          return;
        System.err.println("DATA: Seeking to " + fOff);
        dataFile.seek(fOff);

        // french
        int fsize = (int) TMGrammar_Disk.readLongLittleEndian(dataFile);
        System.err.println("DATA: Read fsize: " + fsize);
        french = new int[fsize];
        int bsize = 20;
        ByteBuffer bb = ByteBuffer.allocate(bsize).order(
            ByteOrder.LITTLE_ENDIAN);
        bb.limit(fsize * 4).clear();
        dataFile.readFully(bb.array(), 0, fsize * 4);
        bb.asIntBuffer().get(french, 0, fsize);

        // convert to lm id
        for (int k = 0; k < fsize; k++) {
          if (french[k] < 0)
            arity++;
          if (french[k] < 0)
            french[k] = nonTerminals.convert_disk_index_2_lm_index(french[k]);
          else
            french[k] = terminals.convert_disk_index_2_lm_index(french[k]);
        }

        // all the rules
        int numRules = TMGrammar_Disk.readIntLittleEndian(dataFile);
        System.err.println("DATA: Read numRules: " + numRules);
        for (int i = 0; i < numRules; i++) {
          // TODO: lhs, should not have this
          int lhs = TMGrammar_Disk.readIntLittleEndian(dataFile);
          System.out.println("LHS: " + lhs);
          lhs = nonTerminals.convert_disk_index_2_lm_index(lhs);

          // eng, get integer indexed by the disk-grammar itself
          int elen = (int) TMGrammar_Disk.readLongLittleEndian(dataFile);
          int[] eng = new int[elen];
          if (elen * 4 > bsize) {
            bsize *= 2;
            bb = ByteBuffer.allocate(bsize).order(ByteOrder.LITTLE_ENDIAN);
          }
          bb.limit(elen * 4).clear();
          dataFile.readFully(bb.array(), 0, elen * 4);
          bb.asIntBuffer().get(eng);
          // convert to lm id
          for (int k = 0; k < elen; k++) {
            if (eng[k] < 0)
              eng[k] = nonTerminals.convert_disk_index_2_lm_index(eng[k]);
            else
              eng[k] = terminals.convert_disk_index_2_lm_index(eng[k]);
          }
          // feat scores
          int slen = (int) TMGrammar_Disk.readLongLittleEndian(dataFile);
          float[] scores = new float[slen];
          bb.limit(slen * 4).clear();
          dataFile.readFully(bb.array(), 0, slen * 4);
          bb.asFloatBuffer().get(scores, 0, slen);

          // add rules
          l_sorted_rules.add(new Rule_Disk(lhs, french, eng, default_owner,
              scores, arity));// TODO: sorted?
        }
        loaded = true;
      } catch (IOException e) {
        e.printStackTrace();
        throw new RuntimeException("Caught " + e);
      }
    }
  }

  public class Rule_Disk extends Rule {

    // TODO: this function is wrong
    public Rule_Disk(int lhs_in, int[] fr_in, int[] eng_in, int owner_in,
        float[] feat_scores_in, int arity_in) {
      super();
      estimate_rule();// estimate lower-bound, and set statelesscost
    }

    // obtain statelesscost
    protected float estimate_rule() {
      statelesscost = (float) 0.0;
      for (Model m_i : p_l_models) {
        double mdcost = m_i.estimate(this) * m_i.weight;
        // estcost += mdcost;
        if (m_i.stateless == true)
          statelesscost += mdcost;
      }
      return -1;
    }

    /*
     * public String toString() { return toString(null,null); }
     * 
     * public String toString(Vocabulary t, Vocabulary nt) { StringBuffer sb =
     * new StringBuffer(); boolean hasVocab = (t != null && nt != null);
     * sb.append("< LHS="); if (hasVocab)
     * sb.append('[').append(nt.getValue(lhs)).append(']'); else sb.append(lhs);
     * sb.append(" --> "); for (int w: e) { if (hasVocab) if (w < 0)
     * sb.append(nt.getValue(w)); else sb.append(t.getValue(w)); else
     * sb.append(w); sb.append(' '); } sb.append("|||"); for (float f : scores)
     * { sb.append(' ').append(f); } sb.append(" >"); return sb.toString(); }
     */

  }

  public static class Vocabulary {
    HashMap<String, Integer> str2index;
    String[] index2str;

    public Vocabulary(BufferedReader r) throws IOException {
      int l = 0;
      str2index = new HashMap<String, Integer>();
      index2str = new String[1];
      String line;
      while ((line = r.readLine()) != null) {
        String[] fields = line.split(" ");
        if (fields.length != 2)
          throw new RuntimeException("Bad format: " + l);
        int index = Integer.parseInt(fields[0]);
        if (index > index2str.length) {
          String[] x = new String[index + 1];
          System.arraycopy(index2str, 0, x, 0, index2str.length);
          index2str = x;
        }
        index2str[index] = fields[1];
        str2index.put(fields[1], index);
      }
    }

    public int getIndex(String key) {
      return str2index.get(key);
    }

    public String getValue(int index) {
      if (index < 0)
        return index2str[(-index >> 3) - 1];
      return index2str[index];
    }

    // /conver the integer in LM VOC to disk-grammar-voc integer
    public int convert_disk_index_2_lm_index(int disk_id) {
      StringBuffer symbol = new StringBuffer();
      if (disk_id < 0) {
        symbol.append("[");
        symbol.append(getValue(disk_id));

        int pos = (-disk_id & 7);
        if (pos != 0) {// indexed-nonterminal: [PHRASE,1]
          symbol.append(",");
          symbol.append(pos);
        }
        symbol.append("]");
      } else {
        symbol.append(getValue(disk_id));
      }
      return Symbol.add_non_terminal_symbol(symbol.toString());
    }

    public int convert_lm_index_2_disk_index(int lm_id) {
      return getIndex(Symbol.get_string(lm_id));
    }

  }

}
