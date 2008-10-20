package mt.translationtreebank;

import edu.stanford.nlp.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;
import java.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.process.*;


public class TranslationAlignment {
  String source_raw_;
  String[] source_;
  String translation_raw_;
  String[] translation_;
  int[][] matrix_;
  boolean wellformed_ = true;

  private static boolean DEBUG = false;

  private ChineseEscaper ce_ = new ChineseEscaper();
  private PTBEscapingProcessor ptbe_ = new PTBEscapingProcessor();

  public boolean isWellFormed() {
    return wellformed_;
  }

  private String[] fixTranslation(String[] t) {
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

  private int[][] fixMatrix(int[][] m, int newRowLength) {
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

  public static void printAlignmentGridHeader() {
    printAlignmentGridHeader(new PrintWriter(System.out, true));
  }

  public static void printAlignmentGridHeader(PrintWriter pw) {
    pw.println("<br></body></html>");
    pw.println("<html><head><style type=\"text/css\"> table {border-collapse: collapse;} td { padding: 4px; border: 1px solid black } </style>");
  }

  public static void printAlignmentGridBottom() {
    printAlignmentGridBottom(new PrintWriter(System.out, true));
  }

  public static void printAlignmentGridBottom(PrintWriter pw) {
    pw.println("<br></body></html>");
  }

  public static void printAlignmentGrids(Collection<TranslationAlignment> tas) {
    printAlignmentGridHeader();
    for(TranslationAlignment ta : tas) {
      printAlignmentGrid(ta);
    }
    printAlignmentGridBottom();
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

  public TreeSet<Integer> mapChineseToEnglish(IntPair ip) {
    TreeSet<Integer> english = new TreeSet<Integer>();
    for (int i = ip.getSource(); i <= ip.getTarget(); i++) {
      int matrixSource = i+1;
      for (int tidx = 1; tidx < translation_.length+1; tidx++) {
        if (matrix_[tidx][matrixSource] > 0) {
          english.add(tidx-1);
        }
      }
    }
    return english;
  }

  public TreeSet<Integer> mapChineseToEnglish_FillGap(IntPair ip, TreeSet<Integer> enRange) {
    boolean contiguous = true;
    int prevI = -1;
    TreeSet<Integer> nullgaps = new TreeSet<Integer>();
    List<IntPair> gaps = new ArrayList<IntPair>();
    
    for (Integer i : enRange) {
      //System.out.println("eni = "+i+"<br>");
      if (prevI != -1) {
        if (i > prevI+1) {
          IntPair gap = new IntPair(prevI+1, i-1);
          //System.out.println("Add = "+gap+"<br>");
          gaps.add(gap);
        }
      }
      prevI = i;
    }
    
    for(IntPair gap : gaps) {
      for (int eni = gap.getSource() ; eni<=gap.getTarget(); eni++) {
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


  public static void printAlignmentGrid(TranslationAlignment ta) {
    printAlignmentGrid(ta, new PrintWriter(System.out, true));
  }

  public static void fixDeterminer(String[] translation, String[] source, int[][] matrix, List<Integer> deIndices) {
    boolean err = false;
    if (translation.length != matrix.length || translation.length == 0) {
      err = true;
    } else if (matrix[0].length != source.length || source.length == 0) {
      err = true;
    }
    if (err) { throw new RuntimeException("printGridNoNull FAILED."); }
    
    if (deIndices.size() > 1) { 
      // skip this case because it's multi-DEs
      return;
    }

    for (int cidx = deIndices.get(0)+1; cidx < source.length; cidx++) {
      // check if the word align to a determiner as well as something later.
      // but check backwards
      boolean isNonDT = false;
      for (int eidx = translation.length-1; eidx >= 0; eidx--) {
        if ( !isDeterminer(translation[eidx]) && matrix[eidx][cidx] > 0) { 
          isNonDT = true; 
        }
        else if ( isDeterminer(translation[eidx]) && isNonDT ) {
          // here it means that source[cidx] has linked to another word later than
          // the current eidx (which is a determiner).
          // We can clear the eidx entry in matrix
          matrix[eidx][cidx] = -1;
        }
      }
    }
  }
  
  private static boolean isDeterminer(String word) {
    word = word.toLowerCase();
    if (word.equals("the") || word.equals("a") || word.equals("its")) return true;
    return false;
  }

  public static void printNPTree(Tree t, PrintWriter pw) {
    pw.println("<pre>");
    if (t != null) {
      t.pennPrint(pw);
    } else {
      pw.println("No Tree");
    }
    pw.println("</pre>");
  }
  public static void printGridNoNull(String[] translation, String[] source, int[][] matrix, PrintWriter pw) {
    boolean err = false;
    if (translation.length != matrix.length || translation.length == 0) {
      err = true;
    } else if (matrix[0].length != source.length || source.length == 0) {
      err = true;
    }
    if (err) { System.err.println("printGridNoNull FAILED."); return; }

    pw.println("<table>");
    pw.println("<tr><td></td>");
    for(int i = 0; i < source.length; i++) {
      pw.printf("<td>%s</td>\n", source[i]);
    }
    
    for(int tidx = 0; tidx < translation.length; tidx++) {
      pw.printf("<tr><td>%s</td>\n", translation[tidx]);
      for(int sidx = 0; sidx < source.length; sidx++) {
        if (matrix[tidx][sidx] == 0)
          pw.println("  <td>&nbsp;</td>");
        else if (matrix[tidx][sidx] > 0)
          pw.printf("    <td bgcolor=\"black\">%d,%d</td>\n", tidx, sidx);
        else if (matrix[tidx][sidx] == -1)
          pw.printf("    <td bgcolor=\"green\">%d</td>\n", matrix[tidx][sidx]);
        else
          pw.printf("    <td bgcolor=\"red\">%d</td>\n", matrix[tidx][sidx]);
      }
      pw.println("</tr>");
    }
    pw.println("</table>");
  }

  public static void printAlignmentGrid(TranslationAlignment ta, PrintWriter pw) {
    pw.println("<table>");
    pw.println("<tr><td></td>");
    for(int i = 0; i <= ta.source_.length; i++) {
      pw.printf("<td>%s</td>\n", ta.getSource(i));
    }

    for(int tidx = 0; tidx <= ta.translation_.length; tidx++) {
      pw.printf("<tr><td>%s</td>\n", ta.getTranslation(tidx));
      for(int sidx = 0; sidx <= ta.source_.length; sidx++) {
        if (ta.matrix_[tidx][sidx] == 0)
          pw.println("  <td>&nbsp;</td>");
        else if (ta.matrix_[tidx][sidx] == 1)
          pw.printf("    <td bgcolor=\"black\">%d,%d</td>\n", tidx, sidx);
        else if (ta.matrix_[tidx][sidx] == 2)
          pw.printf("    <td bgcolor=\"red\">%d,%d</td>\n", tidx, sidx);
        else if (ta.matrix_[tidx][sidx] == 3)
          pw.printf("    <td bgcolor=\"green\">%d,%d</td>\n", tidx, sidx);
      }
      pw.println("</tr>");
    }
    pw.println("</table>");
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

      /* fix errors in translation_ : all 'Ltd.' become 'Lt.'. */
      translation_ = fixTranslation(translation_);
      if (matrix_.length != translation_.length+1) {
        matrix_ = fixMatrix(matrix_, translation_.length+1);
      }

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
      String content = StringUtils.slurpFile(file);
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

  public static TranslationAlignment fixAlignmentGridWithEnglishTree(
    TranslationAlignment ta, List<Tree> enTrees) {
    int totalEnWords = 0;
    List<String> leaveslist = new ArrayList<String>();
    
    for(Tree eT : enTrees) {
      Sentence<HasWord> sentence = eT.yield();
      for (int i = 0; i < sentence.size(); i++) {
        HasWord hw = sentence.get(i);
        leaveslist.add(hw.word());
      }
    }

    String[] leaves = new String[leaveslist.size()];
    for(int i = 0; i < leaves.length; i++) leaves[i] = leaveslist.get(i);
    
    ta = fixAlignmentGrid_Scores(ta, leaves);
    ta = fixAlignmentGridOnTranslation_Poss_Neg(ta, leaves);
    String[] translation = ta.translation_;


    String str1 = StringUtils.join(leaves, "");
    String str2 = StringUtils.join(translation, "");
    if (!str1.equals(str2)) {
      throw new RuntimeException("\n"+str1+"\n != \n"+str2);
    }

    List<List<Integer>> indexgroups = getIndexGroups(leaves, translation);

    int[][] newMatrix = new int[ta.matrix_.length][];
    int translationEnd = ta.matrix_.length;
    int sourceEnd = ta.matrix_[0].length;

    for (int i = 0; i < translationEnd; i++) {
      for (int j = 0; j < sourceEnd; j++) {
        newMatrix[i] = new int[ta.matrix_[i].length];
      }
    }

    boolean diff = false;
    for (int j = 0; j < sourceEnd; j++) {
      for(List<Integer> idxgroup : indexgroups) {
        boolean result = false;
        for(Integer idx : idxgroup) {
          if (ta.matrix_[idx][j] > 1) {
            result = true;
          }
        }
        for(Integer idx : idxgroup) {
          if (result && ta.matrix_[idx][j] == 0) {
            newMatrix[idx][j] = 3; // make it green
            diff = true;
          } else {
            newMatrix[idx][j] = ta.matrix_[idx][j];
          }
        }
      }
    }
    
    if (diff) {
      System.out.println("<p>Before<p>\n");
      printAlignmentGrid(ta);
      for (int i = 0; i < translationEnd; i++) {
        for (int j = 0; j < sourceEnd; j++) {
          ta.matrix_[i][j] = newMatrix[i][j];
        }
      }
      System.out.println("<p>After<p>\n");
      printAlignmentGrid(ta);
      for (int i = 0; i < translationEnd; i++) {
        for (int j = 0; j < sourceEnd; j++) {
          ta.matrix_[i][j] = newMatrix[i][j];
        }
      }
      System.out.println("<p>After<p>\n");
      printAlignmentGrid(ta);
    }


    return ta;
  }
  
  // Cases like "6:3". In the alignment file, it's separated
  public static TranslationAlignment fixAlignmentGrid_Scores(TranslationAlignment ta, String[] leaves) {
    String regex = "^(\\d+):(\\d+)$";
    Pattern pattern = Pattern.compile(regex);
    int needFix = -1;
    String first = null, second = null;
    for (int i = 0; i < ta.translation_.length; i++) {
      String t = ta.translation_[i];
      Matcher matcher = pattern.matcher(t);
      if (matcher.find() && !t.equals(leaves[i])) {
        first = matcher.group(1);
        second = matcher.group(2);
        needFix = i+1; // add one because 0 is NULL
        break;
      }
    }
    boolean fixed = false;
    while(needFix >= 0) {
      fixed = true;
      int[][] newMatrix = new int[ta.matrix_.length+2][];
      String[] newTranslation = new String[ta.translation_.length+2];
      for(int i = 0; i < newMatrix.length; i++) {
        newMatrix[i] = new int[ta.matrix_[0].length];
        for(int j = 0; j < ta.matrix_[0].length; j++) {
          if (i <= needFix) {
            newMatrix[i][j] = ta.matrix_[i][j];
          } else if (i == needFix+1 || i == needFix+2) {
            newMatrix[i][j] = ta.matrix_[needFix][j];
          } else {
            newMatrix[i][j] = ta.matrix_[i-2][j];
          }
        }
      }

      int needFixInTranslation = needFix - 1; // in translation_, NULL is not there
      for(int i = 0; i < newTranslation.length; i++) {
        if (i < needFixInTranslation) {
          newTranslation[i] = ta.translation_[i];
        } else if (i==needFixInTranslation) {
          newTranslation[i] = first;
        } else if (i==needFixInTranslation+1) {
          newTranslation[i] = ":";
        } else if (i==needFixInTranslation+2) {
          newTranslation[i] = second;
        } else {
          newTranslation[i] = ta.translation_[i-2];
        }
      }
      ta = new TranslationAlignment(ta.source_, newTranslation, newMatrix);
      needFix = -1;
      first = second = null;
      for (int i = 0; i < ta.translation_.length; i++) {
        String t = ta.translation_[i];
        Matcher matcher = pattern.matcher(t);
        if (matcher.find() && !t.equals(leaves[i])) {
          first = matcher.group(1);
          second = matcher.group(2);
          needFix = i+1; // add one because 0 is NULL
          break;
        }
      }
    }
    if (fixed && DEBUG) { 
      System.err.println("matrix changed on 'fixAlignmentGrid_Scores'"); 
      System.err.println(StringUtils.join(ta.translation_, " "));
    }
    return ta;
  }

  /*
  // TODO: fix corresponding enTrees?
  public static TranslationAlignment fixAlignmentGridOnTranslation(TranslationAlignment ta) {
    ta = fixAlignmentGridOnTranslation_Poss_Neg(ta);
    return ta;
  }
  */

  // TODO: fix corresponding enTrees?
  public static TranslationAlignment fixAlignmentGridOnTranslation_Poss_Neg(TranslationAlignment ta, String[] leaves) {
    // check if there's "BLAH's" case
    int needFix = -1;
    boolean cannot = false;

    //System.err.println("alignment="+StringUtils.join(ta.translation_, " ")+"<br>");
    //System.err.println("from tree="+StringUtils.join(leaves, " ")+"<br>");
    for (int i = 0; i < ta.translation_.length; i++) {
      String t = ta.translation_[i];
      if (i < leaves.length && !t.equals(leaves[i])) {
        if (t.endsWith("'s") && !t.equals("'s")) { needFix = i+1 ; break; } // add one because 0 is NULL
        if (t.equals("cannot")) { needFix = i+1; cannot = true; break; }
      }
    }

    int[][] newMatrix = new int[ta.matrix_.length+1][];
    String[] newTranslation = new String[ta.translation_.length+1];
    boolean fixed = false;
    while (needFix >= 0) {
      fixed = true;
      // insert a row after 'needFix'
      for(int i = 0; i < newMatrix.length; i++) {
        newMatrix[i] = new int[ta.matrix_[0].length];
        for(int j = 0; j < ta.matrix_[0].length; j++) {
          if (i <= needFix) {
            newMatrix[i][j] = ta.matrix_[i][j];
          } else if (i==needFix+1) {
            newMatrix[i][j] = ta.matrix_[needFix][j];
          } else {
            newMatrix[i][j] = ta.matrix_[i-1][j];
          }
        }
      }
      int needFixInTranslation = needFix - 1; // in translation_, NULL is not there
      for(int i = 0; i < newTranslation.length; i++) {
        if (i < needFixInTranslation) {
          newTranslation[i] = ta.translation_[i];
        } else if (i==needFixInTranslation) {
          if (cannot) {
            newTranslation[i] = "can";
          } else {
            newTranslation[i] = ta.translation_[i].substring(0, ta.translation_[i].length()-2);
          }
        } else if (i==needFixInTranslation+1) {
          if (cannot)
            newTranslation[i] = "not";
          else
            newTranslation[i] = "'s";
        } else {
          newTranslation[i] = ta.translation_[i-1];
        }
      }
      ta = new TranslationAlignment(ta.source_, newTranslation, newMatrix);
      needFix = -1;
      for (int i = 0; i < ta.translation_.length; i++) {
        String t = ta.translation_[i];
        if (t.endsWith("'s") && !t.equals("'s")) { needFix = i+1 ; break; } // add one because 0 is NULL 
      }
    }
    if (fixed && DEBUG) { 
      System.err.println("matrix changed on 'fixAlignmentGridOnTranslation_Poss_Neg'");
      System.err.println(StringUtils.join(ta.translation_, " "));
    }
    return ta;
  }

  public static TranslationAlignment fixAlignmentGridMergingChinese(
    TranslationAlignment ta, List<Tree> chTrees) {
    Sentence<HasWord> sentence = chTrees.get(0).yield();
    String[] leaves = new String[sentence.size()];
    for (int i = 0; i < sentence.size(); i++) {
      HasWord hw = sentence.get(i);
      leaves[i] = hw.word();
    }
    String[] source = ta.source_;
    List<List<Integer>> indexgroups = getIndexGroups(leaves, source);

    int translationEnd = ta.matrix_.length;
    int sourceEnd = leaves.length+1;
    //int[][] newMatrix = new int[ta.matrix_.length][];
    int[][] newMatrix = new int[translationEnd][];
    for (int i = 0; i < translationEnd; i++) {
      newMatrix[i] = new int[sourceEnd];
    }
    for (int i = 0; i < translationEnd; i++) {
      for (int j = 0; j < sourceEnd; j++) {
        if (j==0) {
          newMatrix[i][0] = ta.matrix_[i][0];
        } else {
          int result = 0;
          for(Integer idx : indexgroups.get(j-1)) {
            if (ta.matrix_[i][idx] > 0) result = 1;
          }
          newMatrix[i][j] = result;
        }
      }
    }
    ta = new TranslationAlignment(leaves, ta.translation_, newMatrix);
    return ta;
  }


  public static TranslationAlignment fixAlignmentGridMergingEnglish(
    TranslationAlignment ta, List<Tree> enTrees) {
    Sentence<HasWord> sentence = new Sentence<HasWord>();
    for (Tree enT : enTrees) {
      sentence.addAll(enT.yield());
    }
    String[] leaves = new String[sentence.size()];
    for (int i = 0; i < sentence.size(); i++) {
      HasWord hw = sentence.get(i);
      leaves[i] = hw.word();
    }
    String[] translation = ta.translation_;
    List<List<Integer>> indexgroups = getIndexGroups(leaves, translation);

    int translationEnd = leaves.length+1;
    int sourceEnd = ta.matrix_[0].length;
    int[][] newMatrix = new int[translationEnd][];
    for (int i = 0; i < translationEnd; i++) {
      newMatrix[i] = new int[sourceEnd];
    }
    for (int i = 0; i < translationEnd; i++) {
      for (int j = 0; j < sourceEnd; j++) {
        if (i==0) {
          newMatrix[0][j] = ta.matrix_[0][j];
        } else {
          int result = 0;
          for(Integer idx : indexgroups.get(i-1)) {
            if (ta.matrix_[idx][j] > 0) result = 1;
          }
          newMatrix[i][j] = result;
        }
      }
    }
    ta = new TranslationAlignment(ta.source_, leaves, newMatrix);
    return ta;
  }

  
  public static TranslationAlignment fixAlignmentGridWithChineseTree(
    TranslationAlignment ta, List<Tree> chTrees) {
    if (chTrees.size() > 1) {
      //System.err.println("chTrees > 1");
    }
    Sentence<HasWord> sentence = chTrees.get(0).yield();
    String[] leaves = new String[sentence.size()];
    for (int i = 0; i < sentence.size(); i++) {
      HasWord hw = sentence.get(i);
      leaves[i] = hw.word();
    }
    
    String[] source = ta.source_;
    List<List<Integer>> indexgroups = getIndexGroups(leaves, source);

    int[][] newMatrix = new int[ta.matrix_.length][];
    int translationEnd = ta.matrix_.length;
    int sourceEnd = ta.matrix_[0].length;

    for (int i = 0; i < translationEnd; i++) {
      for (int j = 0; j < sourceEnd; j++) {
        newMatrix[i] = new int[ta.matrix_[i].length];
      }
    }

    boolean diff = false;
    for (int i = 0; i < translationEnd; i++) {
      for(List<Integer> idxgroup : indexgroups) {
        boolean result = false;
        for(Integer idx : idxgroup) {
          if (ta.matrix_[i][idx] > 1) {
            result = true;
          }
        }
        for(Integer idx : idxgroup) {
          if (result && ta.matrix_[i][idx] != 1) {
            newMatrix[i][idx] = 2; // make it red
            diff = true;
          } else {
            newMatrix[i][idx] = ta.matrix_[i][idx];
          }
        }
      }
    }
    if (DEBUG) {
      if (diff) {
      System.out.println("<p>Before<p>\n");
      printAlignmentGrid(ta);
      for (int i = 0; i < translationEnd; i++) {
        for (int j = 0; j < sourceEnd; j++) {
          ta.matrix_[i][j] = newMatrix[i][j];
        }
      }
      System.out.println("<p>After<p>\n");
      printAlignmentGrid(ta);
      }
    }
    
    return ta;
  }

  private static void checkTranslationAlignmentAndEnTrees(TranslationAlignment ta, List<Tree> enTrees) {
    String[] enFromAlignment = ta.translation_;
    Sentence<HasWord> enFromTrees = new Sentence<HasWord>();
    for (Tree eT : enTrees) {
      enFromTrees.addAll(eT.yield());
    }
    if (enFromAlignment.length != enFromTrees.size()) {
      System.out.println("Check failed.<br>");
      System.out.println("ALGN: "+StringUtils.join(enFromAlignment, " ")+"<br>");
      System.out.println("TREE: "+StringUtils.join(enFromTrees, " ")+"<br>");
    } else {
      for (int i = 0; i < enFromTrees.size(); i++) {
        if (!enFromTrees.get(i).word().equals(enFromAlignment[i])) {
          System.out.println("Check failed.<br>");
          System.out.println("ALGN: "+StringUtils.join(enFromAlignment, " ")+"<br>");
          System.out.println("TREE: "+StringUtils.join(enFromTrees, " ")+"<br>");
          break;
        }
      }
    }
  }

  private static void checkTranslationAlignmentAndChTrees(TranslationAlignment ta, List<Tree> chTrees) {
    String[] chFromAlignment = ta.source_;
    Sentence<HasWord> chFromTrees = chTrees.get(0).yield();
    if (chFromAlignment.length != chFromTrees.size()) {
      System.out.println("Check failed.<br>");
      System.out.println("ALGN: "+StringUtils.join(chFromAlignment, " ")+"<br>");
      System.out.println("TREE: "+StringUtils.join(chFromTrees, " ")+"<br>");
    } else {
      for (int i = 0; i < chFromTrees.size(); i++) {
        if (!chFromTrees.get(i).word().equals(chFromAlignment[i])) {
          System.out.println("Check failed.<br>");
          System.out.println("ALGN: "+StringUtils.join(chFromAlignment, " ")+"<br>");
          System.out.println("TREE: "+StringUtils.join(chFromTrees, " ")+"<br>");
          break;
        }
      }
    }
  }


  
  private static List<List<Integer>> getIndexGroups(String[] leaves, String[] source) {
    List<List<Integer>> indexgroups = new ArrayList<List<Integer>>();

    int tidx = 0;
    for(int lidx = 0; lidx < leaves.length; lidx++) {
      List<Integer> indexgroup = new ArrayList<Integer>();
      String leaf = leaves[lidx];
      //System.err.println("LEAF="+leaf);
      StringBuilder chunk = new StringBuilder();
      while(!leaf.equals(chunk.toString())) {
        chunk.append(source[tidx]);
        indexgroup.add(tidx+1); // have to offset by 1, because 0 is NULL
        //System.err.println("CHUNK="+chunk.toString());
        tidx++;
      }
      indexgroups.add(indexgroup);
    }
    return indexgroups;
  }

  private static Tree getTreeWithEdges(Tree root, int leftEdge, int rightEdge) {
    Queue<Tree> queue = new LinkedList<Tree>();
    queue.add(root);
    while(queue.size() > 0) {
      Tree t = queue.remove();
      Tree[] children = t.children();
      for (Tree c : children) {
        int left = Trees.leftEdge(c, root);
        int right = Trees.rightEdge(c, root);
        if (left==leftEdge && right==rightEdge) {
          return c;
        }
        if (left <= leftEdge || right >= rightEdge) {
          queue.add(c);
        }
      }
    }
    return null;
  }

  private static Tree getTreeWithEdges(List<Tree> ts, int leftEdge, int rightEdge) {
    int[] startIndices = new int[ts.size()];

    for(int i = 0; i < ts.size(); i++) {
      if (i==0) startIndices[i] = 0;
      else {
        startIndices[i] = startIndices[i-1]+ts.get(i-1).yield().size();
      }
    }

    int pickTreeIdx = -1;
    
    for(int i = 0; i < startIndices.length; i++) {
      if (leftEdge >= startIndices[i] && 
          (i==startIndices.length-1 || rightEdge <= startIndices[i+1])) {
        pickTreeIdx = i;
      }
    }
    if (pickTreeIdx == -1) {
      return null;
    }

    Tree root = ts.get(pickTreeIdx);
    Queue<Tree> queue = new LinkedList<Tree>();
    queue.add(root);

    while(queue.size() > 0) {
      Tree t = queue.remove();
      Tree[] children = t.children();
      for (Tree c : children) {
        int left = Trees.leftEdge(c, root);
        int right = Trees.rightEdge(c, root);

        if (left==leftEdge-startIndices[pickTreeIdx] && 
            right==rightEdge-startIndices[pickTreeIdx]) {
          return c;
        }
        if (left <= leftEdge-startIndices[pickTreeIdx] ||
            right >= rightEdge-startIndices[pickTreeIdx]) {
          queue.add(c);
        }
      }
    }
    return null;
  }


  private static String analyzeNPwithDE(IntPair np, TreePair tp, PrintWriter pw) throws IOException {
    List<IntPair> englishNP = tp.NPwithDEs.get(np);
    if (englishNP.size() != 1) {
      return "fragmented";
    }

    Tree chTree = tp.chTrees.get(0);
    Tree chNPTree = getTreeWithEdges(chTree,np.getSource(), np.getTarget()+1);
    if (chNPTree == null) {
      throw new RuntimeException("chNPTree shouldn't be null");
    }

    Tree enNPTree = getTreeWithEdges(tp.enTrees, englishNP.get(0).getSource(), englishNP.get(0).getTarget()+1);
    if (enNPTree == null) {
      System.err.println("enNPTree: NULL");
    } else {
      System.err.println("enNPTree: Found");
      //enNPTree.pennPrint(System.err);
    }

    // if there's only one chunk of English, get the submatrix and subsource & subtranslation
    IntPair ennp = englishNP.get(0);
    int nplength = np.getTarget()-np.getSource()+1;
    int ennplength = ennp.getTarget()-ennp.getSource()+1;
    String[] subsource      = new String[nplength];
    String[] subtranslation = new String[ennplength];
    int[][] submatrix = new int[ennplength][nplength];

    for(int tidx = ennp.getSource(); tidx <= ennp.getTarget(); tidx++) {
      for(int sidx = np.getSource(); sidx <= np.getTarget(); sidx++) {
        // This really can be improved. Note that in matrix_, 0 --> NULL
        submatrix[tidx-ennp.getSource()][sidx-np.getSource()] = tp.alignment.matrix_[tidx+1][sidx+1];
      }
    }

    //locate the "的"
    for(int tidx = ennp.getSource(); tidx <= ennp.getTarget(); tidx++) {
      subtranslation[tidx-ennp.getSource()] = tp.alignment.translation_[tidx];
    }
    List<Integer> deIndices = new ArrayList<Integer>();
    for(int sidx = np.getSource(); sidx <= np.getTarget(); sidx++) {
      subsource[sidx-np.getSource()] = tp.alignment.source_[sidx];
      if (tp.alignment.source_[sidx].equals("的") ||
	      tp.alignment.source_[sidx].equals("之")) {
        deIndices.add(sidx-np.getSource());
      }
    }

    // The manual alignment like to align the determiners with the noun,
    // which cause lots of "undecided" type. Try to eliminate this case
    fixDeterminer(subtranslation, subsource, submatrix, deIndices);


    // Print out the sub-grid to file
    printGridNoNull(subtranslation, subsource, submatrix, pw);
    // Print related chNPTree & enNPTree, if exist
    printNPTree(chNPTree, pw);
    printNPTree(enNPTree, pw);

    if (deIndices.size() > 1) {
      return "multi-DEs";
    }
    if (deIndices.size() == 0) {
      return "no DE?";
    }

    // Now it's the case with only one DE
    // TODO: find the mapping to English of the first part, 
    //       and find the mapping to the 2nd part
    int deIdx = deIndices.get(0);


    // for "A de B"
    // get the translation range of A
    int min = subtranslation.length;
    int max = -1;
    for(int sidx = 0; sidx < deIdx; sidx++) {
      for(int tidx = 0; tidx < subtranslation.length; tidx++) {
        if (submatrix[tidx][sidx] > 0) {
          if (tidx < min) min = tidx;
          if (tidx > max) max = tidx;
        }
      }
    }
    System.err.println("rangeA = "+ subtranslation[min] + " - " + subtranslation[max]);
    IntPair rangeA = new IntPair(min,max);

    min = subtranslation.length;
    max = -1;
    for(int sidx = deIdx+1; sidx < subsource.length; sidx++) {
      for(int tidx = 0; tidx < subtranslation.length; tidx++) {
        if (submatrix[tidx][sidx] > 0) {
          if (tidx < min) min = tidx;
          if (tidx > max) max = tidx;
        }
      }
    }

    //System.err.println("rangeB = "+ subtranslation[min] + " - " + subtranslation[max]);
    IntPair rangeB = new IntPair(min,max);

    if (rangeA.getTarget() < rangeB.getSource()) {
      for(int eidx = rangeA.getTarget()+1; eidx <= rangeB.getSource()-1; eidx++) {
        if (submatrix[eidx][deIdx] > 0) {
          String deWord = subtranslation[eidx];
          if (deWord.equals("'s")) {
            return "A 's B";
          }
        }
      }

      // check if A becomes an adjective
      if (checkOrderedAdjective(rangeA, rangeB, enNPTree)) return "A B (adj)";

      return "ordered";
    }
    if (rangeB.getTarget() < rangeA.getSource()) {
      String boundaryWord = subtranslation[rangeA.getSource()];
      if (boundaryWord.equals("of") ||
          boundaryWord.equals("to")) {
        return "B "+boundaryWord+" A";
      }
      if (boundaryWord.equals("with") ||
          boundaryWord.equals("from") ||
          boundaryWord.equals("in") ||
          boundaryWord.equals("inside") ||
          boundaryWord.equals("for") ||
          boundaryWord.equals("on") ||
          boundaryWord.equals("between") ||
          boundaryWord.equals("by") ||
          boundaryWord.equals("among") ||
          boundaryWord.equals("under")) {
        return "B prep A";
      }
      if (boundaryWord.equals("that")) {
        return "relative clause";
      }

      String deMappedWord = null;
      // check if deIdx aligns to somewhere in between max(rangeB) and min(rangeA)
      for(int eidx = rangeB.getTarget()+1; eidx <= rangeA.getSource()-1; eidx++) {
        if (submatrix[eidx][deIdx] > 0) {
          String deWord = subtranslation[eidx];
          if (deWord.equals("of") ||
              deWord.equals("to")) {
            return "B "+deWord+" A";
          }
          if (deWord.equals("with") ||
              deWord.equals("from") ||
              deWord.equals("in") ||
              deWord.equals("inside") ||
              deWord.equals("for") ||
              deWord.equals("on") ||
              deWord.equals("between") ||
              deWord.equals("by") ||
              deWord.equals("among") ||
              deWord.equals("under")) {
            return "B prep A";
          }
          if (deWord.equals("that")) {
            return "relative clause";
          }
          deMappedWord = deWord;
          break;
        }
      }

      // This is flipped case, but we don't know what it is yet.
      // If enNPTree exists, 
      // we can check if range A is a VP and range B is an NP
      if (checkFlippedRelativeClause(rangeA, rangeB, enNPTree)) return "flipped (relc)";

      return "flipped";
      // TODO: DE might also just map directly to the preposition (of)
      
    }
    return "undecided";
  }

  private static boolean checkOrderedAdjective(IntPair rangeA, IntPair rangeB, Tree enTree) {
    if (enTree == null) return false;
    Tree adj = getTreeWithEdges(enTree, rangeA.getSource(), rangeA.getTarget()+1);
    if (adj != null && (adj.value().equals("ADJP") || adj.value().equals("JJ"))) {
      return true;
    }
    return false;
  }

  private static boolean checkFlippedRelativeClause(IntPair rangeA, IntPair rangeB, Tree enTree) {
    if (enTree == null) return false;
    Tree relc = getTreeWithEdges(enTree, rangeA.getSource(), rangeA.getTarget()+1);
    if (relc != null && relc.value().equals("VP")) {
      return true;
    }
    return false;
  }

  private static void printNPwithDEtoFile(int fileidx, int npidx, PrintWriter npPW, IntPair np, TreePair tp, String type) {
    List<IntPair> englishNP = tp.NPwithDEs.get(np);
    List<String> ch = new ArrayList<String>();
    for (int i = np.getSource(); i<=np.getTarget(); i++) {
      ch.add(tp.alignment.source_[i]);
    }
    String chStr = StringUtils.join(ch, " ");

    List<String> en = new ArrayList<String>();
    for (IntPair enNP : englishNP) {
      StringBuilder ensb = new StringBuilder();
      for (int i = enNP.getSource(); i<=enNP.getTarget(); i++) {
        ensb.append(tp.alignment.translation_[i]).append(" ");
      }
      en.add(ensb.toString());
    }
    String enStr = StringUtils.join(en, "|| ");

    npPW.printf("%d\t%d\t%s\t%d\t%s\t%s\n", fileidx, npidx, type, englishNP.size(), chStr, enStr);
  }

  // testing only
  public static void main(String[] args) throws IOException {
    int validAlignments = 0;
    int numtreepairs = 0;
    int numNPwithDE = 0;
    int numNPwithDE_contiguous = 0;
    int numNPwithDE_fragmented = 0;
    int FIDX = Integer.parseInt(args[0]);
    Properties props =StringUtils.argsToProperties(args);
    Counter typeCounter = new ClassicCounter<String>();
    
    // For this to run on both NLP machine and my computer
    String dirname = "/u/nlp/scr/data/ldc/LDC2006E93/GALE-Y1Q4/word_alignment/data/chinese/nw/";
    File dir = new File(dirname);
    if (!dir.exists()) {
      dirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\LDC2006E93\\GALE-Y1Q4\\word_alignment\\data\\chinese\\nw\\";
      dir = new File(dirname);
      if (!dir.exists()) {
        throw new RuntimeException("LDC2006E93 doesn't exist in either of the hard-coded locations.");
      }
    }

    for(int fileidx = FIDX; fileidx <= FIDX; fileidx++) {
      // (1) Read alignment files
      String aname = String.format("%schtb_%03d.txt", dirname, fileidx);
      File file = new File(aname);
      List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();
      if (file.exists()) {
        System.err.println("Processing  "+fileidx);
        alignment_list = TranslationAlignment.readFromFile(file);
      } else {
        System.err.println("Skip "+fileidx);
      }

      // (2) Read Chinese Trees
      ChineseTreeReader ctr = new ChineseTreeReader();
      String ctbdirname = "/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed/";
      File ctbdir = new File(ctbdirname);
      if (!ctbdir.exists()) {
        ctbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\CTB6\\data\\utf8\\bracketed\\";
        ctbdir = new File(ctbdirname);
        if (!ctbdir.exists())
          throw new RuntimeException("CTB6.0 doesn't exist in either of the hard-coded locations.");
      }
      String ctbname =
        String.format("%schtb_%04d.fid", ctbdirname, fileidx);
      ctr.readMoreTrees(ctbname);

      // (3) Read English Trees
      EnglishTreeReader etr = new EnglishTreeReader();
      String ectbdirname = "/u/nlp/scr/data/ldc/LDC2007T02-EnglishChineseTranslationTreebankV1.0/data/pennTB-style-trees/";
      File ectbdir = new File(ectbdirname);
      if (!ectbdir.exists()) {
        ectbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\LDC2007T02-EnglishChineseTranslationTreebankV1.0\\data\\pennTB-style-trees\\";
        ectbdir = new File(ectbdirname);
        if (!ectbdir.exists()) {
          throw new RuntimeException("EnglishChineseTranslationTreebankV1.0 doesn't exist in either of the hard-coded locations.");
        }
      }
      String ename =
        String.format("%schtb_%03d.mrg.gz",
                      ectbdirname, fileidx);
      etr.readMoreTrees(ename);

      // (4) Going through entries in (1) and check if they exist in (2)
      // (5) Going through entries in (1) and check if they exist in (3)
      // (6) also, if the tests passed, output various information

      List<TreePair> treepairs = new ArrayList<TreePair>();

      // open output file for NP list. This format is easy to import to Excel
      String npOutputDir = props.getProperty("npOutputDir", null);
      PrintWriter npPW = null;
      PrintWriter npgridPW = null;
      if (npOutputDir != null) {
        String filename = npOutputDir+"/"+fileidx+".np";
        System.err.println("Output NPs to "+filename);
        npPW= new PrintWriter(new BufferedWriter(new FileWriter(filename)));

        filename = npOutputDir+"/grids/"+fileidx+".npgrid.html";
        System.err.println("Output NP Grids to "+filename);
        npgridPW= new PrintWriter(new BufferedWriter(new FileWriter(filename)));
      }
      // open output file for orginal Translation Alignment file.
      // In this format, every token is as defined in the manual word_alignment file.
      // So, Chinese tokens are characters.
      // The reason of having this option is mostly for debugging.
      // If you find any final grid in the tree-aligned version somewhat weird,
      // you can print out the original grid and see if it makes sense.
      String origTAdir = props.getProperty("origTAdir", null);
      PrintWriter origtaPW = null;
      if (origTAdir != null) {
        String filename = origTAdir+"/"+fileidx+".origta.html";
        System.err.println("Output original grids to "+filename);
        origtaPW = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
      }

      int npCount = 1;
      if (origtaPW != null) {printAlignmentGridHeader(origtaPW);}
      if (npgridPW != null) {printAlignmentGridHeader(npgridPW);}
      for (TranslationAlignment ta : alignment_list) {
        if (origtaPW != null) {
          printAlignmentGrid(ta, origtaPW);
        }

        List<Tree> chTrees = ctr.getTreesWithWords(ta.source_);
        if (chTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in CTB: %s\n", fileidx, StringUtils.join(ta.source_, " "));
          continue;
          // skip for now
        } else if (chTrees.size() > 1) {
          System.err.printf("i=%d: Mulitiple trees: %s\n", fileidx, StringUtils.join(ta.source_, " "));
        }
        
        List<Tree> enTrees = etr.getTreesWithWords(ta.translation_);
        if (enTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in PTB: %s\n", fileidx, StringUtils.join(ta.translation_, " "));
          continue;
          // skip for now
        } else if (enTrees.size() > 1) {
          System.err.printf("i=%d: Mulitiple trees: %s\n", fileidx, StringUtils.join(ta.translation_, " "));
        }
        
        // Fix the Translation Alignment before adding to the TreePair
        if (DEBUG) System.err.println("i="+fileidx);
        ta = fixAlignmentGridWithChineseTree(ta, chTrees);
        ta = fixAlignmentGridMergingChinese(ta, chTrees);
        ta = fixAlignmentGridWithEnglishTree(ta, enTrees);
        ta = fixAlignmentGridMergingEnglish(ta, enTrees);
        checkTranslationAlignmentAndEnTrees(ta, enTrees);
        checkTranslationAlignmentAndChTrees(ta, chTrees);
        TreePair tp = new TreePair(ta, enTrees, chTrees);
        numNPwithDE += tp.NPwithDEs.size();
        
        for (IntPair NPwithDE : tp.NPwithDEs.keySet()) {
          List<IntPair> englishNP = tp.NPwithDEs.get(NPwithDE);
          if (englishNP.size()==1) {
            numNPwithDE_contiguous++;
          } else {
            numNPwithDE_fragmented++;
          }
          
          if (npPW != null) {
            printAlignmentGridHeader(npgridPW);
            String type = analyzeNPwithDE(NPwithDE, tp, npgridPW);
            typeCounter.incrementCount(type);
            printAlignmentGridBottom(npgridPW);
            printNPwithDEtoFile(fileidx, npCount, npPW, NPwithDE, tp, type);
            npCount++;
          }
        }
        
        treepairs.add(tp);
      }
      if (npgridPW != null) { printAlignmentGridBottom(npgridPW); npgridPW.close(); }
      if (origtaPW != null) { printAlignmentGridBottom(origtaPW); origtaPW.close(); }
      
      numNPwithDE  += TreePair.printAllwithDE(treepairs);
      numtreepairs += treepairs.size();
      
      if (npPW != null) {
        npPW.close();
      }
      validAlignments += alignment_list.size();
    }
    
    // count Countiguous NPs & Fragmented NPs
    
    System.err.println("# valid translation alignment = "+validAlignments);
    System.err.println("# Tree Pairs = "+numtreepairs);
    System.err.println("# NPs with DE = "+numNPwithDE);
    System.err.println("# NPs with DE (contiguous)= "+numNPwithDE_contiguous);
    System.err.println("# NPs with DE (fragmented)= "+numNPwithDE_fragmented);
    System.err.println(typeCounter);
  }
}
