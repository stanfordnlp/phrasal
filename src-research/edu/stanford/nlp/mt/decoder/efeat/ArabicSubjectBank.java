package edu.stanford.nlp.mt.decoder.efeat;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.util.MutablePair;
import edu.stanford.nlp.util.Triple;

public class ArabicSubjectBank {
  protected static ArabicSubjectBank thisInstance = null;
  protected final Map<Integer, SentenceData> subjectBank;
  protected boolean isLoaded = false;

  protected static final String DELIM = "|||";

  protected ArabicSubjectBank() {
    subjectBank = new HashMap<Integer, SentenceData>();
  }

  private class SentenceData {
    public SentenceData() {
      subjects = new ArrayList<Triple<Integer, Integer, Integer>>();
    }

    public List<Triple<Integer, Integer, Integer>> subjects;
  }

  public static ArabicSubjectBank getInstance() {
    if (thisInstance == null)
      thisInstance = new ArabicSubjectBank();
    return thisInstance;
  }

  public void load(final File filename, final int maxSubjLen, final int verbGap) {
    if (isLoaded)
      return;
    try {
      final LineNumberReader reader = new LineNumberReader(
          new InputStreamReader(new FileInputStream(filename), "UTF-8"));

      int nullSubjects = 0;
      int numSubjects = 0;
      int numVerbs = 0;
      int transId = 0;
      while (reader.ready()) {
        final List<MutablePair<Integer, Integer>> subjSpans = new ArrayList<MutablePair<Integer, Integer>>();
        final Set<Integer> verbs = new HashSet<Integer>();

        final StringTokenizer st = new StringTokenizer(reader.readLine(), DELIM);
        for (int i = 0; st.hasMoreTokens(); i++) {
          final String token = st.nextToken();
          if (i == 0) {
            transId = Integer.parseInt(token.trim());

          } else if (token.contains("{")) {
            final String stripped = token.replaceAll("\\{|\\}", "");
            final StringTokenizer verbIndices = new StringTokenizer(stripped,
                ",");
            while (verbIndices.hasMoreTokens()) {
              int verbIdx = Integer.parseInt(verbIndices.nextToken().trim());
              verbs.add(verbIdx);
            }

          } else {
            final String[] indices = token.split(",");
            assert indices.length == 2;

            final int start = Integer.parseInt(indices[0].trim());
            final int end = Integer.parseInt(indices[1].trim());
            if (end - start < maxSubjLen)
              subjSpans.add(new MutablePair<Integer, Integer>(start, end));
          }
        }

        // Now build the sentence data representation
        final SentenceData newSent = new SentenceData();
        if (subjSpans.size() == 0) {
          nullSubjects++;
        } else {
          for (MutablePair<Integer, Integer> subject : subjSpans) {
            int start = subject.first();
            for (int i = 1; i <= verbGap; i++) {
              if (verbs.contains(start - i)) {
                Triple<Integer, Integer, Integer> subjVerbGroup = new Triple<Integer, Integer, Integer>(
                    start - i, subject.first(), subject.second());
                newSent.subjects.add(subjVerbGroup);
                break;
              }
            }
          }
        }

        numSubjects += newSent.subjects.size();
        numVerbs += verbs.size();
        subjectBank.put(transId, newSent);
      }

      reader.close();
      isLoaded = true;

      System.err.printf(">> %s Stats <<\n", this.getClass().getName());
      System.err.printf("sentences: %d\n", subjectBank.keySet().size());
      System.err.printf("null: %d\n", nullSubjects);
      System.err.printf("subjects: %d\n", numSubjects);
      System.err.printf("verbs: %d\n", numVerbs);

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not load %s\n", this.getClass().getName(),
          filename);
    } catch (IOException e) {
      System.err.printf("%s: Failed to read file %s\n", this.getClass()
          .getName(), filename);
    }
  }

  public List<Triple<Integer, Integer, Integer>> subjectsForSentence(
      final int translationId) {
    if (!isLoaded)
      throw new RuntimeException(String.format(
          "%s: Subject bank not initialized (subj)", this.getClass().getName()));

    if (subjectBank.get(translationId) == null)
      throw new RuntimeException(String.format(
          "%s: Could not find subjects for id %d", this.getClass().getName(),
          translationId));

    return Collections
        .unmodifiableList(subjectBank.get(translationId).subjects);
  }

  /**
   */
  public static void main(String[] args) {
    ArabicSubjectBank asb = ArabicSubjectBank.getInstance();
    asb.load(new File("/home/rayder441/sandbox/SubjDetector/debug.vso"), 100, 2);

    for (int transId = 0; transId < 10; transId++) {
      System.out.printf(">> TransId %d <<\n", transId);
      List<Triple<Integer, Integer, Integer>> subjs = asb
          .subjectsForSentence(transId);
      for (Triple<Integer, Integer, Integer> subj : subjs)
        System.out.println(subj.toString());
    }
  }
}
