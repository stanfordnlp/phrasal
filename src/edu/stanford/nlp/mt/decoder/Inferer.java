package edu.stanford.nlp.mt.decoder;

import java.util.List;

import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Interface for decoding algorithms.
 * 
 * @author danielcer
 * @author Spence Green
 */
public interface Inferer<TK, FV> {

  public static enum NbestMode {Standard, Diverse, Combined};
  
  /**
   * Produce a 1-best translation.
   * 
   * @param source
   * @param sourceInputId
   * @param sourceInputProperties
   * @param constrainedOutputSpace
   * @param targets
   * @return
   */
  public RichTranslation<TK, FV> translate(Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> constrainedOutputSpace, List<Sequence<TK>> targets);
  
  /**
   * Produce a word alignment.
   * 
   * @param source
   * @param target
   * @param sourceInputId
   * @param sourceInputProperties
   * @return
   */
  public SymmetricalWordAlignment wordAlign(Sequence<TK> source, Sequence<TK> target, int sourceInputId,
      InputProperties sourceInputProperties);

  /**
   * Produce a 1-best translation.
   * 
   * @param scorer
   * @param source
   * @param sourceInputId
   * @param sourceInputProperties
   * @param outputSpace
   * @param targets
   * @return
   */
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer, Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets);

  /**
   * Produce an n-best list of translations.
   * 
   * @param source
   * @param sourceInputId
   * @param sourceInputProperties
   * @param constrainedOutputSpace
   * @param targets
   * @param size
   * @param distinct if true then return distinct nbest items
   * @param diverse if true then return diverse nbest items
   * @return
   */
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> constrainedOutputSpace, List<Sequence<TK>> targets,
      int size, boolean distinct, NbestMode nbestMode);

  /**
   * Produce an n-best list of translations.
   * 
   * @param scorer
   * @param source
   * @param sourceInputId
   * @param sourceInputProperties
   * @param outputSpace
   * @param targets
   * @param size
   * @param distinct if true then return distinct nbest items
   * @param diverse if true then return diverse nbest items
   * @return
   */
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer, Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets,
      int size, boolean distinct, NbestMode nbestMode);
}
