package mt.decoder.inferer;

import java.util.*;

import mt.base.RichTranslation;
import mt.base.Sequence;
import mt.decoder.util.ConstrainedOutputSpace;
import mt.decoder.util.Scorer;

/**
 *
 * @author danielcer
 */
public interface Inferer<TK,FV> {

  /**
   *
   */
  RichTranslation<TK,FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets);

  RichTranslation<TK,FV> translate(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets);
  /**
   *
   */
  List<RichTranslation<TK,FV>> nbest(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets, int size);
  
  List<RichTranslation<TK,FV>> nbest(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets, int size);
  
}
