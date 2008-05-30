package mt.base;

import java.util.List;


/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public interface NBestListContainer<TK,FV> {
	/**
	 * 
	 * @return
	 */
	List<List<ScoredFeaturizedTranslation<TK,FV>>> nbestLists();
}
