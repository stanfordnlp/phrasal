package edu.stanford.nlp.mt.syntax.classifyde;

import edu.stanford.nlp.mt.train.transtb.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FullInformationFeaturizer extends AbstractFeaturizer {
  Map<String, String> cilin_map;
  Map<String, String> cilin_level2map;
  Map<String, Set<String>> cilin_multipleEntry;

  public Counter<String> extractFeatures(int deIdxInSent,
      Pair<Integer, Integer> chNPrange, Tree chTree, Properties props,
      Set<String> cachedWords) {

    String twofeatStr = props.getProperty("2feat", "false");
    String revisedStr = props.getProperty("revised", "false");
    String ngramStr = props.getProperty("ngram", "false");
    String firstStr = props.getProperty("1st", "false");
    String lastcharNStr = props.getProperty("lastcharN", "false");
    String lastcharNgramStr = props.getProperty("lastcharNgram", "false");
    String pwordStr = props.getProperty("pword", "false");
    String pathStr = props.getProperty("path", "false");
    String percentageStr = props.getProperty("percentage", "false");
    String ciLinStr = props.getProperty("ciLin", "false");
    String ciLinMultipleTagStr = props.getProperty("ciLinMultipleTag", "false");
    String topicalityStr = props.getProperty("topicality", "false");
    String lengthStr = props.getProperty("length", "false");
    String wordNgramStr = props.getProperty("wordNgram", "false");

    Boolean twofeat = Boolean.parseBoolean(twofeatStr);
    Boolean revised = Boolean.parseBoolean(revisedStr);
    Boolean ngram = Boolean.parseBoolean(ngramStr);
    Boolean first = Boolean.parseBoolean(firstStr);
    Boolean lastcharN = Boolean.parseBoolean(lastcharNStr);
    Boolean lastcharNgram = Boolean.parseBoolean(lastcharNgramStr);
    Boolean pword = Boolean.parseBoolean(pwordStr);
    Boolean path = Boolean.parseBoolean(pathStr);
    Boolean percentage = Boolean.parseBoolean(percentageStr);
    Boolean ciLin = Boolean.parseBoolean(ciLinStr);
    Boolean ciLinMultipleTag = Boolean.parseBoolean(ciLinMultipleTagStr);
    Boolean topicality = Boolean.parseBoolean(topicalityStr);
    Boolean length = Boolean.parseBoolean(lengthStr);
    Boolean wordNgram = Boolean.parseBoolean(wordNgramStr);

    // Pair<Integer, Integer> chNPrange =
    // validSent.parsedNPwithDEs_deIdx.get(deIdxInSent);
    // Tree chTree = validSent.chParsedTrees.get(0);
    Tree chNPTree = AlignmentUtils.getTreeWithEdges(chTree, chNPrange.first,
        chNPrange.second + 1);
    if (chNPTree == null) {
      chTree.pennPrint(System.err);
      System.err.println("range=" + chNPrange);
      System.err.println("treesize=" + chTree.yield().size());
      throw new RuntimeException();
    }
    int deIdxInPhrase = deIdxInSent - chNPrange.first;
    Tree maskedChNPTree = ExperimentUtils.maskIrrelevantDEs(chNPTree,
        deIdxInPhrase);
    ArrayList<TaggedWord> npYield = maskedChNPTree.taggedYield();

    // (1) make feature list

    // make feature list
    Counter<String> featureList = new ClassicCounter<String>();
    StringBuilder sb = new StringBuilder();

    sb = new StringBuilder();
    String deTag = npYield.get(deIdxInPhrase).tag();
    if (twofeat) {
      sb.append("dePos:").append(deTag);
      featureList.incrementCount(sb.toString());
    }

    if (revised) {
      if ("DEC".equals(deTag)) {
        if (ExperimentUtils.hasVApattern(maskedChNPTree))
          featureList.incrementCount("hasVA");
      } else if ("DEG".equals(deTag)) {
        if (ExperimentUtils.hasADJPpattern(maskedChNPTree))
          featureList.incrementCount("hasADJP");
        if (ExperimentUtils.hasQPpattern(maskedChNPTree))
          featureList.incrementCount("hasQP");
        if (ExperimentUtils.hasNPPNpattern(maskedChNPTree))
          featureList.incrementCount("hasNPPN");
      }
    }

    // get deIndices
    ArrayList<TaggedWord> sentence = chNPTree.taggedYield();
    int deIdx = deIdxInSent - chNPrange.first;

    if (ngram) {
      List<TaggedWord> beforeDE = new ArrayList<TaggedWord>();
      List<TaggedWord> afterDE = new ArrayList<TaggedWord>();

      for (int i = 0; i < sentence.size(); i++) {
        TaggedWord w = sentence.get(i);
        if (i < deIdx)
          beforeDE.add(w);
        if (i > deIdx)
          afterDE.add(w);
      }

      featureList.addAll(posNgramFeatures(beforeDE, "beforeDE:"));
      featureList.addAll(posNgramFeatures(afterDE, "afterDE:"));
      if (beforeDE.size() > 0 && afterDE.size() > 0)
        featureList.incrementCount("crossDE:"
            + beforeDE.get(beforeDE.size() - 1).tag() + "-"
            + afterDE.get(0).tag());
    }

    // (1.X) features from first layer ==> first == false
    if (first) {
      Tree[] children = chNPTree.children();
      List<String> firstLayer = new ArrayList<String>();
      for (Tree t : children) {
        firstLayer.add(t.label().value());
      }
      featureList.addAll(ngramFeatures(firstLayer, "1st:"));
    }

    // (1.2) if the word before DE is a Noun, take the last character
    // of the noun as a feature
    if (lastcharN) {
      if (deIdx - 1 < 0)
        System.err.println("Nothing before DE: " + chNPTree);
      else if (sentence.get(deIdx - 1).tag().startsWith("N")) {
        String word = sentence.get(deIdx - 1).word();
        char[] chars = word.toCharArray();
        featureList.incrementCount("Nchar-" + chars[chars.length - 1]);
      }
    }

    // (1.X) features from last char in each word
    if (lastcharNgram) {
      List<String> lastChars = new ArrayList<String>();
      for (int i = 0; i < sentence.size(); i++) {
        String word = sentence.get(i).word();
        char[] chars = word.toCharArray();
        StringBuilder sb2 = new StringBuilder();
        sb2.append(chars[chars.length - 1]);
        lastChars.add(sb2.toString());
      }
      featureList.addAll(ngramFeatures(lastChars, "lastcngrams:"));
    }

    // (1.3) add the word of the PP before DE
    if (pword) {
      StringBuilder pwords = new StringBuilder();
      for (int i = 0; i < deIdx; i++) {
        String tag = sentence.get(i).tag();
        String word = sentence.get(i).word();
        if (tag.equals("P")) {
          pwords.append(word);
        }
      }
      featureList.incrementCount("Pchar-" + pwords.toString());
    }

    // (1.4) path features
    if (path) {
      featureList.addAll(extractAllPaths(chNPTree, "path:"));
    }

    // (1.X) if the QP is a "percentage"
    if (percentage) {
      if (deIdx == 1
          && (sentence.get(0).word().startsWith("百分之") || sentence.get(0)
              .word().endsWith("％"))) {
        featureList.incrementCount("PERCENTAGE");
      }
      /*
       * if (deIdx == 1 && (sentence.get(0).tag().equals("NR"))) {
       * featureList.incrementCount("NR"); }
       */
      boolean allNR = true;
      for (int i = 0; i < deIdx; i++) {
        TaggedWord tw = sentence.get(i);
        if (!tw.tag().equals("NR")) {
          allNR = false;
          break;
        }
      }
      if (allNR)
        featureList.incrementCount("NR");
    }

    // features using CiLin
    if (ciLin) {
      // Read in CiLin if haven't already done so
      if (cilin_map == null) {
        cilin_map = new HashMap<String, String>();
        cilin_level2map = new HashMap<String, String>();
        cilin_multipleEntry = new HashMap<String, Set<String>>();

        // Read from CiLin file
        String ciLinPath = System.getenv("JAVANLP_HOME")
            + "/projects/mt/src/mt/classifyde/data/CILIN.TXT.utf8";
        String[] cilinLines = IOUtils.slurpFileNoExceptions(ciLinPath).split(
            "\\n");
        System.err.println(cilinLines.length + " lines in CILIN");
        // for (String cilinLine : cilinLines) {
        for (int i = 0; i < cilinLines.length; i++) {
          String cilinLine = cilinLines[i];
          String regex = "^(.*),([A-Z][a-z])\\d+$";
          Pattern pattern = Pattern.compile(regex);
          Matcher matcher = pattern.matcher(cilinLine);
          if (!matcher.find())
            throw new RuntimeException("CILIN line error: " + cilinLine);
          String word = matcher.group(1);
          String cilinLevel2Tag = matcher.group(2);
          StringBuilder cilinTag = new StringBuilder();
          cilinTag.append(cilinLevel2Tag.charAt(0));
          Set<String> cTags = cilin_multipleEntry.get(word);
          if (cTags == null)
            cTags = new TreeSet<String>();
          if (cTags.size() < 1) {
            cilin_map.put(word, cilinTag.toString());
            cilin_level2map.put(word, cilinLevel2Tag);
          }
          cTags.add(cilinTag.toString());
          cilin_multipleEntry.put(word, cTags);
        }
      }

      // map the words into Unigrams
      for (int i = 0; i < sentence.size(); i++) {
        if (i == deIdx)
          continue;

        String w = sentence.get(i).word();
        Set<String> cTags = cilin_multipleEntry.get(w);
        if (ciLinMultipleTag) {
          StringBuilder csb = new StringBuilder();
          if (i < deIdx)
            csb.append("cT_beforeDE:");
          else if (i > deIdx)
            csb.append("cT_afterDE:");
          else
            throw new RuntimeException("never should be here");
          for (String cTag : cTags) {
            csb.append(cTag);
          }
          // System.err.println("==> "+csb.toString()+" <==");
          featureList.incrementCount(csb.toString());
        } else {
          if (cTags == null || cTags.size() == 0 || cTags.size() > 1)
            continue;
          String cTag = cilin_map.get(w);
          if (cTag != null) {
            StringBuilder csb = new StringBuilder();
            if (i < deIdx)
              csb.append("cT_beforeDE:");
            else if (i > deIdx)
              csb.append("cT_afterDE:");
            else
              throw new RuntimeException("never should be here");

            csb.append(cTag);
            // System.err.println("==> "+csb.toString()+" <==");
            featureList.incrementCount(csb.toString());
          }
        }
      }
    }

    // features with topicality
    if (topicality) {
      if (cachedWords == null)
        throw new RuntimeException(
            "-topicality true, but no cachedWords provided");
      // TODO
      Boolean beforeDEInCache = false;
      Boolean afterDEInCache = false;
      for (int i = 0; i < sentence.size(); i++) {
        if (i == deIdx)
          continue;
        String w = sentence.get(i).word();
        String t = sentence.get(i).tag();
        Boolean inCache = cachedWords.contains(w);
        Boolean goodTag = t.startsWith("N");
        if (inCache && goodTag) {
          String cTag = cilin_map.get(w);
          // String c2Tag = cilin_level2map.get(w);
          if (i < deIdx) {
            beforeDEInCache = true;
            // System.err.println("Before-cached: "+w+" in "+sentence);
            StringBuilder builder = new StringBuilder();
            builder.append("beforeDECached-cT:").append(cTag);
            featureList.incrementCount(builder.toString());
          } else if (i > deIdx) {
            afterDEInCache = true;
            // System.err.println(" After-cached: "+w+" in "+sentence);
            StringBuilder builder = new StringBuilder();
            builder.append("afterDECached-cT:").append(cTag);
            featureList.incrementCount(builder.toString());
          } else
            throw new RuntimeException("never should be here");
        }
      }
      if (beforeDEInCache)
        featureList.incrementCount("beforeDEInCache");
      if (afterDEInCache)
        featureList.incrementCount("afterDEInCache");
    }

    if (length) {
      int beforeDElength = deIdxInSent - chNPrange.first;
      int afterDElength = chNPrange.second - deIdxInSent;
      if (beforeDElength == 0) {
        featureList.incrementCount("beforeDElength==0");
      } else {
        featureList.incrementCount("beforeDElength", -Math.log(beforeDElength));
      }
      if (afterDElength == 0) {
        featureList.incrementCount("afterDElength==0");
      } else {
        featureList.incrementCount("afterDElength", -Math.log(afterDElength));
      }
      featureList.incrementCount("wholeLength",
          -Math.log(chNPrange.second - chNPrange.first + 1));
    }

    if (wordNgram) {
      List<String> words = new ArrayList<String>();
      for (int i = 0; i < sentence.size(); i++) {
        String word = sentence.get(i).word();
        words.add(word);
      }
      featureList.addAll(ngramFeatures(words, "wordngrams:"));
    }

    return featureList;
  }

  static Counter<String> posNgramFeatures(List<TaggedWord> words, String prefix) {
    List<String> pos = new ArrayList<String>();
    for (TaggedWord tw : words) {
      pos.add(tw.tag());
    }
    return ngramFeatures(pos, prefix);
  }

  static Counter<String> ngramFeatures(List<String> grams, String prefix) {
    Counter<String> features = new ClassicCounter<String>();
    StringBuilder sb;
    for (int i = -1; i < grams.size(); i++) {
      sb = new StringBuilder();
      sb.append(prefix).append(":");
      if (i == -1)
        sb.append("");
      else
        sb.append(grams.get(i));
      sb.append("-");
      if (i + 1 == grams.size())
        sb.append("");
      else
        sb.append(grams.get(i + 1));
      features.incrementCount(sb.toString());

      if (i != -1) {
        sb = new StringBuilder();
        sb.append(prefix).append(":");
        sb.append(grams.get(i));
        features.incrementCount(sb.toString());
      }
    }
    return features;
  }

  private static Counter<String> extractAllPaths(Tree t, String prefix) {
    Counter<String> l = new ClassicCounter<String>();
    if (t.isPrePreTerminal()) {
      StringBuilder sb = new StringBuilder();
      sb.append(prefix).append(t.label().value());
      l.incrementCount(sb.toString());
      return l;
    } else {
      for (Tree c : t.children()) {
        StringBuilder newPrefix = new StringBuilder();
        newPrefix.append(prefix).append(t.label().value()).append("-");
        l.addAll(extractAllPaths(c, newPrefix.toString()));
      }
      return l;
    }
  }
}
