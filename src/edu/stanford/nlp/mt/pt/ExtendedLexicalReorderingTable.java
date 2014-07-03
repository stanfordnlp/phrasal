package edu.stanford.nlp.mt.pt;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.train.AlignmentTemplate;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * Similar to MosesLexicalizedReorderingTable, but adds classes not supported in
 * Moses: left-discontinuous and right-discontinuous.
 * 
 * @author danielcer
 * @author Michel Galley
 * 
 */
public class ExtendedLexicalReorderingTable {

  /**
   * Reordering types
   * 
   * <pre>
   * Monotone with Previous      Monotone with Next
   * 
   *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
   * f:                         f:         
   *  F_0  PPP                   F_0  
   *  F_1     PPP                F_1      PPP
   *  F_2                        F_2         PPP
   * 
   * 
   * Swap with Previous      Swap with Next
   * 
   *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
   * f:                         f:         
   *  F_0      PPP               F_0  
   *  F_1  PPP                   F_1         PPP      
   *  F_2                        F_2      PPP   
   *  
   * Discontinuous with Prev  Discontinuous with Next
   * 
   *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
   * f:                         f:         
   *  F_0      PPP               F_0          PPP
   *  F_1                        F_1               
   *  F_2  PPP                   F_2      PPP
   * 
   * </pre>
   * 
   * NonMonotone: Swap <em>or</em> Discontinuous.
   * 
   * @author danielcer
   * 
   */

  public enum ReorderingTypes {
    monotoneWithPrevious, swapWithPrevious, discontinuousWithPrevious, nonMonotoneWithPrevious, monotoneWithNext, swapWithNext, discontinuousWithNext, nonMonotoneWithNext, discontinuous2WithPrevious, discontinuous2WithNext, containmentWithPrevious, containmentWithNext
  }

  enum ConditionTypes {
    f, e, fe
  }

  static final ReorderingTypes[] msdPositionMapping = {
      ReorderingTypes.monotoneWithPrevious, ReorderingTypes.swapWithPrevious,
      ReorderingTypes.discontinuousWithPrevious };

  static final ReorderingTypes[] msdBidirectionalPositionMapping = {
      ReorderingTypes.monotoneWithPrevious, ReorderingTypes.swapWithPrevious,
      ReorderingTypes.discontinuousWithPrevious,
      ReorderingTypes.monotoneWithNext, ReorderingTypes.swapWithNext,
      ReorderingTypes.discontinuousWithNext };

  static final ReorderingTypes[] msd2BidirectionalPositionMapping = {
      ReorderingTypes.monotoneWithPrevious, ReorderingTypes.swapWithPrevious,
      ReorderingTypes.discontinuousWithPrevious,
      ReorderingTypes.discontinuous2WithPrevious,
      ReorderingTypes.monotoneWithNext, ReorderingTypes.swapWithNext,
      ReorderingTypes.discontinuousWithNext,
      ReorderingTypes.discontinuous2WithNext };

  static final ReorderingTypes[] msd2cBidirectionalPositionMapping = {
      ReorderingTypes.monotoneWithPrevious, ReorderingTypes.swapWithPrevious,
      ReorderingTypes.discontinuousWithPrevious,
      ReorderingTypes.discontinuous2WithPrevious,
      ReorderingTypes.containmentWithPrevious,
      ReorderingTypes.monotoneWithNext, ReorderingTypes.swapWithNext,
      ReorderingTypes.discontinuousWithNext,
      ReorderingTypes.discontinuous2WithNext,
      ReorderingTypes.containmentWithNext };

  static final ReorderingTypes[] monotonicityPositionalMapping = {
      ReorderingTypes.monotoneWithPrevious,
      ReorderingTypes.nonMonotoneWithPrevious };

  static final ReorderingTypes[] monotonicityBidirectionalMapping = {
      ReorderingTypes.monotoneWithPrevious,
      ReorderingTypes.nonMonotoneWithPrevious,
      ReorderingTypes.monotoneWithNext, ReorderingTypes.nonMonotoneWithNext };

  static final Map<String, Object> fileTypeToReorderingType = Generics.newHashMap();

