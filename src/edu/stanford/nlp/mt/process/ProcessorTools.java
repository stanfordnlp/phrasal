package edu.stanford.nlp.mt.process;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.LineIterator;
import edu.stanford.nlp.process.SerializableFunction;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.Generics;

/**
 * 
 * TODO(spenceg): Maybe need to do label set pruning?
 * 
 * @author Spence Green
 *
 */
public final class ProcessorTools {

  private static final String OP_DELETE = "D";
  private static final String OP_REPLACE = "R";
  private static final String OP_TOUPPER = "TU";
  private static final String OP_INSERT_BEFORE = "IB";
  private static final String OP_INSERT_AFTER = "IA";
  private static final String OP_NONE = "N";
  private static final String OP_WHITESPACE = ".#.";
  
  // Delimiter must *not* be a regex special character!
  private static final String OP_DELIM = "#";
  private static final String WHITESPACE = ".##.";
  private static final String WHITESPACE_INTERNAL = ".###.";

  // Needleman-Wunsch parameters
  private static final int gapPenalty = -1;
  private static final int penalty = -2;
  
  
  private ProcessorTools(){}
  
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
    List<CoreLabel> sequence = Generics.newArrayList(alignment.eSize() * 7);
    
    for (int i = 0; i < alignment.fSize(); ++i) {
      Set<Integer> eAlignments = alignment.f2e(i);
      List<String> eTokens = Generics.newArrayList(eAlignments.size());
      for (int j : eAlignments) {
        eTokens.add(alignment.e().get(j).toString());
      }
      if (sequence.size() > 0) sequence.add(createDatum(WHITESPACE, OP_WHITESPACE, sequence.size()));
      sequence.addAll(toSequence(alignment.f().get(i).toString(), eTokens, sequence.size()));
    }
    return sequence;
  }

  
  private static List<CoreLabel> toSequence(String rawToken,
      List<String> tokenList, int sequenceIndex) {
    
    StringBuilder sb = new StringBuilder();
    for (String s : tokenList) {
      if (sb.length() > 0) sb.append(WHITESPACE_INTERNAL);
      sb.append(s);
    }
    String target = sb.toString();
    int[] t2sGrid = alignStrings(rawToken, target);
    assert t2sGrid.length == target.length();
    
    // Loop over the target
    List<CoreLabel> sequence = Generics.newArrayList(target.length());
    int[] s2t = new int[rawToken.length()];
    Arrays.fill(s2t, -1);
    for (int i = 0; i < target.length(); ++i) {
      String tChar = String.valueOf(target.charAt(i));
      int sIndex = t2sGrid[i];
      if (sIndex < 0) {
        // Delete (insert target character)
        sequence.add(createDatum(tChar, OP_DELETE, i));
      } else {
        String sChar = String.valueOf(rawToken.charAt(sIndex));
        assert sIndex < s2t.length;
        s2t[sIndex] = i;
        if (tChar.equals(sChar)) {
          // NoOp
          sequence.add(createDatum(tChar, OP_NONE, i));
        } else if (tChar.equals(sChar.toLowerCase())) {
          // Uppercase
          sequence.add(createDatum(tChar, OP_TOUPPER, i));
        } else {
          // Replace
          String label = OP_REPLACE + OP_DELIM + sChar;
          sequence.add(createDatum(tChar, label, i));
        }
      }
    }
    
    // Now look for unaligned source spans (deleted source spans)
    for (int i = 0; i < rawToken.length(); ) {
      if (s2t[i] >= 0) continue;
      int j = i + 1;
      while (j < rawToken.length() && s2t[j] < 0) ++j;
      // Span i/j is uncovered
      int p = s2t[i];
      int q = s2t[j];
      // Span p/q in the target bounds this gap
      String pLabel = sequence.get(p).get(CoreAnnotations.GoldAnswerAnnotation.class);
      String qLabel = sequence.get(q).get(CoreAnnotations.GoldAnswerAnnotation.class);
      if (pLabel.equals(OP_NONE)) {
        // Insert after
        String label = OP_INSERT_AFTER + OP_DELIM + rawToken.substring(i, j);
        sequence.get(p).set(CoreAnnotations.GoldAnswerAnnotation.class, label);
        
      } else if (qLabel.equals(OP_NONE)) {
        // Insert before
        String label = OP_INSERT_BEFORE + OP_DELIM + rawToken.substring(i, j);
        sequence.get(q).set(CoreAnnotations.GoldAnswerAnnotation.class, label);
      
      } else {
        // TODO(spenceg): How often does this happen. What to do here?
        System.err.printf("WARNING: Unmanageable span (%s): %s -> %s%n", rawToken.substring(i,j), rawToken, target);
      }
    }
    return sequence;
  }

  /**
   * Needleman-Wunch. Orientation is t2s since we want to know how each target
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
    int[] t2sGrid = new int[grid[0].length];
    int i = grid.length;
    int j = grid[0].length;
    while (i > 0 && j > 0) {
      int simScore = sim(source.charAt(i), target.charAt(j));
      if (i > 0 && j > 0 && grid[i][j] == grid[i-1][j-1] + simScore) {
        t2sGrid[j] = i;
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
    Arrays.fill(t2sGrid, -1);
    return t2sGrid;
  }

  private static int[][] forwardPass(String source, String target) {
    int[][] grid = new int[source.length()][];
    grid[0] = new int[target.length()];
    for (int j = 0; j < grid[0].length; ++j) {
      grid[0][j] = gapPenalty * j;
    }
    for (int i = 1; i < source.length(); ++i) {
      grid[i] = new int[target.length()];
      grid[i][0] = gapPenalty * i;
      for (int j = 1; j < grid[i].length; ++j) {
        int matchCost = grid[i-1][j-1] + sim(source.charAt(i), target.charAt(j));
        int deleteCost = grid[i-1][j] + gapPenalty;
        int insertCost = grid[i][j-1] + gapPenalty;
        grid[i][j] = Math.max(matchCost, Math.max(deleteCost, insertCost));
      }
    }
    return grid;
  }

  private static int sim(char char1, char char2) {
    boolean isMatch = String.valueOf(char1).toLowerCase().equals(String.valueOf(char2).toLowerCase());
    return isMatch ? 1 : penalty;
  }

  private static CoreLabel createDatum(String token, String label, int index) {
    CoreLabel newTok = new CoreLabel();
    newTok.set(CoreAnnotations.CharAnnotation.class, token);
    newTok.set(CoreAnnotations.AnswerAnnotation.class, label);
    newTok.set(CoreAnnotations.GoldAnswerAnnotation.class, label);
    newTok.setIndex(index);
    return newTok;
  }
  
  /**
   * Convert a post-processed character sequence to a token sequence.
   * 
   * @param charSequence
   * @return
   */
  public static List<CoreLabel> toPostProcessedSequence(List<CoreLabel> charSequence) {
    List<CoreLabel> tokenSequence = Generics.newArrayList();
    StringBuilder originalToken = new StringBuilder();
    StringBuilder currentToken = new StringBuilder();
    
    // Cause the processing loop to terminate
    CoreLabel stopSymbol = new CoreLabel();
    stopSymbol.set(CharAnnotation.class, WHITESPACE);
    charSequence.add(stopSymbol);
    
    for (CoreLabel outputChar : charSequence) {
      String text = outputChar.get(CharAnnotation.class);
      String label = outputChar.get(AnswerAnnotation.class);
      if (text.equals(WHITESPACE)) {
        // Process originalToken and currentToken
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
        if (text.equals(WHITESPACE_INTERNAL)) {
          originalToken.append(" ");
          currentToken.append(" ");
        
        } else {
          originalToken.append(text);
          if (label.equals(OP_NONE)) {
            currentToken.append(text);
            
          } else if (label.startsWith(OP_INSERT_AFTER)) {
            String[] fields = label.split(OP_DELIM);
            assert fields.length == 2;
            currentToken.append(text).append(fields[1]);
            
          } else if (label.startsWith(OP_INSERT_BEFORE)) {
            String[] fields = label.split(OP_DELIM);
            assert fields.length == 2;
            currentToken.append(fields[1]).append(text);
            
          } else if (label.startsWith(OP_REPLACE)) {
            String[] fields = label.split(OP_DELIM);
            assert fields.length == 2;
            currentToken.append(fields[1]);
            
          } else if (label.equals(OP_TOUPPER)) {
            currentToken.append(text.toUpperCase());
            
          } else if (label.equals(OP_DELETE)) {
            // delete output character
          }
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
  public static class PostProcessorDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

    private static final long serialVersionUID = -7761401510813091925L;

    private final IteratorFromReaderFactory<List<CoreLabel>> factory;
    private final Preprocessor preProcessor;
    
    public PostProcessorDocumentReaderAndWriter(Preprocessor preprocessor) {
      this.preProcessor = preprocessor;
      this.factory = LineIterator.getFactory(new SerializableFunction<String, List<CoreLabel>>() {
        private static final long serialVersionUID = 3695624909844929834L;
        @Override
        public List<CoreLabel> apply(String in) {
          SymmetricalWordAlignment alignment = preProcessor.process(in);
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
                                  word.get(CoreAnnotations.CharAnnotation.class));
      }
    }
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub
  }
}
