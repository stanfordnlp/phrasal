package edu.stanford.nlp.mt.syntax.classifyde;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.*;

public class WangDEinTextReorderer {
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);

    String sentFile = props.getProperty("sentFile", null);
    String treeFile = props.getProperty("treeFile", null);
    SentTreeFileReader reader = new SentTreeFileReader(sentFile, treeFile);
    Tree parsedSent = null;

    while ((parsedSent = reader.next()) != null) {
      // TODO: this looks like the same as it was before, but it would
      // be good to test it
      List<TaggedWord> hw_yield = parsedSent.taggedYield();
      List<String> yield = new ArrayList<String>();
      for (TaggedWord w : hw_yield) {
        yield.add(w.word());
      }
      List<Integer> deIndices = ExperimentUtils.getDEIndices(hw_yield);
      Map<SortByEndPair<Integer, Integer>, Integer> toOperate = new TreeMap<SortByEndPair<Integer, Integer>, Integer>();

      for (int deIdx : deIndices) {
        Pair<Integer, Integer> r = ExperimentUtils.getNPwithDERangeFromIdx(
            parsedSent, deIdx);
        SortByEndPair<Integer, Integer> range = new SortByEndPair<Integer, Integer>(
            r.first, r.second);
        if (range.first == -1)
          continue;
        String rootLabel = ExperimentUtils.getNPwithDE_rootLabel(parsedSent,
            deIdx);
        Tree rootTree = ExperimentUtils.getNPwithDE_rootTree(parsedSent, deIdx);
        System.err.println("rootTree=");
        rootTree.pennPrint(System.err);

        String tag = hw_yield.get(deIdx).tag();
        if (!rootLabel.equals("NP"))
          continue;

        boolean toSwap = true;
        if (tag.equals("DEG")) {
          Tree leftTree = ExperimentUtils.getNPwithDE_leftTree(parsedSent,
              deIdx);
          // if the left label is null, it means there are more than one to the
          // left of DE
          if (leftTree != null) {
            System.err.println("leftTree=");
            leftTree.pennPrint(System.err);
            String leftLabel = leftTree.label().value();
            if (leftLabel.equals("ADJP") || leftLabel.equals("QP")) {
              System.err.println("ADJP or QP");
              toSwap = false;
            } else if (leftLabel.equals("NP")) {
              if (leftTree.numChildren() == 1
                  && leftTree.firstChild().label().value().equals("PN")) {
                System.err.println("NPPN");
                toSwap = false;
              }
            }
          }
        } else if (!tag.equals("DEC")) {
          System.err.println("DE tag = " + tag);
          toSwap = false;
        }
        if (toSwap)
          toOperate.put(range, deIdx);
      }

      // check overlap
      // check error
      for (SortByEndPair<Integer, Integer> p1 : toOperate.keySet()) {
        for (SortByEndPair<Integer, Integer> p2 : toOperate.keySet()) {
          if (p1.first > p2.second || p1.second < p2.first)
            continue;
          if ((p1.first == p2.second || p1.second == p2.first)
              && p1.first != p1.second) {
            System.err.println("P1=" + p1);
            System.err.println("P2=" + p2);
            throw new RuntimeException("");
          }
          if ((p2.first - p1.first) * (p2.second - p1.second) > 0) {
            System.err.println("P1=" + p1);
            System.err.println("P2=" + p2);
            throw new RuntimeException("");
          }
        }
      }

      // System.err.println("(0): "+StringUtils.join(yield, " "));
      int counter = 1;
      for (Map.Entry<SortByEndPair<Integer, Integer>, Integer> e : toOperate
          .entrySet()) {
        SortByEndPair<Integer, Integer> p = e.getKey();
        int deIdx = e.getValue();
        ExperimentUtils.ReverseSublist(yield, p.first, deIdx - 1);
        ExperimentUtils.ReverseSublist(yield, deIdx + 1, p.second);
        ExperimentUtils.ReverseSublist(yield, p.first, p.second);
        // System.err.println("("+counter+"): "+StringUtils.join(yield, " "));
        counter++;
      }
      System.out.println(StringUtils.join(yield, " "));

      if (toOperate.size() > 0) {
        System.err.println(StringUtils.join(yield, " "));
        System.err.println("-->");
        System.err.println(StringUtils.join(yield, " "));
        System.err.println("=================================");
      } else {
        System.err.println("No change: " + StringUtils.join(yield, " "));
        System.err.println("=================================");
      }
    }
  }
}
