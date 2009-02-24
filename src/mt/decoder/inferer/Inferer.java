package mt.decoder.inferer;

import java.util.*;

import mt.base.RichTranslation;
import mt.base.Sequence;
import mt.decoder.util.ConstrainedOutputSpace;
import mt.decoder.util.Scorer;

/**
 *
 * @author danielcer
 *
 * @param <TK,TV>
 */
public interface Inferer<TK,FV> {

  /**
   *
   * @param foreign
   * @return
   */
  RichTranslation<TK,FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace);

  RichTranslation<TK,FV> translate(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace);
  /**
   *
   * @param foreign
   * @param size
   * @return
   */
  List<RichTranslation<TK,FV>> nbest(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int size);
  
  List<RichTranslation<TK,FV>> nbest(Scorer<FV> scorer, Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int size);
  
}
