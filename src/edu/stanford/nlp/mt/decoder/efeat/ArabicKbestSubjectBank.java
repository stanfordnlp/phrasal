package mt.decoder.efeat;

import java.io.*;
import java.util.*;

import mt.base.IOTools;

public class ArabicKbestSubjectBank {
  private static ArabicKbestSubjectBank thisInstance = null;
  private final Map<Integer,List<ArabicKbestAnalysis>> kBestSubjectBank;
  
  private boolean isLoaded = false;
  
  protected static final String DELIM = "|||";
  
  protected ArabicKbestSubjectBank() {
    kBestSubjectBank = new HashMap<Integer,List<ArabicKbestAnalysis>>();
  }
  
  public static ArabicKbestSubjectBank getInstance() {
    if(thisInstance == null)
      thisInstance = new ArabicKbestSubjectBank();
    return thisInstance;
  }
    
  /**
   * Collapse identical analyses
   */
  private List<ArabicKbestAnalysis> compress(List<ArabicKbestAnalysis> analyses) {
    List<ArabicKbestAnalysis> compressedList = new ArrayList<ArabicKbestAnalysis>();
    
    for(int i = 0; i < analyses.size(); i++) {
      ArabicKbestAnalysis thisAnal = (ArabicKbestAnalysis) analyses.get(i).clone();
      for(int j = i + 1; j < analyses.size(); j++) {
        if(thisAnal.equals(analyses.get(j))) {
          double newLogScore = Math.log(Math.exp(thisAnal.logCRFScore) + Math.exp(analyses.get(j).logCRFScore));
          thisAnal.logCRFScore = newLogScore;
          analyses.remove(j);
          j--;
        }
      }
      compressedList.add(thisAnal);
    }
    return compressedList;
  }
  
  
  public void load(final File filename) {
    if(isLoaded) return;
    
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {

      int lastTransId = 0;
      boolean hasAtLeastOneSubject = false;
      List<ArabicKbestAnalysis> analysisList = new ArrayList<ArabicKbestAnalysis>();
      
      while(reader.ready()) {
        String line = reader.readLine();
        if(line.trim().equals("")) continue;
        
        ArabicKbestAnalysis analysis = new ArabicKbestAnalysis();
        final StringTokenizer st = new StringTokenizer(line, DELIM);
        int translationId = -1;
        
        for(int i = 0; st.hasMoreTokens(); i++) {
          String token = st.nextToken();
          
          if(i == 0) {
            translationId = Integer.parseInt(token);
            
            if(translationId != lastTransId) {
              if(hasAtLeastOneSubject)
                kBestSubjectBank.put(lastTransId, compress(analysisList));
              hasAtLeastOneSubject = false;
              analysisList = new ArrayList<ArabicKbestAnalysis>();
            }
            
            lastTransId = translationId;
            
          } else if(token.charAt(0) == '{') {
            final String stripped = token.replaceAll("\\{|\\}", "");
            final StringTokenizer verbIndices = new StringTokenizer(stripped,",");
            while(verbIndices.hasMoreTokens()) {
              int verbIdx = Integer.parseInt(verbIndices.nextToken().trim());
              analysis.verbs.add(verbIdx);
            }

          } else if(token.charAt(0) == 's') {
            token = token.substring(1, token.length() - 1);
            analysis.logCRFScore = Double.parseDouble(token);
            
          } else {
            final String[] indices = token.split(",");
            
            assert indices.length == 2;

            final int start = Integer.parseInt(indices[0].trim());
            final int end = Integer.parseInt(indices[1].trim());
            analysis.subjects.put(start, end);
            hasAtLeastOneSubject = true;
          }
        }

        if(translationId == -1)
          throw new RuntimeException(String.format("%s: File format problem at line %d",this.getClass().getName(),reader.getLineNumber()));

        analysisList.add(analysis);
      }
      reader.close();
      
      if(hasAtLeastOneSubject)
        kBestSubjectBank.put(lastTransId, compress(analysisList));
      
      isLoaded = true;

      System.err.printf(">> %s Stats <<\n", this.getClass().getName());
      System.err.printf("sentences: %d\n", kBestSubjectBank.keySet().size());

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not load %s\n", this.getClass().getName(), filename);
    } catch (IOException e) {
      System.err.printf("%s: Failed to read file %s\n",this.getClass().getName(), filename);
    } 
  }
  
  public List<ArabicKbestAnalysis> getAnalyses(final int translationId) {
    if(kBestSubjectBank != null && kBestSubjectBank.containsKey(translationId))
      return new ArrayList<ArabicKbestAnalysis>(kBestSubjectBank.get(translationId));
    return null;
  }
  
  public int getNumSentences() {
    if(this.kBestSubjectBank != null)
      return kBestSubjectBank.keySet().size();
    return 0;
  }

    
  /**
   */
  public static void main(String[] args) {
    File testFile = new File("mt04.vso.k");
    ArabicKbestSubjectBank subjBank = ArabicKbestSubjectBank.getInstance();
    
    subjBank.load(testFile);
    
    Set<Integer> testIds = new HashSet<Integer>();
    testIds.add(8);
    
    for(int transId : testIds) {
      List<ArabicKbestAnalysis> analyses = subjBank.getAnalyses(transId);
      System.out.printf("%d: %d analyses:\n", transId, analyses.size());
      for(ArabicKbestAnalysis anal : analyses)
        System.out.println(anal.toString());
    }
   }

}