  static {
    fileTypeToReorderingType.put("msd-fe", msdPositionMapping);
    fileTypeToReorderingType.put("msd-bidirectional-fe",
        msdBidirectionalPositionMapping);
    fileTypeToReorderingType.put("msd2-bidirectional-fe",
        msd2BidirectionalPositionMapping);
    fileTypeToReorderingType.put("msd2c-bidirectional-fe",
        msd2cBidirectionalPositionMapping);
    fileTypeToReorderingType.put("monotonicity-fe",
        monotonicityPositionalMapping);
    fileTypeToReorderingType.put("monotonicity-bidirectional-fe",
        monotonicityBidirectionalMapping);
    fileTypeToReorderingType.put("msd-f", msdPositionMapping);
    fileTypeToReorderingType.put("msd-bidirectional-f",
        msdBidirectionalPositionMapping);
    fileTypeToReorderingType.put("msd2-bidirectional-f",
        msd2BidirectionalPositionMapping);
    fileTypeToReorderingType.put("msd2c-bidirectional-f",
        msd2cBidirectionalPositionMapping);
    fileTypeToReorderingType.put("monotonicity-f",
        monotonicityPositionalMapping);
    fileTypeToReorderingType.put("monotonicity-bidirectional-f",
        monotonicityBidirectionalMapping);
  }

  static final Map<String, ConditionTypes> fileTypeToConditionType = Generics.newHashMap();

  static {
    fileTypeToConditionType.put("msd-fe", ConditionTypes.fe);
    fileTypeToConditionType.put("msd-bidirectional-fe", ConditionTypes.fe);
    fileTypeToConditionType.put("msd2-bidirectional-fe", ConditionTypes.fe);
    fileTypeToConditionType.put("msd2c-bidirectional-fe", ConditionTypes.fe);
    fileTypeToConditionType.put("monotonicity-fe", ConditionTypes.fe);
    fileTypeToConditionType.put("monotonicity-bidirectional-fe",
        ConditionTypes.fe);
    fileTypeToConditionType.put("msd-f", ConditionTypes.f);
    fileTypeToConditionType.put("msd-bidirectional-f", ConditionTypes.f);
    fileTypeToConditionType.put("msd2-bidirectional-f", ConditionTypes.f);
    fileTypeToConditionType.put("msd2c-bidirectional-f", ConditionTypes.f);
    fileTypeToConditionType.put("monotonicity-f", ConditionTypes.f);
    fileTypeToConditionType.put("monotonicity-bidirectional-f",
        ConditionTypes.f);
  }

  public final String filetype;
  private final List<float[]> reorderingScores;
  private final PhraseTable<IString> phraseTable;

  public final ReorderingTypes[] positionalMapping;
  public final ConditionTypes conditionType;

  public float[] getReorderingScores(Rule<IString> rule) {
    int reorderingId = -1;
    if (rule.isSynthetic()) {
      // Do nothing
//    } else if (conditionType == ConditionTypes.f) {
//      reorderingId = FlatPhraseTable.ruleIndex.get(rule.id)[0];
//    } else if (conditionType == ConditionTypes.e) {
//      reorderingId = FlatPhraseTable.ruleIndex.get(rule.id)[1];
//    } 
    } else if (conditionType == ConditionTypes.fe) {
      reorderingId = rule.id;
    }
    reorderingId -= phraseTable.minRuleIndex();
    return reorderingId >= 0 && reorderingId < reorderingScores.size() ? reorderingScores.get(reorderingId) : null;
  }

  public ExtendedLexicalReorderingTable(String filename, PhraseTable<IString> phraseTable, String desiredFileType)
      throws IOException {
    this.phraseTable = phraseTable;
    this.reorderingScores = Generics.newArrayList(phraseTable.size());
    for (int i = 0, sz = phraseTable.size(); i < sz; ++i) this.reorderingScores.add(null);

    String filetype = init(filename, desiredFileType);
    if (!desiredFileType.equals(filetype)) {
      throw new RuntimeException(String.format(
          "Reordering file '%s' of type %s not %s", filename, filetype,
          desiredFileType));
    }
    
    this.filetype = filetype;
    this.positionalMapping = (ReorderingTypes[]) fileTypeToReorderingType
        .get(filetype);
    this.conditionType = fileTypeToConditionType.get(filetype);    
  }

