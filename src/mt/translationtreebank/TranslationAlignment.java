package mt.translationtreebank;

import edu.stanford.nlp.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;
import java.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.process.*;


class TranslationAlignment {
  String source_raw_;
  String[] source_;
  String translation_raw_;
  String[] translation_;
  int[][] matrix_;
  boolean wellformed_ = true;

  private static boolean DEBUG = true;

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

  public static void printAlignmentGrids(Collection<TranslationAlignment> tas) {
    System.out.println("<br></body></html>");
    System.out.println("<html><head><style type=\"text/css\"> table {border-collapse: collapse;} td { padding: 4px; border: 1px solid black } </style>");
    for(TranslationAlignment ta : tas) {
      printAlignmentGrid(ta);
    }
    System.out.println("<br></body></html>");
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


  public static void printAlignmentGrid(TranslationAlignment ta) {
    System.out.println("<table>");
    System.out.println("<tr><td></td>");
    for(int i = 0; i <= ta.source_.length; i++) {
      System.out.printf("<td>%s</td>\n", ta.getSource(i));
    }

    // print out NULL on Chinese
    for(int tidx = 0; tidx <= ta.translation_.length; tidx++) {
      System.out.printf("<tr><td>%s</td>\n", ta.getTranslation(tidx));
      for(int sidx = 0; sidx <= ta.source_.length; sidx++) {
        if (ta.matrix_[tidx][sidx] == 0)
          System.out.println("  <td>&nbsp;</td>");
        else if (ta.matrix_[tidx][sidx] == 1)
          System.out.printf("    <td bgcolor=\"black\">%d,%d</td>\n", tidx, sidx);
        else if (ta.matrix_[tidx][sidx] == 2)
          System.out.printf("    <td bgcolor=\"red\">%d,%d</td>\n", tidx, sidx);
        else if (ta.matrix_[tidx][sidx] == 3)
          System.out.printf("    <td bgcolor=\"green\">%d,%d</td>\n", tidx, sidx);
      }
      System.out.println("</tr>");
    }
    System.out.println("</table>");
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

  public static TranslationAlignment fixAlignmentGridOnTranslation(TranslationAlignment ta) {
    ta = fixAlignmentGridOnTranslation_Poss_Neg(ta);
    return ta;
  }

  public static TranslationAlignment fixAlignmentGridOnTranslation_Poss_Neg(TranslationAlignment ta) {
    // check if there's "BLAH's" case
    int needFix = -1;
    boolean cannot = false;

    for (int i = 0; i < ta.translation_.length; i++) {
      String t = ta.translation_[i];
      if (t.endsWith("'s") && !t.equals("'s")) { needFix = i+1 ; break; } // add one because 0 is NULL
      if (t.equals("cannot")) { needFix = i+1; cannot = true; break; }
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
  
  private static List<List<Integer>> getIndexGroups(String[] leaves, String[] source) {
    List<List<Integer>> indexgroups = new ArrayList<List<Integer>>();

    int tidx = 0;
    for(int lidx = 0; lidx < leaves.length; lidx++) {
      List<Integer> indexgroup = new ArrayList<Integer>();
      String leaf = leaves[lidx];
      StringBuilder chunk = new StringBuilder();
      while(!leaf.equals(chunk.toString())) {
        chunk.append(source[tidx]);
        indexgroup.add(tidx+1); // have to offset by 1, because 0 is NULL
        tidx++;
      }
      indexgroups.add(indexgroup);
    }
    return indexgroups;
  }

  // testing only
  public static void main(String[] args) throws IOException {
    int validAlignments = 0;
    int numtreepairs = 0;
    for(int fileidx = 1; fileidx <= 325; fileidx++) {
      // (1) Read alignment files
      String aname = String.format("/u/nlp/scr/data/ldc/LDC2006E93/GALE-Y1Q4/word_alignment/data/chinese/nw/chtb_%03d.txt", fileidx);
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
      String ctbname =
        String.format("/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed/chtb_%04d.fid", fileidx);
      ctr.readMoreTrees(ctbname);

      // (3) Read English Trees
      EnglishTreeReader etr = new EnglishTreeReader();
      String ename =
        String.format("/u/nlp/scr/data/ldc/LDC2007T02-EnglishChineseTranslationTreebankV1.0/data/pennTB-style-trees/chtb_%03d.mrg.gz", 
                      fileidx);
      etr.readMoreTrees(ename);

      // (4) Going through entries in (1) and check if they exist in (2)
      // (5) Going through entries in (1) and check if they exist in (3)

      List<TreePair> treepairs = new ArrayList<TreePair>();
      for (TranslationAlignment ta : alignment_list) {
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
        ta = fixAlignmentGridOnTranslation(ta);
        ta = fixAlignmentGridWithEnglishTree(ta, enTrees);
        ta = fixAlignmentGridWithChineseTree(ta, chTrees);
        printAlignmentGrid(ta);
        TreePair tp = new TreePair(ta, enTrees, chTrees);
        treepairs.add(tp);
      }
      validAlignments += alignment_list.size();
      numtreepairs += treepairs.size();
    }

    System.err.println("# valid translation alignment = "+validAlignments);
    System.err.println("# Tree Pairs = "+numtreepairs);
  }
}
