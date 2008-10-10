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

  public boolean isWellFormed() {
    return wellformed_;
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
      source_raw_ = matcher.group(1);

      String sourceStr = matcher.group(2);
      sourceStr = sourceStr.trim();
      if (sourceStr.length()==0) { wellformed_ = false; return; }
      source_ = sourceStr.split("\\s+");

      translation_raw_ = matcher.group(3);

      String translationStr = matcher.group(4);
      translationStr = translationStr.trim();
      if (translationStr.length()==0) { wellformed_ = false; return; }
      String[] translation_ = translationStr.split("\\s+");

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
          row[e_i] = Integer.parseInt(elements[e_i]);
        }
        matrix_[r_i] = row;
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

  // testing only
  public static void main(String[] args) throws IOException {

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

      /*
      Set<String> chineseSents = new HashSet<String>();

      for(int i = 0; i < ctr.size(); i++) {
        Sentence<HasWord> sent = AbstractTreeReader.getWords(ctr.getTree(i));
        StringBuilder sb = new StringBuilder();
        for(HasWord hw : sent) {
          sb.append(hw.word());
        }
        String normSent = ctr.normalizeSentence(sb.toString());
        chineseSents.add(normSent);
        //System.out.println("chineseSents: "+normSent);
      }
      */

      for (TranslationAlignment ta : alignment_list) {
        List<Tree> treesWithSameWords = ctr.getTreesWithWords(ta.source_raw_);
        if (treesWithSameWords.size() == 0) {
          System.err.printf("i=%d: Can't find tree in CTB: %s\n", fileidx, ta.source_raw_);
          // skip for now
        } else if (treesWithSameWords.size() > 1) {
          // check if every of themm are the same
          Tree baseTree = treesWithSameWords.get(0);
          for(Tree t : treesWithSameWords) {
            if (!t.equals(baseTree)) {
              System.out.println("Different trees:");
              baseTree.pennPrint();
              t.pennPrint();
            }
          }
        }
      }
    }
  }
}
