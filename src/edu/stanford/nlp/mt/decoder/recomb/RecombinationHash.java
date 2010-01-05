package edu.stanford.nlp.mt.decoder.recomb;

import java.util.*;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.State;


/**
 * 
 * @author danielcer
 *
 * @param <S>
 */
public class RecombinationHash<S extends State<S>> {
	public static String DEBUG_OPT = "RecombinationHashDebug";
	public static boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_OPT, "false"));
	public static boolean DETAILED_DEBUG = false;
	private static int expensiveComparisons = 0;
	private static int comparisons = 0;
	private static int equalityExpensiveComparisions = 0;
	
	private final HashMap<FilterWrappedHypothesis,FilterWrappedHypothesis> recombinationHash;
	//private 
	final RecombinationFilter<S> filter;
	
	static {
		if (DEBUG) {
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					displayStats();
				}
			});
		}
	}
	  
	static private void displayStats() {
		System.err.println("RecombinationHash stats:");
		System.err.println("------------------------");
		System.err.printf("Comparisons: %d\n", comparisons);
		System.err.printf("Expensive Comparisions: %d (%f %%)\n", expensiveComparisons, (expensiveComparisons*100.0/comparisons));
		System.err.printf("Filter Equality Expensive Comparisions: %d (%f %% total) (%f %% expensive)\n", equalityExpensiveComparisions, 
				(equalityExpensiveComparisions*100.0/comparisons),
				(equalityExpensiveComparisions*100.0/expensiveComparisons));
	}
	
	/**
	 * 
	 */
	public RecombinationHash(RecombinationFilter<S> filter) {
		this.recombinationHash = new HashMap<FilterWrappedHypothesis,FilterWrappedHypothesis>();
		this.filter = filter;
	}
	
	
	public enum Status  {NOVEL, NOVEL_INSERTED, BETTER, COMBINABLE, SELF, UPDATED};
	
	public int size() {
		return recombinationHash.size();
	}
	
	/**
	 * 
	 */
	public Status queryStatus(S hypothesis) {
		return queryStatus(hypothesis, false);		
	}
	
	public boolean isBest(S hypothesis) {
		FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(hypothesis,filter);
		FilterWrappedHypothesis filterEquivWrappedHyp = recombinationHash.get(wrappedHyp);
		if (filterEquivWrappedHyp == null) return false;
		return filterEquivWrappedHyp.hypothesis == hypothesis;
	}
	
	/**
	 * 
	 */
	@SuppressWarnings("unchecked")
	public Status queryStatus(S hypothesis, boolean update) {
		if (filter instanceof NoRecombination) {
			if (update) return Status.NOVEL_INSERTED;
			return Status.NOVEL;
		}
		
		FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(hypothesis,filter);
		FilterWrappedHypothesis filterEquivWrappedHyp = recombinationHash.get(wrappedHyp);
		
		if (DETAILED_DEBUG) {
			if (filterEquivWrappedHyp != null) {
				Hypothesis h1 = (Hypothesis)hypothesis;
				Hypothesis h2 = (Hypothesis)filterEquivWrappedHyp.hypothesis;
				System.err.printf("Recombining: %d with %d scores %.3f %.3f\n", Math.min(h1.id, h2.id), Math.max(h1.id, h2.id),
						                                                        Math.min(h1.score(), h2.score()), Math.max(h1.score(), h2.score()));
			}
		}
		if (filterEquivWrappedHyp == null) {
			lastBestOnQuery = hypothesis;
			lastRedudentOnQuery = null;
			if (update) {
				recombinationHash.put(wrappedHyp,wrappedHyp);
				return Status.NOVEL_INSERTED;
			}
			return Status.NOVEL;
		}
	
		if (hypothesis == filterEquivWrappedHyp.hypothesis) {
			lastBestOnQuery = hypothesis;
			lastRedudentOnQuery = null;
			return Status.SELF;
		}
		
		/*
		System.err.printf("-----------\n");
		System.err.printf("RECOMBINING\n");
		System.err.printf("-----------\n");
		System.err.printf("Existing: %s\n", filterEquivWrappedHyp.hypothesis);
		System.err.printf("Suggested: %s\n", hypothesis);
		*/
		if (hypothesis.score() > filterEquivWrappedHyp.hypothesis.score()) {
			//System.err.println("Exisiting < Suggested\n");
			lastRedudentOnQuery = filterEquivWrappedHyp.hypothesis;
			lastBestOnQuery = hypothesis;
			if (update) {
				filterEquivWrappedHyp.hypothesis = hypothesis;
				return Status.UPDATED;
			}
			return Status.BETTER;
		} 
		
		//System.err.println("Exisiting > Suggested\n");
		lastRedudentOnQuery = hypothesis;
		lastBestOnQuery = filterEquivWrappedHyp.hypothesis;
		return Status.COMBINABLE;
	}
	
	private S lastBestOnQuery;
	private S lastRedudentOnQuery;
	
	public S getLastRedudent() {
		return lastRedudentOnQuery;
	}
	
	public S getLastBestOnQuery() {
		return lastBestOnQuery;
	}
	
	
	/**
	 * 
	 */
	public void put(S hypothesis) {
		if (filter instanceof NoRecombination) {
			return;
		}
		FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(hypothesis,filter);
		recombinationHash.put(wrappedHyp, wrappedHyp);
	}
	
	
	public void remove(S hypothesis) {
		remove(hypothesis, false);
	}
	
	public void remove(S hypothesis, boolean missingOkay) {
		if (filter instanceof NoRecombination) {
			return;
		}
		FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(hypothesis,filter);
		FilterWrappedHypothesis filterEquivWrappedHyp = recombinationHash.get(wrappedHyp);
		if (filterEquivWrappedHyp == null) {
			if (missingOkay) return;
			throw new RuntimeException("hypothesis not found in recombination hash");
		}
		if (hypothesis == filterEquivWrappedHyp.hypothesis) recombinationHash.remove(wrappedHyp);
	}
	
	public class FilterWrappedHypothesis {
		public S hypothesis;
		public final RecombinationFilter<S> filter;
		private final long hashCode;
		
		public FilterWrappedHypothesis(S hyp, RecombinationFilter<S> filter) {
			this.hypothesis = hyp;
			this.filter = filter;
			hashCode = filter.recombinationHashCode(hypothesis);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public boolean equals(Object o) {
			
			comparisons++;
			/*
			if (comparisons % 1000 == 0) {
				System.err.println("=================================");
				System.err.printf("HashEntries: %d\n",recombinationHash.size());
				displayStats();
			} */
			
			if (!(o instanceof RecombinationHash.FilterWrappedHypothesis)) {
				return false;
			}
			
			FilterWrappedHypothesis wrappedHyp = (FilterWrappedHypothesis)o;
			
			/*if ((0xFFF & wrappedHyp.longHashCode()) == (0xFFF & this.longHashCode()) && !filter.combinable(this.hypothesis, wrappedHyp.hypothesis)) {
				System.err.println();
				if (wrappedHyp.hypothesis.featurizable != null) System.err.printf("1: %s\n", wrappedHyp.hypothesis.featurizable.partialTranslation);
				if (hypothesis.featurizable != null) System.err.printf("2: %s\n", hypothesis.featurizable.partialTranslation);
				if (wrappedHyp.hypothesis.featurizable != null && hypothesis.featurizable != null) {
					System.err.printf("Noisy equals: %s\n", 
							((AbstractSequence)wrappedHyp.hypothesis.featurizable.partialTranslation).noisyEquals(hypothesis.featurizable.partialTranslation));
				}
				((TranslationIdentityRecombinationFilter)filter).noisy = true;
				System.err.printf("Same hyp: %s\n", wrappedHyp.hypothesis == this.hypothesis);
				System.err.printf("equal: %s\n", filter.combinable(this.hypothesis, wrappedHyp.hypothesis));
				System.err.printf("hashcollison %x <=> %x\n\n", wrappedHyp.longHashCode(), this.longHashCode());
				((TranslationIdentityRecombinationFilter)filter).noisy = true;
			} */
			
			if (wrappedHyp.hypothesis == this.hypothesis) {
				return true;
			}
			
			if (wrappedHyp.longHashCode() != this.longHashCode()) {
				return false;
			}
			
			boolean isCombinable = filter.combinable(this.hypothesis, wrappedHyp.hypothesis);
			
			if (DEBUG) {
				expensiveComparisons++;
				if (isCombinable) {
					equalityExpensiveComparisions++;
				}
			}
			return isCombinable;
		}
		
		
		private long longHashCode() {
			return hashCode;
		}
		
		@Override
		public int hashCode() { 
			return (int)(longHashCode()>>32);
		}
	}
	
	public List<S> hypotheses() {
		List<S> hypotheses = new ArrayList<S>(recombinationHash.size());
		for (FilterWrappedHypothesis fwh : recombinationHash.keySet()) {
			hypotheses.add(fwh.hypothesis);
		}
		return hypotheses;
	} 
	
}
