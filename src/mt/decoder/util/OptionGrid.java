package mt.decoder.util;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.Sequence;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public class OptionGrid<TK> {
	@SuppressWarnings("unchecked")
	private List[] grid;
	private int foreignSz;
	
	/**
	 * 
	 * @param options
	 * @param foreign
	 */
	@SuppressWarnings("unchecked")
	public OptionGrid(List<ConcreteTranslationOption<TK>> options, Sequence<TK> foreign) {
		foreignSz = foreign.size();
		grid = new List[foreignSz*foreignSz];
		for (int startIdx = 0; startIdx < foreignSz; startIdx++) {
			for (int endIdx = startIdx; endIdx < foreignSz; endIdx++) {
				grid[getIndex(startIdx, endIdx)] = new LinkedList(); 
			}
		}
		for (ConcreteTranslationOption<TK> opt : options) {
			int startPos = opt.foreignPos;
			int endPos = opt.foreignCoverage.nextClearBit(opt.foreignPos) - 1;
			grid[getIndex(startPos, endPos)].add(opt);
		}
	}
	
	/**
	 * 
	 * @param startPos
	 * @param endPos
	 */
	@SuppressWarnings("unchecked")
	public List<ConcreteTranslationOption<TK>> get(int startPos, int endPos) {
		return grid[getIndex(startPos, endPos)];
	}
	
	/**
	 * 
	 * @param startPos
	 * @param endPos
	 */
	private int getIndex(int startPos, int endPos) {
		return startPos*foreignSz + endPos;
	}
}
