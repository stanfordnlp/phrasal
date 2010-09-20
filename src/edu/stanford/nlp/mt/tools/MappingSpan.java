package edu.stanford.nlp.mt.tools;

import java.io.*;
import java.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.StringUtils;

/**
 * @author Pi-Chuan Chang
 */

public class MappingSpan {
  public static void main(String[] args) throws Exception {
    BufferedReader unkBR = new BufferedReader(new FileReader(args[0]));
    BufferedReader treeBR = new BufferedReader(new FileReader(args[1]));
    BufferedReader idxBR = new BufferedReader(new FileReader(args[2]));
    BufferedReader spanBR = new BufferedReader(new FileReader(args[3]));
    BufferedReader prepBR = new BufferedReader(new FileReader(args[4]));

    String unkLine, treeLine, idxLine, spanLine;
    while ((unkLine = unkBR.readLine()) != null) {
      treeLine = treeBR.readLine();
      idxLine = idxBR.readLine();
      spanLine = spanBR.readLine();
      prepBR.readLine();

      Tree tree = null;
      try {
        tree = Tree.valueOf(treeLine);
      } catch (Exception e) {
        e.printStackTrace();
      }
      String[] unkToks = unkLine.split(" +");
      if (unkToks.length != tree.yield().size()) {
        throw new RuntimeException("Error: length=" + unkToks.length
            + ": unkLine=" + unkLine + "\n length=" + tree.yield().size()
            + ": treeLine=" + treeLine);
      }

      String[] idxToks = idxLine.split(" +");
      Map<String, IntPair> mapping = new HashMap<String, IntPair>();
      for (String idxTok : idxToks) {
        String[] from_to = idxTok.split(":");
        String from = from_to[0];
        String[] tos = from_to[1].split(",");
        int range0 = Integer.parseInt(tos[0]);
        int range1 = Integer.parseInt(tos[tos.length - 1]);
        IntPair ip = new IntPair(range0, range1);
        if (mapping.get(from) == null) {
          System.err.println("Inserting to mapping: " + from + "/" + ip);
          mapping.put(from, ip);
        } else {
          throw new RuntimeException("multiple mapping for 'from' index "
              + from);
        }
      }
      String[] spanToks = spanLine.split(";");

      List<String> newSpans = new ArrayList<String>();

      for (String spanTok : spanToks) {
        String[] items = spanTok.split(",");
        // 2*n + n + 1
        if ((items.length - 1) % 3 != 0) {
          throw new RuntimeException("#items in spanTok is wrong: " + spanTok);
        }
        int n = (items.length - 1) / 3;

        boolean spanGotDropped = false;

        for (int idxidx = 0; idxidx < 2 * n; idxidx++) {
          String num = items[idxidx];
          IntPair ip = mapping.get(num);
          if (ip == null) {
            // throw new RuntimeException("IntPair of "+num+" is null");
            spanGotDropped = true;
            System.err.printf(
                "tok %s in unk is dropped. so span %s is dropped\n", num,
                spanTok);
          }
          if (spanGotDropped) {
            break;
          }
          if (idxidx % 2 == 0) // beginning of a span
          {
            // overwritten with the first of the range
            items[idxidx] = ip.getSource() + "";
          } else {
            items[idxidx] = ip.getTarget() + "";
          }
        }
        if (spanGotDropped) {
          continue;
        }
        String newSpanTok = StringUtils.join(items, ",");
        if (!spanTok.equals(newSpanTok)) {
          System.err.println("old: " + spanTok);
          System.err.println("new: " + newSpanTok);
        }
        newSpans.add(newSpanTok);
      }
      System.out.println(StringUtils.join(newSpans, ";"));
    }

  }
}