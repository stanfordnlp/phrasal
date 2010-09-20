package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public class OptionGrid<TK> {
	private final List<ConcreteTranslationOption<TK>>[] grid;
	private final int foreignSz;
	
	/**
	 * 
	 */
	@SuppressWarnings("unchecked")
	public OptionGrid(List<ConcreteTranslationOption<TK>> options, Sequence<TK> foreign) {
		foreignSz = foreign.size();
		grid = new List[foreignSz*foreignSz];
		for (int startIdx = 0; startIdx < foreignSz; startIdx++) {
			for (int endIdx = startIdx; endIdx < foreignSz; endIdx++) {
				grid[getIndex(startIdx, endIdx)] = new LinkedList<ConcreteTranslationOption<TK>>(); 
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
	 */
	public List<ConcreteTranslationOption<TK>> get(int startPos, int endPos) {
		return grid[getIndex(startPos, endPos)];
	}
	
	/**
	 * 
	 */
	private int getIndex(int startPos, int endPos) {
		return startPos*foreignSz + endPos;
	}
}
