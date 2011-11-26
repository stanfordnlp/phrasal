package edu.berkeley.nlp.lm.io;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import edu.berkeley.nlp.lm.WordIndexer;
import edu.berkeley.nlp.lm.collections.Counter;
import edu.berkeley.nlp.lm.collections.Iterators;
import edu.berkeley.nlp.lm.util.Logger;
import edu.berkeley.nlp.lm.util.WorkQueue;

/**
 * Reads in n-gram count collections in the format that the Google n-grams Web1T
 * corpus comes in.
 * 
 * @author adampauls
 * 
 */
public class GoogleLmReader<W>
{

	private static final String START_SYMBOL = "<S>";

	private static final String END_SYMBOL = "</S>";

	private static final String UNK_SYMBOL = "<UNK>";

	private final String rootDir;

	private final WordIndexer<W> wordIndexer;

	public GoogleLmReader(final String rootDir, final WordIndexer<W> wordIndexer) {
		this.rootDir = rootDir;

		this.wordIndexer = wordIndexer;

	}

	public void parse(final int numGoogleLoadThreads, final LmReaderCallback<Long> callback) {

		final List<File> listFiles = Arrays.asList(new File(rootDir).listFiles());
		Collections.sort(listFiles);
		int ngramOrder_ = 0;
		for (final File ngramDir : listFiles) {
			final File[] ngramFiles = ngramDir.listFiles(new FilenameFilter()
			{

				@Override
				public boolean accept(final File dir, final String name) {
					return name.endsWith("gz");
				}
			});
			if (ngramOrder_ == 0) {
				if (ngramFiles.length != 1) throw new RuntimeException("Expected just one file for unigrams, found " + Arrays.toString(ngramFiles));
				final Counter<String> counts = new Counter<String>();
				try {
					for (final String line : Iterators.able(IOUtils.lineIterator(ngramFiles[0].getPath()))) {
						final String[] parts = line.split("\t");
						final String word = parts[0];
						final long count = Long.parseLong(parts[1]);
						counts.setCount(word, count);//
					}
				} catch (final NumberFormatException e) {
					throw new RuntimeException(e);

				} catch (final IOException e) {
					throw new RuntimeException(e);

				}
				for (final Entry<String, Double> entry : counts.getEntriesSortedByDecreasingCount()) {
					wordIndexer.getOrAddIndexFromString(entry.getKey());
				}
			}
			Logger.startTrack("Reading ngrams of order " + (ngramOrder_ + 1));
			final WorkQueue wq = new WorkQueue(numGoogleLoadThreads, true);
			for (final File ngramFile_ : ngramFiles) {
				final File ngramFile = ngramFile_;
				final int ngramOrder = ngramOrder_;
				wq.execute(new Runnable()
				{
					@Override
					public void run() {
						if (numGoogleLoadThreads == 0) Logger.startTrack("Reading ngrams from file " + ngramFile);
						try {
							int k = 0;
							for (String line : Iterators.able(IOUtils.lineIterator(ngramFile.getPath()))) {
								if (numGoogleLoadThreads == 0) if (k % 1000 == 0) Logger.logs("Line " + k);
								k++;
								line = line.trim();
								parseLine(line);
							}
						} catch (final NumberFormatException e) {
							throw new RuntimeException(e);

						} catch (final IOException e) {
							throw new RuntimeException(e);

						}
						if (numGoogleLoadThreads == 0)
							Logger.endTrack();
						else {
							Logger.logss("Finished file " + ngramFile);

						}
					}

					/**
					 * @param callback
					 * @param ngramOrder
					 * @param line
					 */
					private void parseLine(final String line) {
						final int tabIndex = line.indexOf('\t');

						int spaceIndex = 0;
						final int[] ngram = new int[ngramOrder + 1];
						final String words = line.substring(0, tabIndex);
						int i = ngram.length - 1;
						while (true) {
							int nextIndex = line.indexOf(' ', spaceIndex);
							if (nextIndex < 0) nextIndex = words.length();
							final String word = words.substring(spaceIndex, nextIndex);
							ngram[i] = wordIndexer.getOrAddIndexFromString(word);
							--i;
							if (nextIndex == words.length()) break;
							spaceIndex = nextIndex + 1;
						}
						final long count = Long.parseLong(line.substring(tabIndex + 1));
						callback.call(ngram, count, words);
					}
				});
			}
			wq.finishWork();
			Logger.endTrack();
			callback.handleNgramOrderFinished(++ngramOrder_);

		}
		callback.cleanup();
		wordIndexer.setStartSymbol(wordIndexer.getWord(wordIndexer.getOrAddIndexFromString(START_SYMBOL)));
		wordIndexer.setEndSymbol(wordIndexer.getWord(wordIndexer.getOrAddIndexFromString(END_SYMBOL)));
		wordIndexer.setUnkSymbol(wordIndexer.getWord(wordIndexer.getOrAddIndexFromString(UNK_SYMBOL)));

	}

}
