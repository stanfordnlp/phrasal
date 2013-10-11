package edu.stanford.nlp.mt.decoder;

import java.util.List;

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
   */
  public RichTranslation<TK, FV> translate(Sequence<TK> source, int sourceInputId,
      OutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets);

  public RichTranslation<TK, FV> translate(Scorer<FV> scorer, Sequence<TK> source,
      int sourceInputId, OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets);

  /**
   * Produce an n-best list of translations.
   */
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source, int sourceInputId,
      OutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int size);

  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer, Sequence<TK> source,
      int sourceInputId, OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets, int size);
}
