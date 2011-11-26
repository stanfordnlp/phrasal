package edu.berkeley.nlp.lm.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.lm.WordIndexer;
import edu.berkeley.nlp.lm.util.Logger;
import edu.berkeley.nlp.lm.values.ProbBackoffPair;

/**
 * A parser for ARPA LM files.
 * 
 * @author Alex Bouchard-Cote
 * @author Adam Pauls
 */
public class ARPALmReader<W>
{

	private static final String START_SYMBOL = "<s>";

	private static final String END_SYMBOL = "</s>";

	private static final String UNK_SYMBOL = "<unk>";

	private BufferedReader reader;

	private int currentNGramLength = 1;

	int currentNGramCount = 0;

	/**
	 * The current line in the file being examined.
	 */
	private int lineNumber = 1;

	private final WordIndexer<W> wordIndexer;

	private final int maxOrder;

	private final String file;

	private LmReaderCallback<ProbBackoffPair> callback;

	/**
	 * 
	 * @throws IOException
	 */
	protected String readLine() throws IOException {
		lineNumber++;
		return reader.readLine();
	}

	/**
	 * 
	 * @param reader
	 */
	public ARPALmReader(final String file, final WordIndexer<W> wordIndexer, final int maxOrder) {
		this.file = file;
		this.wordIndexer = wordIndexer;
		this.maxOrder = maxOrder;
	}

	/**
	 * Parse the ARPA file and populate the relevant fields of the enclosing
	 * ICSILanguageModel
	 * 
	 */
	public void parse(final LmReaderCallback<ProbBackoffPair> callback_) {
		this.callback = callback_;
		this.reader = IOUtils.openInHard(file);
		Logger.startTrack("Parsing ARPA language model file");
		final List<Long> numNGrams = parseHeader();
		callback.initWithLengths(numNGrams);
		parseNGrams();
		Logger.endTrack();
		callback.cleanup();
		wordIndexer.setStartSymbol(wordIndexer.getWord(wordIndexer.getOrAddIndexFromString(START_SYMBOL)));
		wordIndexer.setEndSymbol(wordIndexer.getWord(wordIndexer.getOrAddIndexFromString(END_SYMBOL)));
		wordIndexer.setUnkSymbol(wordIndexer.getWord(wordIndexer.getOrAddIndexFromString(UNK_SYMBOL)));
	}

	/**
	 * 
	 * @throws IOException
	 * @throws ARPAParserException
	 */
	protected List<Long> parseHeader() {
		final List<Long> numEachNgrams = new ArrayList<Long>();
		try {
			while (reader.ready()) {

				final String readLine = readLine();
				final String ngramTotalPrefix = "ngram ";
				if (readLine.startsWith(ngramTotalPrefix)) {
					final int equalsIndex = readLine.indexOf('=');
					assert equalsIndex >= 0;
					final long currNumNGrams = Long.parseLong(readLine.substring(equalsIndex + 1));
					if (numEachNgrams.size() < maxOrder) numEachNgrams.add(currNumNGrams);
				}
				if (readLine.contains("\\1-grams:")) { return numEachNgrams; }
			}
		} catch (final NumberFormatException e) {
			throw new RuntimeException(e);

		} catch (final IOException e) {
			throw new RuntimeException(e);

		}
		throw new RuntimeException("\"\\1-grams: expected (line " + lineNumber + ")");
	}

	/**
		 * 
		 * 
		 */
	protected void parseNGrams() {

		int currLine = 0;
		Logger.startTrack("Reading 1-grams");
		try {
			while (reader.ready()) {
				if (currLine % 1000 == 0) Logger.logs("Read " + currLine);
				currLine++;
				final String line = reader.readLine();
				assert line != null;
				if (line.length() == 0) {
					// nothing to do (skip blank lines)
				} else if (line.charAt(0) == '\\') {
					// a new block of n-gram is beginning
					if (!line.startsWith("\\end")) {
						Logger.logs(currentNGramCount + " " + currentNGramLength + "-gram read.");
						Logger.endTrack();
						callback.handleNgramOrderFinished(currentNGramLength);
						currentNGramLength++;
						if (currentNGramLength > maxOrder) return;
						currentNGramCount = 0;
						Logger.startTrack("Reading " + currentNGramLength + "-grams");
					}
				} else {
					parseLine(line);
				}
			}
			reader.close();
		} catch (final IOException e) {
			throw new RuntimeException(e);

		}
		Logger.endTrack();
		callback.handleNgramOrderFinished(currentNGramLength);
	}

	/**
	 * 
	 * @param line
	 * @throws ARPAParserException
	 */
	protected void parseLine(final String line) {
		// this is a 2 or 3 columns n-gram entry
		final int firstTab = line.indexOf('\t');
		final int secondTab = line.indexOf('\t', firstTab + 1);
		final boolean hasBackOff = (secondTab >= 0);

		final int length = line.length();
		final String logProbString = line.substring(0, firstTab);
		final String firstWord = line.substring(firstTab + 1, secondTab < 0 ? length : secondTab);
		final int[] nGram = callback.ignoreNgrams() ? null : parseNGram(firstWord, currentNGramLength);

		// the first column contains the log pr
		final float logProbability = Float.parseFloat(logProbString);
		float backoff = 0.0f;

		// and its backoff, if specified
		if (hasBackOff) {
			backoff = Float.parseFloat(line.substring(secondTab + 1, length));
		}
		// add the new n-gram
		assert logProbability != 0;
		if (line.contains("years . \" over")) {
			@SuppressWarnings("unused")
			int x = 5;
		}
		callback.call(nGram, new ProbBackoffPair(logProbability, backoff), line);

		currentNGramCount++;
	}

	/**
	 * 
	 * @param string
	 */
	private int[] parseNGram(final String string, final int numWords) {
		final int[] retVal = new int[numWords];
		int spaceIndex = 0;
		int k = 0;
		while (true) {
			final int nextIndex = string.indexOf(' ', spaceIndex);
			final String currWord = string.substring(spaceIndex, nextIndex < 0 ? string.length() : nextIndex);
			retVal[k++] = wordIndexer.getOrAddIndexFromString(currWord);
			if (nextIndex < 0) break;
			spaceIndex = nextIndex + 1;
		}
		return retVal;
	}
}