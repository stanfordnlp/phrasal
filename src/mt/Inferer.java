package mt;

import java.util.*;

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
