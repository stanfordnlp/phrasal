package mt.decoder.util;

import java.util.*;

import mt.decoder.recomb.RecombinationFilter;
import mt.decoder.recomb.RecombinationHash;
import mt.decoder.recomb.RecombinationHistory;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class SloppyBeam<S extends State<S>> implements Beam<S> {
	static public final String DEBUG_PROPERTY = "SloppyBeamDebug";
	static public final int DEBUG_LEVEL = Integer.valueOf(System.getProperty(DEBUG_PROPERTY, "0"));
	static public final int DEBUG_NONE = 0;
	static public final int DEBUG_BASIC = 1;
	static public final int DEBUG_DEEP  = 2;
	
	static public final int MOSES_SLOPPY_PRUNING_CAPACITY_MULTIPLIER = 2;
	
	private final RecombinationHash<S> recombinationHash;
	private final int capacity;
	private double worst = Double.NEGATIVE_INFINITY;
	private int recombined = 0;
	private int preinsertionDiscarded = 0;
	private int pruned = 0;
	
	private final RecombinationHistory<S> recombinationHistory;
	
	
	/**
	 * 
	 */
	public SloppyBeam(int capacity, RecombinationFilter<S> filter) {
		if (DEBUG_LEVEL >= DEBUG_BASIC) {
			System.err.printf("New Sloppy Beam - capacity: %d pruning multiplier: %d\n", capacity, MOSES_SLOPPY_PRUNING_CAPACITY_MULTIPLIER);
		}
		recombinationHash = new RecombinationHash<S>(filter);
		this.capacity = capacity;
		this.recombinationHistory = null;
	}
	
	/**
	 * 
	 */
	public SloppyBeam(int capacity, RecombinationFilter<S> filter, RecombinationHistory<S> recombinationHistory) {
		if (DEBUG_LEVEL >= DEBUG_BASIC) {
			System.err.printf("New Sloppy Beam - capacity: %d pruning multiplier: %d\n", capacity, MOSES_SLOPPY_PRUNING_CAPACITY_MULTIPLIER);
		}
		recombinationHash = new RecombinationHash<S>(filter);
		this.capacity = capacity;
		this.recombinationHistory = recombinationHistory;
	}
	
	
	@Override
	public int capacity() {
		return capacity;
	}

	@Override
	synchronized public S put(S hypothesis) {
		
		//System.out.printf("put score: %.3f (score: %.3f h: %.3f) worst: %.3f\n", hypothesis.finalScoreEstimate(), hypothesis.score, hypothesis.h, worst);
		// see if we're worse than worst
		if (hypothesis.score() < worst) {
			preinsertionDiscarded++;
			return hypothesis;
		}
		
		// recombination check	
		RecombinationHash.Status status = recombinationHash.queryStatus(hypothesis, true);
		if (recombinationHistory != null) {
			recombinationHistory.log(recombinationHash.getLastBestOnQuery(), recombinationHash.getLastRedudent());
		}
		
		if (status == RecombinationHash.Status.COMBINABLE) {
			recombined++;
			return hypothesis;
		} else if (status == RecombinationHash.Status.UPDATED) {
			recombined++;
		}
		
		// prune if necessary
		if (recombinationHash.size() >= MOSES_SLOPPY_PRUNING_CAPACITY_MULTIPLIER*capacity) {
			pruneBeam();
		}
		
		return null;
	}
	
	
	public void pruneBeam() {
		if (DEBUG_LEVEL >= DEBUG_BASIC) {
			System.err.printf("Pre-Prunning - beam size: %d capacity: %d recombination hash size: %d\n", size(), capacity, recombinationHash.size());
		}
		
		List<S> hypotheses = recombinationHash.hypotheses();
		if (hypotheses.size() == 0) return;
		
		Collections.sort(hypotheses);
		
		
		if (DEBUG_LEVEL >= DEBUG_DEEP) {
			System.err.println("Sorted Hypothesis Scores:");
			System.err.println("-------------------------");
			for (int i = 0; i < hypotheses.size(); i++) {
				System.err.printf("%d:%f\n", i, hypotheses.get(i).score());
			}
		}
		
		// update worst
		
		worst = hypotheses.get(Math.max(0, Math.min(capacity-1, hypotheses.size()-1))).score();
		
		if (DEBUG_LEVEL >= DEBUG_BASIC) {
			System.err.printf("post prune worst: %f\n", worst);
		}
		for (int i = capacity; i < hypotheses.size(); i++) {
			S hypothesis = hypotheses.get(i);
			recombinationHash.remove(hypothesis, true);
			if (recombinationHistory != null) {
				recombinationHistory.remove(hypothesis);
			}
			pruned++;
		}
		
		if (DEBUG_LEVEL >= DEBUG_BASIC) {
			System.err.printf("New beam size: %d\n", recombinationHash.size());
			System.err.printf("Post-Prunning - beam size: %d capacity: %d recombination hash size: %d\n", size(), capacity, recombinationHash.size());
		}
	}


	@Override
	public S remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return recombinationHash.size();
	}

	@Override
	public Iterator<S> iterator() {
		pruneBeam();
		List<S> hypotheses = recombinationHash.hypotheses();
		Collections.sort(hypotheses);
		
		return hypotheses.iterator();
	}

	@Override
	public double bestScore() {
		throw new UnsupportedOperationException();
	}

	@Override
	public S removeWorst() {
		throw new UnsupportedOperationException();
	}
	
	/**
	 * 
	 */
	public double worstScore() {
		return worst;
	}
	
    @Override
	public int recombined() {
		return recombined;
	}
    
    @Override
    public int preinsertionDiscarded() {
    	return preinsertionDiscarded;
    }
    
    @Override 
    public int pruned() {
    	return pruned;
    }
}
