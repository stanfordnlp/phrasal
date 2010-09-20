package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.trees.*;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import edu.stanford.nlp.util.StringUtils;

public class TreeToSpans {

  public static Boolean DEBUG = false;

  public static void main(String[] args) throws Exception {
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    String line; // = null;

    while ((line = br.readLine()) != null) {
      Tree tree = null;
      try {
        tree = Tree.valueOf(line);
      } catch (Exception e) {
        e.printStackTrace();
      }

      // tree.pennPrint();

      List<String> spans; // = new ArrayList<String>();
      spans = printSpans(tree, 0);
      System.out.println(StringUtils.join(spans, ";"));
    }
  }

  public static List<String> printSpans(Tree t, int startIdx) {
    List<String> spans = new ArrayList<String>();

    if (t.isLeaf() || t.isPreTerminal())
      return spans;

    if (DEBUG) {
      System.err.printf("printSpans(t,startIdx=%d) called\n", startIdx);
      t.pennPrint(System.err);
    }

    Tree[] kids = t.children();
    // print current span
    int[] idx = new int[kids.length];
    String[] label = new String[kids.length];

    idx[0] = startIdx;
    label[0] = kids[0].nodeString();

    // for (Tree kid : kids) {
    for (int i = 1; i < kids.length; i++) {
      Tree prevKid = kids[i - 1];
      Tree kid = kids[i];
      if (DEBUG)
        System.err.printf("DEBUG: kid = %s\n", kid);
      idx[i] = idx[i - 1] + prevKid.yield().size();
      label[i] = kid.nodeString();
      if (DEBUG) {
        System.err.printf("DEBUG: idx[%d]=%d\n", i, idx[i]);
        System.err.printf("DEBUG: label[%d]=%s\n", i, label[i]);
      }
    }

    List<String> iStrs = new ArrayList<String>();
    for (int i = 0; i < kids.length; i++) {
      String last; // = null;
      if (i == kids.length - 1) {
        if (DEBUG) {
          System.err.println("looking at the last");
          System.err.printf("idx[%d]=%d\n", i, idx[i]);
          System.err.printf("kids[i]=%s\n", kids[i]);
          System.err.printf("kids[kids.length(=%d)-1].yield().size()=%d\n",
              kids.length, kids[kids.length - 1].yield().size());
        }
        int d = idx[i] + kids[kids.length - 1].yield().size() - 1;
        last = "" + d;
      } else {
        int d = idx[i + 1] - 1;
        last = "" + d;
      }
      String str = idx[i] + "," + last;
      iStrs.add(str);
    }

    // print only when kids.length >=2
    if (kids.length >= 2) {
      StringBuilder sb = new StringBuilder();
      sb.append(StringUtils.join(iStrs, ",")).append(",")
          .append(StringUtils.join(label, ",")).append(",")
          .append(t.nodeString());
      spans.add(sb.toString());
      // System.out.printf("%s,%s,%s\n", StringUtils.join(iStrs,","),
      // StringUtils.join(label,","), t.nodeString());
    }

    // then call each subtree
    for (int i = 0; i < kids.length; i++) {
      Tree kid = kids[i];
      spans.addAll(printSpans(kid, idx[i]));
    }
    return spans;
  }

}
