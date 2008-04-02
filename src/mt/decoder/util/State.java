package mt.decoder.util;

/**
 * Marks classes that can be placed on Beam objects 
 * 
 * @author danielcer
 *
 * @param <T>
 */
public interface State<T> extends Comparable<T> {
	
	/**
	 * Compare to object o first based on the relative score assigned to this object and 
	 * object o, and then, on equality of score, compares o and the current object based 
	 * on object identity. The latter ensures that o1.compareTo(o2) is never zero unless
	 * o1 == o2.
	 * 
	 */
	public int compareTo(T o);
	
	/**
	 * Score assigned to the current state, including future cost heuristic if applicable.
	 * 
	 * @return
	 */
	public double score();
	
	/**
	 * Score assigned to the current state w.r.t the future cost heuristic
	 * 
	 * @return
	 */
	public double partialScore();
	
	/**
	 * 
	 * @return
	 */
	public State<T> parent();
	
	/**
	 * 
	 * @return
	 */
	public int depth();
}

