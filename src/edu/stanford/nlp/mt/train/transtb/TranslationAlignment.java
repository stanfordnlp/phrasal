package edu.stanford.nlp.mt.train.transtb;

import edu.stanford.nlp.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;
import java.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.process.*;


public class TranslationAlignment {
  String source_raw_;
  public String[] source_;
  String translation_raw_;
  public String[] translation_;
  public int[][] matrix_;
  boolean wellformed_ = true;

  private ChineseEscaper ce_ = new ChineseEscaper();
  private PTBEscapingProcessor<HasWord,String,String> ptbe_ = new PTBEscapingProcessor<HasWord,String,String>();

  public boolean isWellFormed() {
    return wellformed_;
  }

  private Pair<String[], int[][]> fixTranslationWithMatrix(String[] t, int[][] m) {
      /* fix errors in translation_ : all 'Ltd.' become 'Lt.'. */
      t = fixTranslationWords(t);
      if (m.length != t.length+1) {
        m = trimMatrixRow(m, t.length+1);
      }
      return new Pair<String[], int[][]>(t, m);
  }

  private String[] fixTranslationWords(String[] t) {
    String[] newT = new String[t.length];
    // fix 'Ltd.'
    for(int i = 0; i < newT.length; i++) {
      if (t[i].equals("Lt."))
        newT[i] = "Ltd.";
      else if (t[i].equals("etc"))
        newT[i] = "etc.";
      else 
        newT[i] = t[i];
    }

    // fix 'etc .' at the end
    
    return newT;
  }

  private int[][] trimMatrixRow(int[][] m, int newRowLength) {
    if(newRowLength != m.length-1) throw new RuntimeException("new row length should be just one less");
    int[][] newM = new int[newRowLength][];
    for(int i = 0; i < newRowLength; i++) {
      for(int j = 0; j < m[i].length; j++) {
        newM[i][j] = m[i][j];
      }
    }
    return newM;  
  }

  public String getTranslation(int i) {
    if (i == 0) return "NULL";
    return translation_[i-1];
  }

  public String getSource(int i) {
    if (i == 0) return "NULL";
    return source_[i-1];
  }



  public String normalizeSourceSentence(String sent) {
    List<HasWord> words = new ArrayList<HasWord>();
    words.add(new Word(sent));
    words = ce_.apply(words);
    String output = words.get(0).word();
    output = output.replaceAll("―", "—");
    output = output.replaceAll("・", "·");
    return output;
  }

  public String[] normalizeTranslationSentence(String[] sents) {
    List<HasWord> words = new ArrayList<HasWord>();
    for(String w : sents) words.add(new Word(w));
    words = ptbe_.apply(words);
    String[] newSent = new String[words.size()];
    for(int i = 0; i < newSent.length; i++) {
      newSent[i] = words.get(i).word();
    }
    return newSent;
  }

  public TreeSet<Integer> mapChineseToEnglish(Pair<Integer,Integer> ip) {
    TreeSet<Integer> english = new TreeSet<Integer>();
    for (int i = ip.first; i <= ip.second; i++) {
      int matrixSource = i+1;
      for (int tidx = 1; tidx < translation_.length+1; tidx++) {
        if (matrix_[tidx][matrixSource] > 0) {
          english.add(tidx-1);
        }
      }
    }
    return english;
  }

  public TreeSet<Integer> mapChineseToEnglish_FillGap(Pair<Integer,Integer> ip, TreeSet<Integer> enRange) {
    int prevI = -1;
    TreeSet<Integer> nullgaps = new TreeSet<Integer>();
    List<Pair<Integer,Integer>> gaps = new ArrayList<Pair<Integer,Integer>>();
    
    for (Integer i : enRange) {
      //System.out.println("eni = "+i+"<br>");
      if (prevI != -1) {
        if (i > prevI+1) {
          Pair<Integer,Integer> gap = new Pair<Integer,Integer>(prevI+1, i-1);
          //System.out.println("Add = "+gap+"<br>");
          gaps.add(gap);
        }
      }
      prevI = i;
    }
    
    for(Pair<Integer,Integer> gap : gaps) {
      for (int eni = gap.first ; eni<=gap.second; eni++) {
        // Note: eni is 0-based, so we need to add 1
        boolean add = true;
        // It doesn't matter if the word aligns to NULL: matrix_[eni+1][0] can be whatever
        for (int chi = 1; chi < source_.length+1; chi++) {
          if (matrix_[eni+1][chi] > 0) {
            add = false;
            break;
          }
        }
        if (add) nullgaps.add(eni);
      }
    }
    return nullgaps;
  }



