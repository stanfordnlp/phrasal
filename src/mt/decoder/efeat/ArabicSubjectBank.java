package mt.decoder.efeat;

import java.io.*;
import java.util.*;

import mt.base.Sequence;
import mt.base.SimpleSequence;
import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.util.Pair;

public final class ArabicSubjectBank {
  private static ArabicSubjectBank thisInstance = null;
  private final Map<Sequence<IString>,SentenceData> subjectBank;
  private boolean isLoaded = false;

  public static final int MAX_SUBJ_LENGTH = 5;
  private static final String DELIM = "|||";

  protected ArabicSubjectBank() {
    subjectBank = new HashMap<Sequence<IString>,SentenceData>();
  }

  public static ArabicSubjectBank getInstance() {
    if(thisInstance == null)
      thisInstance = new ArabicSubjectBank();
    return thisInstance;
  }

  private class SentenceData {
    public SentenceData() {
      subjSpans = new ArrayList<Pair<Integer,Integer>>();
      verbs = new HashMap<Integer,Integer>();
    }
    public List<Pair<Integer,Integer>> subjSpans;
    public Map<Integer,Integer> verbs;
  }

  public void load(final File filename) {
    if(isLoaded) return;
    try {
      final LineNumberReader reader = new LineNumberReader(new InputStreamReader(new FileInputStream(filename),"UTF-8"));

      int nullSubjects = 0;
      int numSubjects = 0;
      int numVerbs = 0;
      while(reader.ready()) {
        final SentenceData newSent = new SentenceData();
        Sequence<IString> sentence = null;

        final StringTokenizer st = new StringTokenizer(reader.readLine(),DELIM);
        for(int i = 0; st.hasMoreTokens(); i++) {
          final String token = st.nextToken();
          if(i == 0) {
            sentence = new SimpleSequence<IString>(true, IStrings.toIStringArray(token.split("\\s+")));

          } else if(token.contains("{")) {
            final String stripped = token.replaceAll("\\{|\\}", "");
            final StringTokenizer verbIndices = new StringTokenizer(stripped,",");
            while(verbIndices.hasMoreTokens()) {
              int verbIdx = Integer.parseInt(verbIndices.nextToken().trim());
              newSent.verbs.put(verbIdx,verbIdx);
            }

          } else {
            final String[] indices = token.split(",");
            assert indices.length == 2;

            final int start = Integer.parseInt(indices[0].trim());
            final int end = Integer.parseInt(indices[1].trim());
            if(end - start < MAX_SUBJ_LENGTH)
              newSent.subjSpans.add(new Pair<Integer,Integer>(start,end));
          }
        }

        if(sentence == null)
          throw new RuntimeException(String.format("%s: File format problem at line %d",this.getClass().getName(),reader.getLineNumber()));

        if(newSent.subjSpans.size() == 0) 
          nullSubjects++;
        else
          numSubjects += newSent.subjSpans.size();
        
        numVerbs += newSent.verbs.keySet().size();
        subjectBank.put(sentence, newSent);
      }

      reader.close();
      isLoaded = true;

      System.err.printf(">> %s Stats <<\n", this.getClass().getName());
      System.err.printf("sentences: %d\n", subjectBank.keySet().size());
      System.err.printf("null: %d\n", nullSubjects);
      System.err.printf("subjects: %d\n", numSubjects);
      System.err.printf("verbs: %d\n", numVerbs);

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not load %s\n", this.getClass().getName(), filename);
    } catch (IOException e) {
      System.err.printf("%s: Failed to read file %s\n",this.getClass().getName(), filename);
    }
  }

  public List<Pair<Integer,Integer>> subjectsForSentence(Sequence<IString> foreign) {
    if(!isLoaded)
      throw new RuntimeException(String.format("%s: Subject bank not initialized (subj)", this.getClass().getName()));

    if(subjectBank.get(foreign) == null)
      throw new RuntimeException(String.format("%s: Could not find subjects for |||%s|||",this.getClass().getName(),foreign.toString()));
    
    return Collections.unmodifiableList(subjectBank.get(foreign).subjSpans);
  }

  public Map<Integer,Integer> verbsForSentence(Sequence<IString> foreign) {
    if(!isLoaded)
      throw new RuntimeException(String.format("%s: Subject bank not initialized (verb)", this.getClass().getName()));

    if(subjectBank.get(foreign) == null)
      throw new RuntimeException(String.format("%s: Could not find verbs for |||%s|||",this.getClass().getName(),foreign.toString()));

    return Collections.unmodifiableMap(subjectBank.get(foreign).verbs);
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    ArabicSubjectBank asb = ArabicSubjectBank.getInstance();
    asb.load(new File("/home/rayder441/sandbox/SubjDetector/mt04.unk.vso-feat"));
  }
}
