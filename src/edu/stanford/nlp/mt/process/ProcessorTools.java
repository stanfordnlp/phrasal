package edu.stanford.nlp.mt.process;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.LineIterator;
import edu.stanford.nlp.process.SerializableFunction;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;


/**
 * Utility methods for character-level, sequence-based post-processors.
 * 
 * TODO(spenceg): Maybe need to do label set pruning for InsertBefore, InsertAfter and Replace here?
 * We'd only allow those operations after seeing the operation a certain number of times.
 * 
 * @author Spence Green
 *
 */
public final class ProcessorTools {

  // TODO: Re-enable the Replace class
//  public static enum Operation {Delete, Replace, ToUpper, InsertBefore, InsertAfter, None, Whitespace};
  public static enum Operation {Delete, ToUpper, InsertBefore, InsertAfter, None, Whitespace};
    
  // Delimiter must *not* be a regex special character!
  private static final String OP_DELIM = "#";
  public static final String WHITESPACE = " ";
  
  // Needleman-Wunsch parameters
  private static final int gapPenalty = -1;
  private static final int penalty = -2;
  
  
  private ProcessorTools() {}
  
  /**
   * Convert a string to an unlabeled character sequence. Applies whitespace
   * tokenization.
   * 
   * @param str
   * @return
   */
  public static List<CoreLabel> toCharacterSequence(String str) {
    Sequence<IString> sequence = IStrings.tokenize(str);
    return toCharacterSequence(sequence);
  }
  
