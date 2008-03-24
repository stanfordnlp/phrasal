package mt;

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
	List<List<? extends ScoredFeaturizedTranslation<TK,FV>>> nbestLists();
}
