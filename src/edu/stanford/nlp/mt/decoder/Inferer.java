package edu.stanford.nlp.mt.decoder;

import java.util.List;

import edu.stanford.nlp.mt.base.InputProperties;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * Interface for decoding algorithms.
 * 
 * @author danielcer
 * @author Spence Green
 */
public interface Inferer<TK, FV> {

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
   * @return
   */
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> constrainedOutputSpace, List<Sequence<TK>> targets, int size);

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
   * @return
   */
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer, Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets, int size);
}
