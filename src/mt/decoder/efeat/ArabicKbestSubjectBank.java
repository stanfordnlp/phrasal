package mt.decoder.efeat;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

public class ArabicKbestSubjectBank {
  private static ArabicKbestSubjectBank thisInstance = null;
  private final Map<Integer,AnalysisIndex> kBestSubjectBank;
  
  private boolean isLoaded = false;
  private int maxVerbGap = 2;
  private int maxSubjectLen = 5;
  
  protected static final String DELIM = "|||";
  
  protected ArabicKbestSubjectBank() {
    kBestSubjectBank = new HashMap<Integer,AnalysisIndex>();
  }
  
  public static ArabicKbestSubjectBank getInstance() {
    if(thisInstance == null)
      thisInstance = new ArabicKbestSubjectBank();
    return thisInstance;
  }
  
  private class SentenceAnalysis {
    public SentenceAnalysis() {
      subjSpans = new ArrayList<Pair<Integer,Integer>>();
      verbs = new HashSet<Integer>();
    }
    public List<Pair<Integer,Integer>> subjSpans;
    public Set<Integer> verbs;
    public double score = 0.0;
  }
  
  protected class AnalysisIndex {
    private List<SentenceAnalysis> kAnalyses;
    public boolean hasAnalysis;
    private Map<Integer,List<Triple<Integer,Integer,Double>>> index;
    private SortedSet<Integer> verbs;
    private double nullRealAccumulator = 0.0;
    
    public AnalysisIndex() {
      kAnalyses = new ArrayList<SentenceAnalysis>();
      verbs = new TreeSet<Integer>();
      hasAnalysis = false;
    }
    
    /**
     * Do an insertion sort
     */
    public void addAnalysis(SentenceAnalysis analysis) {
      for(int i = 0; i < kAnalyses.size(); i++) {
        if(analysis.score > kAnalyses.get(i).score) {
          kAnalyses.add(i, analysis);
          verbs.addAll(analysis.verbs);
          hasAnalysis = (verbs.size() != 0);
          return;
        }
      }
      kAnalyses.add(analysis);
      hasAnalysis = (verbs.size() != 0);
    }
    
    public void addNullAnalysis(SentenceAnalysis analysis) {
      if(analysis != null)
        nullRealAccumulator += Math.exp(analysis.score);
    }
    
    private void findAnalyses(final int verbIdx, final int nextVerbIdx) {

      Map<Pair<Integer,Integer>,Double> subjectMap = new HashMap<Pair<Integer,Integer>,Double>();
      
      double noAnalysisAccumulator = 0.0;
      for(SentenceAnalysis analysis : kAnalyses) {
        
        double realAnalysisScore = Math.exp(analysis.score);
        boolean noAnalysis = true;
        for(Pair<Integer,Integer> subject : analysis.subjSpans) {
          int length = subject.second() - subject.first() + 1;
          int verbGap = subject.first() - verbIdx;
          
          if(verbGap < 0 || length > maxSubjectLen) {
            continue;
          
          } else if(verbGap <= maxVerbGap) {
            if(subjectMap.containsKey(subject)) {
              double score = realAnalysisScore + subjectMap.get(subject);
              subjectMap.put(subject, score);
            } else
              subjectMap.put(subject, realAnalysisScore);
            
            noAnalysis = false;
            
          } else if(subject.first() >= nextVerbIdx)
            break;
        }
        
        if(noAnalysis)
          noAnalysisAccumulator += realAnalysisScore;
      }
      
      //Now convert the scores to log space and add to the index
      List<Triple<Integer,Integer,Double>> subjList = new ArrayList<Triple<Integer,Integer,Double>>();
      for(Pair<Integer,Integer> subject : subjectMap.keySet()) {
        double logScore = Math.log(subjectMap.get(subject));
        subjList.add(new Triple<Integer,Integer,Double>(subject.first(),subject.second(),logScore));
      }
      if(noAnalysisAccumulator != 0.0)
        subjList.add(new Triple<Integer,Integer,Double>(-1,-1, Math.log(noAnalysisAccumulator)));
      
      index.put(verbIdx, subjList);
    }
    
    
    /**
     * Accumulates negative log-likelihoods. The yes/no cases are distinguished by
     * the span (-1 in  for should *not* re-order).
     */
    public void makeIndex() {
      if(kAnalyses.size() != 0 && index == null) {
        index = new HashMap<Integer,List<Triple<Integer,Integer,Double>>>();
        
        //For each verb, see if the analysis has a subject
        int lastIdx = -1;
        for(final int nextIdx : verbs) {
          if(lastIdx != -1)
            findAnalyses(lastIdx, nextIdx);
          
          lastIdx = nextIdx;
        }
        
        //Don't forget to process the last verb here
        findAnalyses(lastIdx,Integer.MAX_VALUE);
        
        verbs = new TreeSet<Integer>(index.keySet());
        kAnalyses = null; //Mark for gc
        
        if(nullRealAccumulator != 0.0)
          nullRealAccumulator = Math.log(nullRealAccumulator);
        
      } else
        throw new RuntimeException("Attempted to make index for empty analysis set");
    }   
  }
  
  
  public void load(final File filename, final int maxSubjLen, final int verbGap) {
    if(isLoaded) return;
    
    //Set indexing options
    maxVerbGap = verbGap;
    maxSubjectLen = maxSubjLen;
    
    try {
      final LineNumberReader reader = new LineNumberReader(new InputStreamReader(new FileInputStream(filename),"UTF-8"));

      int nullAnalyses = 0;
      int lastTransId = 0;
      AnalysisIndex analysisIndex = new AnalysisIndex();
      while(reader.ready()) {
        String line = reader.readLine();
        if(line.trim().equals("")) continue;
        
        final SentenceAnalysis newAnalysis = new SentenceAnalysis();
        final StringTokenizer st = new StringTokenizer(line, DELIM);
        int translationId = -1;
        for(int i = 0; st.hasMoreTokens(); i++) {
          String token = st.nextToken();
          
          if(i == 0) {
            translationId = Integer.parseInt(token);
            
            if(translationId != lastTransId) {
              if(analysisIndex.hasAnalysis) {
                analysisIndex.makeIndex();
                kBestSubjectBank.put(lastTransId, analysisIndex);
              } else
                nullAnalyses++;
              
              analysisIndex = new AnalysisIndex();
            }
            
            lastTransId = translationId;
            
          } else if(token.charAt(0) == '{') {
            final String stripped = token.replaceAll("\\{|\\}", "");
            final StringTokenizer verbIndices = new StringTokenizer(stripped,",");
            while(verbIndices.hasMoreTokens()) {
              int verbIdx = Integer.parseInt(verbIndices.nextToken().trim());
              newAnalysis.verbs.add(verbIdx);
            }

          } else if(token.charAt(0) == 's') {
            token = token.substring(1, token.length() - 1);
            newAnalysis.score = Double.parseDouble(token);
            
          } else {
            final String[] indices = token.split(",");
            
            assert indices.length == 2;

            final int start = Integer.parseInt(indices[0].trim());
            final int end = Integer.parseInt(indices[1].trim());
            newAnalysis.subjSpans.add(new Pair<Integer,Integer>(start,end));
          }
        }

        if(translationId == -1)
          throw new RuntimeException(String.format("%s: File format problem at line %d",this.getClass().getName(),reader.getLineNumber()));

        if(newAnalysis.subjSpans.size() == 0)
          analysisIndex.addNullAnalysis(newAnalysis);
        else
          analysisIndex.addAnalysis(newAnalysis);
      }
      
      if(analysisIndex.hasAnalysis) {
        analysisIndex.makeIndex();
        kBestSubjectBank.put(lastTransId, analysisIndex);
      } else
        nullAnalyses++;

      reader.close();
      isLoaded = true;

      System.err.printf(">> %s Stats <<\n", this.getClass().getName());
      System.err.printf("sentences: %d\n", kBestSubjectBank.keySet().size());
      System.err.printf("null:      %d\n", nullAnalyses);

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not load %s\n", this.getClass().getName(), filename);
    } catch (IOException e) {
      System.err.printf("%s: Failed to read file %s\n",this.getClass().getName(), filename);
    } 
  }
  