  /**
   * Convert a tokenized sequence to a character string.
   * 
   * @param tokenSequence
   * @return
   */
  public static List<CoreLabel> toCharacterSequence(Sequence<IString> tokenSequence) {
    List<CoreLabel> charSequence = new ArrayList<>(tokenSequence.size() * 7);
    for (IString token : tokenSequence) {
      String tokenStr = token.toString();
      if (charSequence.size() > 0) {
        CoreLabel charLabel = new CoreLabel();
        charLabel.set(CoreAnnotations.TextAnnotation.class, WHITESPACE);
        charLabel.set(CoreAnnotations.CharAnnotation.class, WHITESPACE);
        charLabel.set(CoreAnnotations.ParentAnnotation.class, WHITESPACE);
        charLabel.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, -1);
        charLabel.setIndex(charSequence.size());
        charSequence.add(charLabel);
      }
      for (int j = 0, size = tokenStr.length(); j < size; ++j) {
        CoreLabel charLabel = new CoreLabel();
        String ch = String.valueOf(tokenStr.charAt(j));
        charLabel.set(CoreAnnotations.TextAnnotation.class, ch);
        charLabel.set(CoreAnnotations.CharAnnotation.class, ch);
        charLabel.set(CoreAnnotations.ParentAnnotation.class, tokenStr);
        charLabel.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, j);
        charLabel.setIndex(charSequence.size());
        charSequence.add(charLabel);
      }
    } 
    return charSequence;
  }
  
  /**
   * Convert a raw/preprocessed String pair to a labeled sequence appropriate for training
   * the CRF-based post-processor.
   * 
   * The SymmetricalWordAlignment is created by a Preprocessor. Source is the raw input, target is
   * the tokenized/pre-processed output.
   * 
   * @return
   */
  public static List<CoreLabel> alignedPairToLabeledSequence(SymmetricalWordAlignment alignment) {
    List<CoreLabel> sequence = new ArrayList<>(alignment.eSize() * 7);
    
    for (int i = 0; i < alignment.fSize(); ++i) {
      if (sequence.size() > 0) sequence.add(createDatum(WHITESPACE, Operation.Whitespace.toString(), sequence.size(), WHITESPACE, 0));
      String token = alignment.f().get(i).toString();
      Set<Integer> eAlignments = alignment.f2e(i);
      if (eAlignments.size() == 0) {
        System.err.printf("%s: WARNING: discarding unaligned token (%s)%n", ProcessorTools.class.getName(), token);
        
      } else {
        List<String> eTokens = new ArrayList<>(eAlignments.size());
        for (int j : eAlignments) {
          eTokens.add(alignment.e().get(j).toString());
        }
        List<CoreLabel> charSequence = toSequence(token, eTokens, sequence.size());
        sequence.addAll(charSequence);
      }
    }
    return sequence;
  }

  /**
   * Create the observed label sequence from a training example.
   * 
   * NOTE: this method ignores punctuation normalization, which causes a huge blow-up
   * in the number of Replace and Insert operations. Most pre-processors convert punctuation
   * to ASCII equivalents, which is probably acceptable as output for most users.
   * 
   * @param sourceToken
   * @param targetTokens
   * @param outputIndex
   * @return
   */
  private static List<CoreLabel> toSequence(String sourceToken, List<String> targetTokens, int outputIndex) {
    // Concatenate the target tokens
    StringBuilder sb = new StringBuilder();
    for (String s : targetTokens) {
      if (sb.length() > 0) sb.append(WHITESPACE);
      sb.append(s);
    }
    String target = sb.toString();
    int[] t2sGrid = alignStrings(sourceToken, target);
    assert t2sGrid.length == target.length();
    
    // Loop over the target
    List<CoreLabel> sequence = new ArrayList<>(target.length());
    int[] s2t = new int[sourceToken.length()];
    Arrays.fill(s2t, -1);
    int parentIndex = 0;
    int charIndex = 0;
    for (int i = 0; i < t2sGrid.length; ++i) {
      String tChar = String.valueOf(target.charAt(i));
      if (tChar.equals(WHITESPACE)) {
        ++parentIndex;
        charIndex = -1;
      }
      String parentToken = parentIndex >= targetTokens.size() ? "#NoNe#" : targetTokens.get(parentIndex);
      int sIndex = t2sGrid[i];
      if (sIndex < 0) {
        // Delete (insert target character)
        sequence.add(createDatum(tChar, Operation.Delete.toString(), i, parentToken, charIndex));
      } else {
        String sChar = String.valueOf(sourceToken.charAt(sIndex));
        assert sIndex < s2t.length;
        s2t[sIndex] = i;
        if (tChar.equals(sChar) || TokenUtils.isPunctuation(sChar)) {
          // NoOp
          sequence.add(createDatum(tChar, Operation.None.toString(), i, parentToken, charIndex));
        } else if (tChar.equals(sChar.toLowerCase())) {
          // Uppercase
          sequence.add(createDatum(tChar, Operation.ToUpper.toString(), i, parentToken, charIndex));
        } else {
          // Replace
          // TODO(spenceg): Re-enable at some point
//          String label = Operation.Replace.toString() + OP_DELIM + sChar;
//          sequence.add(createDatum(tChar, label, i, parentToken, charIndex));
          sequence.add(createDatum(tChar, Operation.None.toString(), i, parentToken, charIndex));
        }
      }
      ++charIndex;
    }
    
    // Now look for unaligned source spans (deleted source spans)
    for (int i = 0; i < s2t.length; ++i) {
      if (s2t[i] >= 0) continue;
      int j = i + 1;
      while (j < s2t.length && s2t[j] < 0) ++j;
      // Source span i/j is uncovered
      int p = i > 0 ? s2t[i-1] : -1;
      int q = j < s2t.length ? s2t[j] : -1;
      // Span p/q in the target bounds this gap
      String pLabel = p > 0 ? sequence.get(p).get(CoreAnnotations.GoldAnswerAnnotation.class) : null;
      String qLabel = q > 0 ? sequence.get(q).get(CoreAnnotations.GoldAnswerAnnotation.class) : null;
      Operation pOperation = null;
      Operation qOperation = null;
      try {
        pOperation = pLabel == null ? null : Operation.valueOf(pLabel);
      } catch (Exception e) {
        // The label is lexicalized, so it clearly isn't None,
        // which is the label we're seeking.
      }
      try {
        qOperation = qLabel == null ? null : Operation.valueOf(qLabel);
      } catch (Exception e) {
        // The label is lexicalized, so it clearly isn't None,
        // which is the label we're seeking.
      }
      
      if (pOperation != null && pOperation == Operation.None) {
        // Insert after
        String span = sourceToken.substring(i, j);
        if ( ! TokenUtils.isPunctuation(span)) {
          String label = Operation.InsertAfter.toString() + OP_DELIM + span;
          sequence.get(p).set(CoreAnnotations.GoldAnswerAnnotation.class, label);
        }
        
      } else if (qOperation == Operation.None) {
        // Insert before
        String span = sourceToken.substring(i, j);
        if ( ! TokenUtils.isPunctuation(span)) {
          String label = Operation.InsertBefore.toString() + OP_DELIM + span;
          sequence.get(q).set(CoreAnnotations.GoldAnswerAnnotation.class, label);
        }
        
      } else {
        if (Pattern.compile("\u00AD").matcher(sourceToken).find()) {
          // Soft hyphen nonsense. Do nothing
        } else {
          System.err.printf("WARNING: Unmanageable span (%s): %s -> %s%n", sourceToken.substring(i,j), sourceToken, target);
        }
      }
    }
    return sequence;
  }
  
  /**
   * Needleman-Wunsch. Orientation is t2s since we want to know how each target
   * character was produced.
   * 
   * http://en.wikipedia.org/wiki/Needleman%E2%80%93Wunsch_algorithm
   * 
   * @param source
   * @param target
   * @return
   */
  private static int[] alignStrings(String source, String target) {
    int[][] grid = forwardPass(source, target); 
    int[] t2sGrid = backwardPass(grid, source, target);
    return t2sGrid;
  }

  private static int[] backwardPass(int[][] grid, String source, String target) {
    int[] t2sGrid = new int[target.length()];
    Arrays.fill(t2sGrid, -1);
    int i = grid.length - 1;
    int j = grid[0].length - 1;
    while (i > 0 && j > 0) {
      // Convert from 1-indexing to 0-indexing
      int sourceIdx = i-1;
      int targetIdx = j-1;
      
      int simScore = sim(source.charAt(sourceIdx), target.charAt(targetIdx));
      if (i > 0 && j > 0 && grid[i][j] == grid[i-1][j-1] + simScore) {
        t2sGrid[targetIdx] = sourceIdx;
        --i;
        --j;
        
      } else if (i > 0 && grid[i][j] == grid[i-1][j] + gapPenalty) {
        --i;
        
      } else if (j > 0 && grid[i][j] == grid[i][j-1] + gapPenalty) {
        --j;
        
      } else {
        throw new RuntimeException("Corrupt alignment grid");
      }
    }
    return t2sGrid;
  }

  private static int[][] forwardPass(String source, String target) {
    int[][] grid = new int[source.length()+1][];
    grid[0] = new int[target.length()+1];
    for (int j = 0; j < grid[0].length; ++j) {
      grid[0][j] = gapPenalty * j;
    }
    for (int i = 1; i < grid.length; ++i) {
      grid[i] = new int[target.length()+1];
      grid[i][0] = gapPenalty * i;
      for (int j = 1; j < grid[i].length; ++j) {
        // Convert from 1-indexing to 0-indexing
        int sourceIdx = i-1;
        int targetIdx = j-1;
        
        int matchCost = grid[i-1][j-1] + sim(source.charAt(sourceIdx), target.charAt(targetIdx));
        int deleteCost = grid[i-1][j] + gapPenalty;
        int insertCost = grid[i][j-1] + gapPenalty;
        grid[i][j] = Math.max(matchCost, Math.max(deleteCost, insertCost));
      }
    }
    return grid;
  }

  /**
   * Similarity score of two characters.
   * 
   * @param char1
   * @param char2
   * @return
   */
  private static int sim(char char1, char char2) {
    // Strict lexical match.
    boolean isMatch = String.valueOf(char1).toLowerCase().equals(String.valueOf(char2).toLowerCase());
    return isMatch ? 1 : penalty;
  }

  private static CoreLabel createDatum(String character, String label, int index, String parentToken, int charIndex) {
    CoreLabel labeledCharacter = new CoreLabel();
    labeledCharacter.set(CoreAnnotations.TextAnnotation.class, character);
    labeledCharacter.set(CoreAnnotations.CharAnnotation.class, character);
    labeledCharacter.set(CoreAnnotations.ParentAnnotation.class, parentToken);
    labeledCharacter.set(CoreAnnotations.AnswerAnnotation.class, label);
    labeledCharacter.set(CoreAnnotations.GoldAnswerAnnotation.class, label);
    labeledCharacter.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, charIndex);
    labeledCharacter.setIndex(index);
    return labeledCharacter;
  }
  
  /**
   * Convert a post-processed character sequence to a token sequence.
   * 
   * @param charSequence
   * @return
   */
  public static List<CoreLabel> toPostProcessedSequence(List<CoreLabel> charSequence) {
    List<CoreLabel> tokenSequence = new ArrayList<>();
    StringBuilder originalToken = new StringBuilder();
    StringBuilder currentToken = new StringBuilder();
    
    // Cause the processing loop to terminate
    CoreLabel stopSymbol = new CoreLabel();
    stopSymbol.set(CharAnnotation.class, WHITESPACE);
    stopSymbol.set(AnswerAnnotation.class, Operation.Whitespace.toString());
    charSequence.add(stopSymbol);
    
    for (CoreLabel outputChar : charSequence) {
      String text = outputChar.get(CharAnnotation.class);
      String[] fields = outputChar.get(AnswerAnnotation.class).split(OP_DELIM);
      Operation label;
      try {
        label = Operation.valueOf(fields[0]);
      } catch (IllegalArgumentException e) {
        System.err.printf("%s: WARNING Illegal operation %s/%s%n", ProcessorTools.class.getName(), text, fields[0]);
        label = Operation.None;
      }
      if (label == Operation.Whitespace || (label == Operation.None && text.equals(WHITESPACE))) {
        // This is the token delimiter.
        String original = originalToken.toString();
        String[] outputTokens = currentToken.toString().split("\\s+");
        for (String tokenText : outputTokens) {
          CoreLabel token = new CoreLabel();
          token.setValue(tokenText);
          token.setWord(tokenText);
          token.set(OriginalTextAnnotation.class, original);
          tokenSequence.add(token);
        }
        originalToken = new StringBuilder();
        currentToken = new StringBuilder();
        
      } else {
        originalToken.append(text);
        if (label == Operation.None) {
          currentToken.append(text);

        } else if (label == Operation.InsertAfter) {
          assert fields.length == 2;
          currentToken.append(text).append(fields[1]);

        } else if (label == Operation.InsertBefore) {
          assert fields.length == 2;
          currentToken.append(fields[1]).append(text);

        } 
        // TODO(spenceg): Re-enable
//        else if (label == Operation.Replace) {
//          assert fields.length == 2;
//          currentToken.append(fields[1]);
//
//        } 
        else if (label == Operation.ToUpper) {
          currentToken.append(text.toUpperCase());

        } else if (label == Operation.Delete) {
          // delete output character
        }
      }
    }
    // Remove the stop symbol
    charSequence.remove(charSequence.size()-1);
    return tokenSequence;
  }
  
  /**
   * Creates training data for the CRF-based post-processor.
   * 
   * @author Spence Green
   *
   */
  public static class PostprocessorDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

    private static final long serialVersionUID = -7761401510813091925L;

    private final IteratorFromReaderFactory<List<CoreLabel>> factory;
    private final Preprocessor preProcessor;
    
    public PostprocessorDocumentReaderAndWriter(Preprocessor preprocessor) {
      this.preProcessor = preprocessor;
      this.factory = LineIterator.getFactory(new SerializableFunction<String, List<CoreLabel>>() {
        private static final long serialVersionUID = 3695624909844929834L;
        @Override
        public List<CoreLabel> apply(String in) {
          SymmetricalWordAlignment alignment = preProcessor.processAndAlign(in.trim());
          return ProcessorTools.alignedPairToLabeledSequence(alignment);
        }
      });
    }
    
    @Override
    public Iterator<List<CoreLabel>> getIterator(Reader r) {
      return factory.getIterator(r);
    }

    @Override
    public void init(SeqClassifierFlags flags) {}

    @Override
    public void printAnswers(List<CoreLabel> doc, PrintWriter pw) {
      pw.println("Answer\tGoldAnswer\tCharacter");
      for(CoreLabel word : doc) {
        pw.printf("%s\t%s\t%s%n", word.get(CoreAnnotations.AnswerAnnotation.class),
                                  word.get(CoreAnnotations.GoldAnswerAnnotation.class),
                                  word.get(CoreAnnotations.TextAnnotation.class));
      }
    }
  }
}