  public static boolean checkDeIsOf(String[] translation, String[] source, int[][] matrix, int deIdx) {
    boolean set = false;
    int deEidx = -1;
    for (int eidx = 0; eidx < translation.length; eidx++) {
      if (matrix[eidx][deIdx] > 0) {
        if (set) return false;
        if (!set) { deEidx = eidx; set = true; }
      }
    }
    if (set && translation[deEidx].equals("of")) return true;
    return false;
  }

  TranslationAlignment(String[] source, String[] translation) {
    this.source_ = source.clone();
    this.translation_ = translation.clone();
  }

  public TranslationAlignment(String[] source, String[] translation, int[][] matrix) {
    this.source_ = new String[source.length];
    this.translation_ = new String[translation.length];
    this.matrix_ = new int[translation.length+1][];

    for(int i = 0; i < translation.length; i++) {
      this.translation_[i] = translation[i];
    }
    for(int j = 0; j < source.length; j++) {
      this.source_[j] = source[j];
    }

    for(int i = 0; i < translation.length+1; i++) {
      this.matrix_[i] = new int[source.length+1];
      for(int j = 0; j < source.length+1; j++) {
        this.matrix_[i][j] = matrix[i][j];
      }
    }
  }

  public TranslationAlignment(String dataStr) {
    String regex
      = "<source_raw>(.*)</source_raw>\\n"+
      "<source>(.*)</source>\\n"+
      "<translation_raw>(.*)</translation_raw>\\n"+
      "<translation>(.*)</translation>\\n"+
      "<matrix>(.*)</matrix>";

    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(dataStr);

    if (matcher.find()) {
      source_raw_ = normalizeSourceSentence(matcher.group(1));

      String sourceStr = matcher.group(2);
      sourceStr = sourceStr.trim();
      sourceStr = normalizeSourceSentence(sourceStr);
      if (sourceStr.length()==0) { wellformed_ = false; return; }
      source_ = sourceStr.split("\\s+");

      translation_raw_ = matcher.group(3);

      String translationStr = matcher.group(4);
      translationStr = translationStr.trim();
      if (translationStr.length()==0) { wellformed_ = false; return; }
      translation_ = translationStr.split("\\s+");
      translation_ = normalizeTranslationSentence(translation_);

      // Read in the 2D matrix
      String matrixStr = matcher.group(5);
      matrixStr = matrixStr.trim();
      if (matrixStr.length()==0) { wellformed_ = false; return; }
      String[] rows = matrixStr.split("\\n");
      if (rows.length != translation_.length+1) {
        System.err.println("Ill-formed:");
        System.err.println(dataStr);
        wellformed_ = false; return;
      }
      matrix_ = new int[translation_.length+1][];
      for (int r_i = 0; r_i < rows.length; r_i++) {
        String rowStr = rows[r_i];
        int[] row = new int[source_.length+1];
        rowStr = rowStr.trim();
        String[] elements = rowStr.split("\\s+");
        if (elements.length != source_.length+1) {
          System.err.println("Ill-formed:");
          System.err.println(dataStr);
          System.err.println(elements.length+"\t"+source_.length);
        }

        for(int e_i = 0; e_i < elements.length; e_i++) {
          int element = Integer.parseInt(elements[e_i]);
          row[e_i] = element;
          // ignore "possible" alignemt
          if (element == 2) { row[e_i] = 0; }
          if (element > 2) throw new RuntimeException("In alignment file: Bigger than 2?");

        }
        matrix_[r_i] = row;
      }

      // this fixes translation_ and matrix_ at the same time,
      // because sometimes some changes in translation_ might 
      // need to adjust matrix_ as well
      Pair<String[], int[][]> tm = fixTranslationWithMatrix(translation_, matrix_);
      translation_ = tm.first;
      matrix_ = tm.second;
    } else {
      System.err.println("Ill-formed:");
      System.err.println(dataStr);
      wellformed_ = false; return;
    }
  }



  public static List<TranslationAlignment> readFromFile(String filename) 
  throws IOException {
    File file = new File(filename);
    return readFromFile(file);
  }

  public static List<TranslationAlignment> readFromFile(File file) 
  throws IOException {
    List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();

    if (file.exists()) {
      String content = IOUtils.slurpFile(file);
      String[] sents = content.split("</seg>");
      for (String sent : sents) {
        sent = sent.trim();;
        if (sent.length()>0) {
          TranslationAlignment ta = new TranslationAlignment(sent);
          if (ta.isWellFormed()) {
            alignment_list.add(ta);
          } else {
            //System.err.println("Ill-formed.");
          }
        }
      }
    }
    return alignment_list;
  }
}