  public SortedSet<Integer> getVerbs(final int translationId) {
    AnalysisIndex kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null)
      return kbest.verbs;
    return null;
  }
  
  public List<Triple<Integer,Integer,Double>> getSubjectsForVerb(final int translationId, final int verbIdx) {
    AnalysisIndex kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null && kbest.index != null)
      return kbest.index.get(verbIdx);
    return null;
  }
  
  public int getNumSentences() {
    if(this.kBestSubjectBank != null)
      return kBestSubjectBank.keySet().size();
    return 0;
  }

  
  /*********************************************************************************
   *  WSGDEBUG: Unit test methods
  **********************************************************************************/
  
  public List<Pair<Integer,Integer>> subjectsForSentence(final int translationId, final int k) {
    AnalysisIndex kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null && k <= kbest.kAnalyses.size())
      return kbest.kAnalyses.get(k).subjSpans;

    return null;
  }
  
  public Set<Integer> verbsForSentence(final int translationId, final int k) {
    AnalysisIndex kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null && k <= kbest.kAnalyses.size())
      return kbest.kAnalyses.get(k).verbs;

    return null;
  }
  
  public double getScore(final int translationId, final int k) {
    AnalysisIndex kbest = this.kBestSubjectBank.get(translationId);
    if(kbest != null && k <= kbest.kAnalyses.size())
      return kbest.kAnalyses.get(k).score;

    return 0.0;
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    File testFile = new File("debug.kbest");
    ArabicKbestSubjectBank subjBank = ArabicKbestSubjectBank.getInstance();
    
    subjBank.load(testFile, 100, 2);
    
    for(int transId = 0; transId < 4; transId++) {
      System.out.printf("============ TransID %d ============\n", transId);
      SortedSet<Integer> verbs = subjBank.getVerbs(transId);
      if(verbs == null || verbs.size() == 0)
        System.out.println(" No verbs");
      else {
        System.out.print("verbs = {");
        for(final int verbIdx : verbs)
          System.out.printf("%d ",verbIdx);
        System.out.println("}");       

        //Test the inverted index
        System.out.println(">> Inverted Index <<");
        for(final int verbIdx : verbs) {
          for(Triple<Integer,Integer,Double> subj : subjBank.getSubjectsForVerb(transId, verbIdx))
            System.out.printf("%d --> %d %d (%f)\n", verbIdx,subj.first(),subj.second(),subj.third());
        }
        System.out.println();
      }
    }    
  }

}
