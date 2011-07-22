package edu.stanford.nlp.mt.tools;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import java.util.Arrays;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.metrics.NISTTokenizer;
import edu.stanford.nlp.util.Pair;

public class BLEURanker {
	static List<Sequence<IString>> readCorpus(String fileName)
			throws IOException {
		
		LineNumberReader reader = new LineNumberReader(new FileReader(fileName));
		List<Sequence<IString>> hyps = new ArrayList<Sequence<IString>>();
		
		for (String line; (line = reader.readLine()) != null;) {
			line = NISTTokenizer.tokenize(line);
			line = line.replaceAll("\\s+$", "");
			line = line.replaceAll("^\\s+", "");
			Sequence<IString> translation = new RawSequence<IString>(
					IStrings.toIStringArray(line.split("\\s+")));
			hyps.add(translation);
		}
		reader.close();
		return hyps;
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err
					.println("Usage:\n\tjava ..BLEURanker ref hypSet1 hypSet2 hypSet3");
			System.exit(-1);
		}

		
		List<Sequence<IString>> referencesList = readCorpus(args[0]);
		List<Pair<Double,Pair<Sequence<IString>,Sequence<IString>>>> scoreList = 
				new ArrayList<Pair<Double,Pair<Sequence<IString>,Sequence<IString>>>>();
        System.out.println("referencesList.size: "+ referencesList.size());
		for (int i = 1; i < args.length; i++) {
        	List<Sequence<IString>> corpus = readCorpus(args[i]);
            for (int j = 0; j < corpus.size(); j++) {
				double score = BLEUMetric.computeLocalSmoothScore(corpus.get(j).toString(), 
            			Arrays.asList(new String[]{referencesList.get(j).toString()}), 4);
            	Pair<Double,Pair<Sequence<IString>,Sequence<IString>>> scoreTuple =
            			new Pair<Double,Pair<Sequence<IString>,Sequence<IString>>>(score,
            					new Pair<Sequence<IString>,Sequence<IString>>(corpus.get(j),referencesList.get(j)));
                scoreList.add(scoreTuple);
            }
        }
        Collections.sort(scoreList);
        Collections.reverse(scoreList);
        Collections.shuffle(scoreList, new Random(1));
        for (Pair<Double,Pair<Sequence<IString>,Sequence<IString>>> scoreTuple : scoreList) {
        	if (scoreTuple.second.first.size() <= 15 && scoreTuple.second.second.size() <= 15) {
        	   //System.out.printf("%s ||| %s ||| %.3f\n", scoreTuple.second.first, scoreTuple.second.second, scoreTuple.first);
        	   System.out.printf("%s ||| %s \n", scoreTuple.second.first, scoreTuple.second.second, scoreTuple.first);
        	}
        	// System.out.println(scoreTuple);
        }	
    }
}
