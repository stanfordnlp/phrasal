package mt.decoder.inferer;

import java.util.*;

import mt.base.RichTranslation;
import mt.base.Sequence;
import mt.decoder.util.ConstrainedOutputSpace;

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

  /**
   *
   * @param foreign
   * @param size
   * @return
   */
  List<RichTranslation<TK,FV>> nbest(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int size);
  
}
