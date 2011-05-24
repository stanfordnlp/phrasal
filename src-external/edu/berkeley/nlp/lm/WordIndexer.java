package edu.berkeley.nlp.lm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates words in the vocabulary of a language model. Stores a two-way
 * mapping between integers and words.
 * 
 * @author adampauls
 * 
 * @param <W>
 *            The type representing a word.
 */
public interface WordIndexer<W> extends Serializable
{

	/**
	 * Gets the index for a word, adding if necessary.
	 * 
	 * @param word
	 * @return
	 */
	public int getOrAddIndex(W word);

	public int getOrAddIndexFromString(String word);

	/**
	 * Should never add to vocabulary, and should return getUnkSymbol() if the
	 * word is not in the vocabulary.
	 * 
	 * @param word
	 * @return
	 */
	public int getIndexPossiblyUnk(W word);

	public W getWord(int index);

	/**
	 * Number of words that have been added so far
	 * 
	 * @return
	 */
	public int numWords();

	/**
	 * Returns the start symbol (usually something like {@literal <s>}
	 * 
	 * @return
	 */
	public W getStartSymbol();

	public void setStartSymbol(W sym);

	/**
	 * Returns the start symbol (usually something like {@literal </s>}
	 * 
	 * @return
	 */
	public W getEndSymbol();

	public void setEndSymbol(W sym);

	/**
	 * Returns the unk symbol (usually something like {@literal <unk>}
	 * 
	 * @return
	 */
	public W getUnkSymbol();

	public void setUnkSymbol(W sym);

	/**
	 * Informs the implementation that no more words can be added to the
	 * vocabulary. Implementations may perform some space optimization, and
	 * should trigger an error if an attempt is made to add a word after this
	 * point.
	 */
	public void trimAndLock();

	public static class StaticMethods
	{
		/**
		 * Converts an int representation of an n-gram to a list. Converts only
		 * the range of the array specified by [startPos,endPos)
		 * 
		 * @param <T>
		 * @param wordIndexer
		 * @param intNgram
		 * @param startPos
		 * @param endPos
		 * @return
		 */
		public static <T> List<T> toList(final WordIndexer<T> wordIndexer, final int[] intNgram, final int startPos, final int endPos) {
			final List<T> l = new ArrayList<T>(endPos - startPos);
			for (int i = startPos; i < endPos; ++i) {
				l.add(wordIndexer.getWord(intNgram[i]));
			}
			return l;
		}

		public static <T> List<T> toList(final WordIndexer<T> wordIndexer, final int[] intNgram) {
			return toList(wordIndexer, intNgram, 0, intNgram.length);
		}
	}

}