  private String init(String filename, String type) throws IOException {
    boolean withGaps = Phrasal.withGaps;
    Runtime rt = Runtime.getRuntime();
    long preTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();

    System.err.printf("Loading extended Moses Lexical Reordering Table: %s%n", filename);
    ReorderingTypes[] positionalMapping = null;
    ConditionTypes conditionType = null;
    String selectedFiletype = null;
    if (type == null) {
      for (String filetype : fileTypeToReorderingType.keySet()) {
        if (filename.contains(filetype)) {
          positionalMapping = (ReorderingTypes[]) fileTypeToReorderingType
              .get(filetype);
          conditionType = fileTypeToConditionType.get(filetype);
          selectedFiletype = filetype;
          break;
        }
      }
    } else {
      positionalMapping = (ReorderingTypes[]) fileTypeToReorderingType
          .get(type);
      conditionType = fileTypeToConditionType.get(type);
      selectedFiletype = type;
    }

    if (positionalMapping == null) {
      throw new RuntimeException(String.format(
          "Unable to determine lexical re-ordering file type for: %s",
          filename));
    }

    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    for (String line; (line = reader.readLine()) != null; ) {
      final List<List<String>> fields = StringUtils.splitFieldsFast(line, AlignmentTemplate.DELIM);
      
      List<String> srcTokens;
      List<String> tgtTokens = null;
      List<String> scoreList;
      if (fields.size() == 2) {
        // TODO(spenceg): This format is not used anymore. Deprecate this condition.
        srcTokens = fields.get(0);
        scoreList = fields.get(1);
        
      } else if (fields.size() == 3) {
        // Standard phrase table format without alignments
        srcTokens = fields.get(0);
        tgtTokens = fields.get(1);
        scoreList = fields.get(2);
        
      } else if (fields.size() >= 5) {
        // Standard phrase table format with alignments
        srcTokens = fields.get(0);
        tgtTokens = fields.get(1);
        scoreList = fields.get(4);
        
      } else {
        throw new RuntimeException("Invalid re-ordering table line: " + String.valueOf(reader.getLineNumber()));
      }

      if (scoreList.size() != positionalMapping.length) {
        throw new RuntimeException(
            String
                .format(
                    "File type '%s' requires that %d scores be provided for each entry, however only %d were found (line %d)",
                    filetype, positionalMapping.length, scoreList.size(),
                    reader.getLineNumber()));
      }
      
      int idx = -1;
      if (conditionType == ConditionTypes.e
          || conditionType == ConditionTypes.f) {
        IString[] tokens = IStrings.toIStringArray(srcTokens);
        final int[] indexInts = withGaps ? DTUTable.toWordIndexArray(tokens) : IStrings.toIntArray(tokens);
        //TODO(spenceg): This lookup is broken as of 2 July 2014. It broke sometime earlier, but I only
        //discovered the bug today.
        throw new RuntimeException("unidirectional conditioning is not implemented / broken");
        
      } else {
        IString[] fTokens = IStrings.toIStringArray(srcTokens);
        int[] fIndexInts = withGaps ? DTUTable.toWordIndexArray(fTokens) : IStrings.toIntArray(fTokens);
        IString[] eTokens = IStrings.toIStringArray(tgtTokens);
        int[] eIndexInts = withGaps ? DTUTable.toWordIndexArray(eTokens) : IStrings.toIntArray(eTokens);
        idx = phraseTable.getId(new SimpleSequence<IString>(true, IStrings.toIStringArray(fIndexInts)), 
            new SimpleSequence<IString>(true, IStrings.toIStringArray(eIndexInts)));
      }

      // Lookup this rule in the phrase table
      if (idx < 0) {
        throw new RuntimeException(String.format("Phrase %d not in phrase table", reader.getLineNumber()));
      }
      if (reorderingScores.get(idx) != null) {
        throw new RuntimeException(String.format("Duplicate phrase %d in phrase table", reader.getLineNumber()));
      }
      float[] scores = IOTools.stringListToNumeric(scoreList);
      idx -= phraseTable.minRuleIndex();
      reorderingScores.set(idx, scores);
    }
    reader.close();
    
    long postTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    System.err.printf(
        "Done loading reordering table: %s (mem used: %d MiB time: %.3fs)%n",
        filename, (postTableLoadMemUsed - preTableLoadMemUsed) / (1024 * 1024),
        elapsedTime);

    return selectedFiletype;
  }
}
