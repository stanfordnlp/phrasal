package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.PaddedList;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegacyHypothesisList {
  //List<Word> srcWords;
  Tree srcTree;
  Map<Integer, Map<Integer, String>> srcDeps;
  private int nbestSize = 0;

  List<Candidate> cands;
  List<LegacyAlignment> alignments;
  String extraFeatFiles;
  FeatureIndex featureIndex;
  String[] auxiliaryFeaturesFns;

  public LegacyHypothesisList(Tree srcTree, List<Candidate> Cands, 
    List<LegacyAlignment> alignments, Map<Integer, Map<Integer, String>> srcDeps, 
                        FeatureIndex featureIndex, String extraFeatFiles,
                        String[] auxiliaryFeaturesFns) {

    this.featureIndex = featureIndex;
    this.srcTree = srcTree;
    this.cands = Cands;
    this.alignments = alignments;
    this.srcDeps = srcDeps;
    this.extraFeatFiles = extraFeatFiles;
    this.auxiliaryFeaturesFns = auxiliaryFeaturesFns;
  }


  public List<Candidate> getCands() { return cands; }
  
  public LegacyHypothesisList() { cands = new ArrayList<Candidate>(); }
  
  public Map<Integer, Double> extractFeaturesFromCand(Candidate cand) {
    Map<Integer, Double> featureMap = new HashMap<Integer, Double>(); 
    PaddedList<TaggedWord> tgtSent 
      = new PaddedList<TaggedWord>(cand.getTree().taggedYield(new ArrayList<TaggedWord>()), new TaggedWord());

    // this part is for word/tag unigram/bigram
    for (int i = 0; i <= tgtSent.size(); i++) {
      TaggedWord cur_tw = tgtSent.get(i);
      TaggedWord pre_tw = tgtSent.get(i-1);
      
      // TODO: add more features extractors here
      appendBigramFeatures(pre_tw, cur_tw, featureMap);
      appendTagBigramFeatures(pre_tw, cur_tw, featureMap);
      if (i != tgtSent.size()) {
        appendUnigramFeatures(cur_tw, featureMap);
        appendTagUnigramFeatures(cur_tw, featureMap);
      }
    }
    return featureMap;
  }

  public void appendUnigramFeatures(TaggedWord cur_tw, 
    Map<Integer, Double> featureMap) {
    String s = cur_tw.word();
    StringBuilder sb = new StringBuilder();
    sb.append("W-UNI-");
    sb.append(s);
    featureIndex.add(sb.toString());
    int idx = featureIndex.indexOf(sb.toString());
    featureMap.put(idx, 1.0);
  }

  public void appendBigramFeatures(TaggedWord pre_tw, TaggedWord cur_tw,
    Map<Integer, Double> featureMap) {

    String pre = pre_tw.word();
    String cur = cur_tw.word();

    StringBuilder sb = new StringBuilder();
    sb.append("W-BI-");
    sb.append(pre);
    sb.append("-");
    sb.append(cur);
    featureIndex.add(sb.toString());
    int idx = featureIndex.indexOf(sb.toString());
    featureMap.put(idx, 1.0);
  }


  public void appendTagUnigramFeatures(TaggedWord cur_tw, 
    Map<Integer, Double> featureMap) {

    String s = cur_tw.tag();
    StringBuilder sb = new StringBuilder();
    sb.append("T-UNI-");
    sb.append(s);
    featureIndex.add(sb.toString());
    int idx = featureIndex.indexOf(sb.toString());
    featureMap.put(idx, 1.0);
  }

  public void appendTagBigramFeatures(TaggedWord pre_tw, TaggedWord cur_tw, 
    Map<Integer, Double> featureMap) {

    String pre = pre_tw.tag();
    String cur = cur_tw.tag();

    StringBuilder sb = new StringBuilder();
    sb.append("T-BI-");
    sb.append(pre);
    sb.append("-");
    sb.append(cur);
    featureIndex.add(sb.toString());
    int idx = featureIndex.indexOf(sb.toString());
    featureMap.put(idx, 1.0);
  }

  // immediate (1-path) Grammatical Relation Mapping Features
  void immediateGRMappingFeatures(Candidate cand, LegacyAlignment alignment, 
    Map<Integer, Double> featureMap) {
    if (srcDeps == null || alignment == null) {
      throw new RuntimeException("Grammatical Relation Mapping features - "+
        " require srcDeps and alignments");
    }
    // go through each dependency in English Dependencies
    Map<Integer, Map<Integer, String>> enMap = cand.getDeps();
    for (Integer enGovIdx : enMap.keySet()) {
      Map<Integer, String> subMap = enMap.get(enGovIdx);
      for (Integer enDepIdx : subMap.keySet()) {
        String depName = subMap.get(enDepIdx);
        StringBuilder sb = new StringBuilder();
        sb.append("1GR-");
        sb.append(depName);
        sb.append("-");
        List<Integer> chGovIndices = alignment.get(enGovIdx);
        List<Integer> chDepIndices = alignment.get(enDepIdx);
        for (int chGovIdx : chGovIndices) {
          Map<Integer, String> map = srcDeps.get(chGovIdx);
          if (map != null) {
            for (int chDepIdx : chDepIndices) {
              String cd = map.get(chDepIdx);
              if (cd != null) {
                StringBuilder sb2 = new StringBuilder(sb);
                sb2.append(cd);
                //System.err.println("FEAT:"+sb.toString());
                featureIndex.add(sb2.toString());
                int idx = featureIndex.indexOf(sb2.toString());
                featureMap.put(idx, 1.0);
              }
            }
          }
        }
      }
    }
  }

  public void appendFeaturesFromFile(BufferedReader br, 
    Map<Integer, Double> featureMap) throws IOException {
    String line = br.readLine();
    if (line==null) {
      throw new RuntimeException(
        "candidates number from the feature file doesn't match");
    }
    //Pattern p = Pattern.compile("([^:]+): ([\\d\\.]+)");
    Pattern p = Pattern.compile("([^\\s]*?): ([\\d\\.]+)");
    Matcher m = p.matcher(line);
    while(m.find()) {
      String featName = m.group(1);
      double fWeight = Double.parseDouble(m.group(2));
      featureIndex.add(featName);
      int idx = featureIndex.indexOf(featName);
      featureMap.put(idx, fWeight);
      //System.err.println("DEBUG: "+featName+"="+fWeight+"\n");
    }
  }
  
  @SuppressWarnings("deprecation")
	public CompactHypothesisList extractFeatures(int dataPointIdx) 
    throws IOException {

    if (alignments != null) {
      assert cands.size() == alignments.size();
      if (cands.size() != alignments.size()) {
        throw new RuntimeException("cands.size != alignment.size");
      }
    }

    BufferedReader featBr = null;
    if (extraFeatFiles != null) {
      featBr = new BufferedReader(new FileReader(extraFeatFiles));
    }

    int cntHyps = cands.size();
    int[][] fIndices = new int[cntHyps][];
    float[][] fValues = new float[cntHyps][];
    double[] bleus = new double[cntHyps];

    for (int i = 0; i < cands.size(); i++) {
      Candidate cand = cands.get(i);
      LegacyAlignment alignment = (alignments != null ? alignments.get(i) : null);

      Map<Integer, Double> candFeats = extractFeaturesFromCand(cand);
      immediateGRMappingFeatures(cand, alignment, candFeats);

      if (featBr != null) {
        appendFeaturesFromFile(featBr, candFeats);
      }

      Set<Integer> featureIndices = new TreeSet<Integer>(candFeats.keySet());
      
      int[] fIndex = new int[featureIndices.size()];
      float[] fValue = new float[featureIndices.size()];
      
      int featId = 0; for (Integer index : featureIndices) {
        fIndex[featId] = index;
        fValue[featId] = new Float(candFeats.get(index));
      featId++; }
      fIndices[nbestSize] = fIndex;
      fValues[nbestSize] = fValue; 
      bleus[nbestSize] = cand.getBleu();
      nbestSize++;
    }

    if (featBr != null) featBr.close();
    return new CompactHypothesisList(fIndices, fValues, bleus, nbestSize, 
      featureIndex);
  }

  public int size() { return cands.size(); }
}
