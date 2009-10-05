package mt.decoder.efeat;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

public class ArabicKbestSubjectBank {
  private static ArabicKbestSubjectBank thisInstance = null;
  private final Map<Integer,kBestSentenceData> kBestSubjectBank;
  
  private boolean isLoaded = false;
  private int maxVerbGap = 2;
  
  protected static final String DELIM = "|||";
  
  protected ArabicKbestSubjectBank() {
    kBestSubjectBank = new HashMap<Integer,kBestSentenceData>();
  }
  
  public static ArabicKbestSubjectBank getInstance() {
    if(thisInstance == null)
      thisInstance = new ArabicKbestSubjectBank();
    return thisInstance;
  }
  
  private class kSentenceData {
    public kSentenceData() {
      subjSpans = new ArrayList<Pair<Integer,Integer>>();
      verbs = new HashSet<Integer>();
    }
    public List<Pair<Integer,Integer>> subjSpans;
    public Set<Integer> verbs;
    public double score = 0.0;
  }
  
  protected class kBestSentenceData {
    private List<kSentenceData> kHypotheses;
    public boolean hasSubject;
    private Map<Integer,List<Triple<Integer,Integer,Double>>> invIndex;
    private SortedSet<Integer> verbs;
    
    public kBestSentenceData() {
      kHypotheses = new ArrayList<kSentenceData>();
      hasSubject = false;
    }
    
    /**
     * Do an insertion sort
     */
    public void addSentence(kSentenceData sent) {
      for(int i = 0; i < kHypotheses.size(); i++) {
        if(sent.score > kHypotheses.get(i).score) {
          kHypotheses.add(i, sent);
          return;
        }
      }
      kHypotheses.add(sent);
    }
    
    public void makeInvertedIndex() {
      if(kHypotheses != null && kHypotheses.size() != 0) {
        invIndex = new HashMap<Integer,List<Triple<Integer,Integer,Double>>>();
        verbs = new TreeSet<Integer>();
        
        Iterator<Integer> itr = kHypotheses.get(0).verbs.iterator();
        while(itr.hasNext()) {
          int verbIdx = itr.next();
          List<Triple<Integer,Integer,Double>> thisVerbsSubjects = new ArrayList<Triple<Integer,Integer,Double>>();
          for(int k = 0; k < kHypotheses.size(); k++) {
            List<Pair<Integer,Integer>> subjects = (List<Pair<Integer, Integer>>) kHypotheses.get(k).subjSpans;
            double kScore = kHypotheses.get(k).score;
            
            for(Pair<Integer,Integer> subject : subjects) {
              int verbGap = subject.first() - verbIdx;
              if(verbGap > 0 && verbGap <= maxVerbGap) {
                Triple<Integer,Integer,Double> newSubj = new Triple<Integer,Integer,Double>(subject.first(),subject.second(),kScore);
                thisVerbsSubjects.add(newSubj); //This will be sorted by default
              }
            }
          }
          if(thisVerbsSubjects.size() != 0) {
            invIndex.put(verbIdx, thisVerbsSubjects);
            verbs.add(verbIdx);
          }
        }        
      }
    }
    
  }
  
  
  public void load(final File filename, final int maxSubjLen, final int verbGap) {
    if(isLoaded) return;
    
    maxVerbGap = verbGap;
    
    try {
      final LineNumberReader reader = new LineNumberReader(new InputStreamReader(new FileInputStream(filename),"UTF-8"));

      int nullSubjects = 0;
      int lastTransId = 0;
      kBestSentenceData kbest = new kBestSentenceData();
      while(reader.ready()) {
        final kSentenceData newSent = new kSentenceData();

        final StringTokenizer st = new StringTokenizer(reader.readLine(),DELIM);
        int translationId = -1;
        for(int i = 0; st.hasMoreTokens(); i++) {
          String token = st.nextToken();
          if(i == 0) {
            translationId = Integer.parseInt(token);
            if(translationId != lastTransId) {
              if(!kbest.hasSubject)
                nullSubjects++;
              kbest.makeInvertedIndex();
              kBestSubjectBank.put(lastTransId, kbest);
              kbest = new kBestSentenceData();
            }
            lastTransId = translationId;
            
          } else if(token.charAt(0) == '{') {
            final String stripped = token.replaceAll("\\{|\\}", "");
            final StringTokenizer verbIndices = new StringTokenizer(stripped,",");
            while(verbIndices.hasMoreTokens()) {
              int verbIdx = Integer.parseInt(verbIndices.nextToken().trim());
              newSent.verbs.add(verbIdx);
            }

          } else if(token.charAt(0) == 's') {
            token = token.substring(1, token.length() - 1);
            newSent.score = Double.parseDouble(token);
            
          } else {
            final String[] indices = token.split(",");
            assert indices.length == 2;

            final int start = Integer.parseInt(indices[0].trim());
            final int end = Integer.parseInt(indices[1].trim());
            if(end - start < maxSubjLen)
              newSent.subjSpans.add(new Pair<Integer,Integer>(start,end));
          }
        }

        if(translationId == -1)
          throw new RuntimeException(String.format("%s: File format problem at line %d",this.getClass().getName(),reader.getLineNumber()));

        if(newSent.subjSpans.size() != 0) 
          kbest.hasSubject = true;
      
        kbest.addSentence(newSent);
      }
      
      if(!kbest.hasSubject)
        nullSubjects++;
      kbest.makeInvertedIndex();
      kBestSubjectBank.put(lastTransId, kbest);

      reader.close();
      isLoaded = true;

      System.err.printf(">> %s Stats <<\n", this.getClass().getName());
      System.err.printf("sentences: %d\n", kBestSubjectBank.keySet().size());
      System.err.printf("null: %d\n", nullSubjects);

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not load %s\n", this.getClass().getName(), filename);
    } catch (IOException e) {
      System.err.printf("%s: Failed to read file %s\n",this.getClass().getName(), filename);
    }
    
    
  }
  
