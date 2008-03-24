package mt;

import java.util.*;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class CombinedRecombinationFilter<S> implements
		RecombinationFilter<S> {
	public static enum CombinationRule { AND, OR }
	public static final CombinationRule DEFAULT_COMBINATION_RULE = CombinationRule.AND;
	
	final RecombinationFilter<S>[] filters;
	final CombinationRule combinationRule;
	
	/**
	 * 
	 * @param filters
	 */
	public CombinedRecombinationFilter(RecombinationFilter<S>... filters) {
		this.filters = Arrays.copyOf(filters, filters.length);
		combinationRule = DEFAULT_COMBINATION_RULE;
	}
	
	/**
	 * 
	 * @param filters
	 */
	@SuppressWarnings("unchecked")
	public CombinedRecombinationFilter(List<RecombinationFilter<S>> filters) {
		this.filters = (RecombinationFilter<S>[])filters.toArray(new RecombinationFilter[0]);
		combinationRule = DEFAULT_COMBINATION_RULE;
	}
	
	
	/**
	 * 
	 * @param combinationRule
	 * @param filters
	 */
	public CombinedRecombinationFilter(CombinationRule combinationRule, RecombinationFilter<S>... filters) {
		this.filters = Arrays.copyOf(filters, filters.length);
		this.combinationRule = combinationRule;
	}
	
	/**
	 * 
	 * @param filters
	 */
	@SuppressWarnings("unchecked")
	public CombinedRecombinationFilter(CombinationRule combinationRule, List<RecombinationFilter<S>> filters) {
		this.filters = (RecombinationFilter<S>[])filters.toArray();
		this.combinationRule = combinationRule;
	}
	
	@Override
	public boolean combinable(S hypA, S hypB) {
		switch(combinationRule) {
		case AND:
			for (RecombinationFilter<S> filter : filters) {
				if (!filter.combinable(hypA, hypB)) return false;
			}
			return true;
		case OR:
			for (RecombinationFilter<S> filter : filters) {
				if (filter.combinable(hypA, hypB)) return true;
			}
			return false;
		default:
			throw new RuntimeException(String.format("Unsupported combination rule: %s", combinationRule));
		}
	}

	@Override
	public long recombinationHashCode(S hyp) {
		long hashCode = 0;
		long multiplier = 0x5DEECE66DL;
		for (RecombinationFilter<S> filter : filters) {
			long localHashCode = filter.recombinationHashCode(hyp);
			hashCode = multiplier*hashCode + 0xBL;
			hashCode += multiplier*localHashCode + 0xBL;
		}
		return hashCode;
	}
}
