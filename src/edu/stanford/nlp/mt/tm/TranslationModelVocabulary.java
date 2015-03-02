package edu.stanford.nlp.mt.tm;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;


/**
 * A container for the observed source vocabulary.
 * 
 * @author Spence Green
 *
 */
public final class TranslationModelVocabulary {

  private static Vocabulary sourceVocab;
  private static Vocabulary targetVocab;
  
  private TranslationModelVocabulary() {}

  /**
   * Get the system-wide target vocabulary.
   * 
   * @return
   */
  public static Vocabulary getSourceInstance() {
    if (sourceVocab == null) {
      sourceVocab = getNewVocabulary();
    }
    return sourceVocab;
  }
  
  /**
   * Get the system-wide target vocabulary.
   * @return
   */
  public static Vocabulary getTargetInstance() {
    if (targetVocab == null) {
      targetVocab = getNewVocabulary();
    }
    return targetVocab;
  }
  
  private static Vocabulary getNewVocabulary() {
    return new Vocabulary() {
      Set<Integer> set = new HashSet<>(10000);
      @Override
      public void add(String word) {
        IString istr = new IString(word);
        set.add(istr.id);
      }

      @Override
      public void add(IString word) {
        set.add(word.id);
      }

      @Override
      public void add(Sequence<IString> sequence) {
        for (IString word : sequence) {
          set.add(word.id);
        }
      }

      @Override
      public boolean contains(String word) {
        IString istr = new IString(word);
        return set.contains(istr.id);
      }

      @Override
      public boolean contains(IString word) {
        return set.contains(word.id);
      }

      @Override
      public boolean contains(Sequence<IString> sequence) {
        for (IString word : sequence) {
          if ( ! set.contains(word.id)) {
            return false;
          }
        }
        return true;
      }
    };
  }
  
  public static interface Vocabulary {
    public void add(String word);
    
    public void add(IString word);
    
    public void add(Sequence<IString> sequence);
    
    public boolean contains(String word);
    
    public boolean contains(IString word);
    
    public boolean contains(Sequence<IString> word);
  }
}