  //WSGDEBUG: These methods are used to ensure that the kbest list is read in properly
  public int getK(final int translationId) {
    if(kBestSubjectBank.get(translationId) != null)
      return kBestSubjectBank.get(translationId).kHypotheses.size();

    return 0;
  }
  
  public List<Pair<Integer,Integer>> subjectsForSentence(final int translationId, final int k) {
    kBestSentenceData kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null && k <= kbest.kHypotheses.size())
      return kbest.kHypotheses.get(k).subjSpans;

    return null;
  }
  
  public Set<Integer> verbsForSentence(final int translationId, final int k) {
    kBestSentenceData kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null && k <= kbest.kHypotheses.size())
      return kbest.kHypotheses.get(k).verbs;

    return null;
  }
  
  public double getScore(final int translationId, final int k) {
    kBestSentenceData kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null && k <= kbest.kHypotheses.size())
      return kbest.kHypotheses.get(k).score;

    return 0.0;
  }
  
  
  
  public SortedSet<Integer> getVerbs(final int translationId) {
    kBestSentenceData kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null)
      return kbest.verbs;
    return null;
  }
  
  public List<Triple<Integer,Integer,Double>> getSubjectsForVerb(final int translationId, final int verbIdx) {
    kBestSentenceData kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null && kbest.invIndex != null)
      return kbest.invIndex.get(verbIdx);
    return null;
  }
  
  public int getNumSentences() {
    if(this.kBestSubjectBank != null)
      return kBestSubjectBank.keySet().size();
    return 0;
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    File testFile = new File("kbest.debug");
    ArabicKbestSubjectBank subjBank = ArabicKbestSubjectBank.getInstance();
    
    subjBank.load(testFile, 100, 2);
    
    for(int transId = 0; transId < subjBank.getNumSentences(); transId++) {
      int kMax = subjBank.getK(transId);
      for(int k = 0; k < kMax; k++) {
        List<Pair<Integer,Integer>> subjs = subjBank.subjectsForSentence(transId, k);
        Set<Integer> verbs = subjBank.verbsForSentence(transId, k);
        double score = subjBank.getScore(transId, k);
        
        System.out.printf("%d \\ %d \\ %f\n", transId,k,score);
        for(Pair<Integer,Integer> subj : subjs)
          System.out.printf("  s: {%d,%d}\n", subj.first(),subj.second());
        
        for(int verbIdx : verbs)
          System.out.printf("  v: %d\n", verbIdx);
      }
      
      //Test the inverted index
      System.out.println(">> Inverted Index <<");
      SortedSet<Integer> verbs = subjBank.getVerbs(transId);
      Iterator<Integer> itr = verbs.iterator();
      while(itr.hasNext()) {
        int verbIdx = itr.next();
        for(Triple<Integer,Integer,Double> subj : subjBank.getSubjectsForVerb(transId, verbIdx))
          System.out.printf("%d --> %d %d (%f)\n", verbIdx,subj.first(),subj.second(),subj.third());
      }
      System.out.println(">> ============== <<");
      System.out.println();
    }
    
  }

}
