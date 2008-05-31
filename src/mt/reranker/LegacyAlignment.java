package mt.reranker;

import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class contains a List<List<Integer>> to represent
 * how each word in an English sentence is aligned to the 
 * words in a Chinese sentence -- it can be the other way
 * around, depending on what kind of alignments you read in.
 * It was originally written for <code>DepsAnalyzer</code> 
 * class to use.
 *
 **/
public class LegacyAlignment {
  public double score;
  List<List<Integer>> alignments;
  
  public LegacyAlignment() {
    alignments = new ArrayList<List<Integer>>();
    score = 0.0;
  }

  public void add(List<Integer> list) {
    alignments.add(list);
  }

  public List<Integer> get(int idx) {
    if (idx < alignments.size()) {
      return alignments.get(idx);
    } else {
      return new ArrayList<Integer>();
    }
  }

  // GIZA++-style output
  public String toString(String source[], String target[]) {
    StringBuilder sb = new StringBuilder();
    sb.append(StringUtils.join(source, " ")).append("\n");


    if (alignments.size() != source.length) {
      throw new RuntimeException("alignment.size()=="+alignments.size()+", source.leng="+source.length+"\n"+"alignment size should be the same as |source| . Aborting");
    }
    
    // build the reverse map in order to print out GIZA++ format
    Map<Integer,List<Integer>> map = new HashMap<Integer,List<Integer>>();

    for (int i = 0; i < alignments.size(); i++) {
      List<Integer> l = alignments.get(i);
      for (Integer ai : l) {
        //if (ai==-1) //NULL
        //  map[ai+1].add(i+1);
        //else
        List<Integer> list = map.get(ai+1);
        if (list==null) list = new ArrayList<Integer>();
        list.add(i+1);
        map.put(ai+1,list);
      }
    }
          

    for (int i = 0; i < target.length+1; i++) {
      if (i==0) {
        sb.append("NULL"); // NULL is 0
      } else {
        sb.append(target[i-1]);
      }
      sb.append(" ({ ");
      List<Integer> l = map.get(i);
      if (l!=null) {
        for (Integer ai : l) {
          if (ai==0) continue; // NULL does not need to be printed out
          sb.append(ai);
          sb.append(" ");
        }
      }
      sb.append("}) ");
    }
    return sb.toString();
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < alignments.size(); i++) {
      sb.append(i); // NULL is 0
      sb.append(" ");
      sb.append("-->");
      List<Integer> l = alignments.get(i);
      for (Integer ai : l) {
        sb.append(ai);
        sb.append(" ");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  static List<LegacyAlignment> readAlignments(String aname) {
    String line = null;
    
    Pattern p = Pattern.compile("([^\\s+]) \\(\\{\\s*(.*?)\\s*\\}\\)");
    List<LegacyAlignment> als = new ArrayList<LegacyAlignment>();
      
    try {
      BufferedReader in = new BufferedReader(new FileReader(aname));

      // first line is chinese words
      line = in.readLine();
      while((line = in.readLine()) != null) {
        Matcher m = p.matcher(line);
        LegacyAlignment sentA = new LegacyAlignment();
        
        while(m.find()) {
          // m.group(1) : the English word
          // m.group(2) : the alignment to Chinese word
          String alignment = m.group(2);
          String[] aligns = alignment.split(" ");
          List<Integer> ints = new ArrayList<Integer>();
          for (String a : aligns) {
            if (a.length()> 0)
              ints.add(Integer.parseInt(a));
          }
          sentA.add(ints);
        }
        als.add(sentA);
      }
    }
    catch (IOException e) {
      System.err.printf("Error reading alignments: '%s'\n", aname);
      System.err.printf("\nStack Trace:\n==============\n");
      e.printStackTrace(); System.exit(-1);
    }
    return als;
  }

  public static void main(String[] args) {
    List<LegacyAlignment> a = readAlignments("test/testFile");
    String[] source = new String[7];
    //source[0] = "NULL";
    source[0] = "??";
    source[1] = "??";
    source[2] = "??";
    source[3] = "?";
    source[4] = "??";
    source[5] = "??";
    source[6] = "??";

    String[] target = new String[16];
    //target[0] = "NULL";
    target[0] = "the";
    target[1] = "development";
    target[2] = "of";
    target[3] = "shanghai";
    target[4] = "'s";
    target[5] = "pudong";
    target[6] = "is";
    target[7] = "in";
    target[8] = "step";
    target[8]= "with";
    target[10]= "the";
    target[11]= "establishment";
    target[12]= "of";
    target[13]= "its";
    target[14]= "legal";
    target[15]= "system";


    System.err.println(a.get(0).toString(source, target));

  }


}
