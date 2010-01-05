package edu.stanford.nlp.mt.reranker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class contains two <code>List<List<Integer>></code> to represent
 * how each word in an English sentence is aligned to the 
 * words in a Chinese sentence, and vice versa.
 * Note: the words are 0-indexed. NULL does not exist.
 *
 * @author Pi-Chuan Chang
 *
 **/
public class Alignment {
  public double score;
  TreeMap<Integer,List<Integer>> en_zh;
  TreeMap<Integer,List<Integer>> zh_en;
  
  public Alignment() {
    en_zh = new TreeMap<Integer,List<Integer>>();
    zh_en = new TreeMap<Integer,List<Integer>>();
    score = 0.0;
  }


  public int sizeEnZh() {
    return en_zh.size();
  }

  public int sizeZhEn() {
    return zh_en.size();
  }

  public int size() {
    return sizeEnZh();
  }

  public void put(int zh, int en) {
    // zh --> en direction
    List<Integer> elist = zh_en.get(zh);
    if (elist==null) {
      elist = new ArrayList<Integer>();
    }
    elist.add(en);
    zh_en.put(zh,elist);

    // en --> zh direction
    List<Integer> clist = en_zh.get(en);
    if (clist==null) {
      clist = new ArrayList<Integer>();
    }
    clist.add(zh);
    en_zh.put(en,clist);
  }

  public Map<Integer, List<Integer>> getZhEn() {
    return zh_en;
  }

  public Map<Integer, List<Integer>> getEnZh() {
    return en_zh;
  }


  public List<Integer> getZhEn(int idx) {
    List<Integer> elist = zh_en.get(idx);
    if (elist == null) {
      elist = new ArrayList<Integer>();
    }
    return elist;
  }



  public List<Integer> getEnZh(int idx) {
    List<Integer> clist = en_zh.get(idx);
    if (clist == null) {
      clist = new ArrayList<Integer>();
    }
    return clist;
  }

  /**
   * for historical reason, get is the same as getEnZh
   **/
  public Map<Integer, List<Integer>> get() {
    return getEnZh();
  }

  public Map<Integer, List<Integer>> get(boolean EnZh) {
    if (EnZh)
      return getEnZh();
    else
      return getZhEn();
  }

  /**
   * for historical reason, get is the same as getEnZh
   **/
  public List<Integer> get(int idx) {
    return getEnZh(idx);
 } 


  public List<Integer> get(int idx, boolean EnZh) {
    if (EnZh)
      return getEnZh(idx);
    else
      return getZhEn(idx);
 }

  static Alignment readOneAlignmentFromLine(String line) {
    line = line.trim();
    String[] aligns = line.split("\\s+");
    Alignment a = new Alignment();
    for (String align : aligns) {
      String[] en2zh = align.split("\\-");
      int eIdx = Integer.parseInt(en2zh[1]);
      int cIdx = Integer.parseInt(en2zh[0]);
      a.put(cIdx,eIdx);
    }
    return a;
  }

  static List<Alignment> readAlignments(String growDiagFinal) {
    try {
      BufferedReader alignR = new BufferedReader(new FileReader(growDiagFinal));

      String line=null;
      List<Alignment> as = new ArrayList<Alignment>();

      while((line=alignR.readLine())!=null) {
        Alignment a = readOneAlignmentFromLine(line);
        as.add(a);
      }
      return as;
    }
    catch (IOException e) {
      System.err.printf("Error reading alignments: '%s'\n", growDiagFinal);
      System.err.printf("\nStack Trace:\n==============\n");
      e.printStackTrace(); System.exit(-1);
    }
    return null;
  }

  @Override
	public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Zh-En:\n");
    for (int i = 0; i < zh_en.size(); i++) {
      sb.append(i); 
      sb.append(" ");
      sb.append("-->");
      List<Integer> l = zh_en.get(i);
      if (l==null) break;
      for (Integer ai : l) {
        sb.append(ai);
        sb.append(" ");
      }
      sb.append("\n");
    }

    sb.append("En-Zh:\n");
    /*
    for (int i = 0; i < en_zh.size(); i++) {
      sb.append(i); 
      sb.append(" ");
      sb.append("-->");
      List<Integer> l = en_zh.get(i);
      if (l==null) break;
      for (Integer ai : l) {
        sb.append(ai);
        sb.append(" ");
      }
      sb.append("\n");
    */
    for (Map.Entry<Integer, List<Integer>> entry : en_zh.entrySet()) {
      int e = entry.getKey();
      List<Integer> l = entry.getValue();
      sb.append(e);
      sb.append(" ");
      sb.append("-->");
      if (l==null) break;
      for (Integer ai : l) {
        sb.append(ai);
        sb.append(" ");
      }
      sb.append("\n");    
    }

    return sb.toString();
  }

  public static void main(String[] args) {
    List<Alignment> aligns = Alignment.readAlignments("blah");
    for (Alignment align : aligns) {
      System.err.println(align);
    }
  }

}
