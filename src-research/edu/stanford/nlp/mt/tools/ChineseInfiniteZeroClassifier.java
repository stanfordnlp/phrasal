/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.io.NumberRangesFileFilter;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabeledWord;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.Dependency;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.PennTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.*;

/**
 *
 * @author robvoigt
 */

public class ChineseInfiniteZeroClassifier {

	public static boolean PRINT = false;
	public static double GLOBAL_THRESHOLD = 0.80;
	public static Map<String,Integer> featureFireCounts = new HashMap<String, Integer>(); 
	public static Map<String,Map<String,Integer>> freeVerbCounts = new HashMap<String,Map<String,Integer>>();
	public static Map<String,Map<String,Integer>> freeVerbDeps = new HashMap<String,Map<String,Integer>>();

	
	protected static boolean rightIsWrong(String[] pos_split, int i) {
		return false;
	}

	protected static Set<Integer> findNoSubjVVs (String[] pos_split, GrammaticalStructure deps) {
		Set<Integer> noSubjVVs = new HashSet<Integer>();
		for (int i = 0; i < pos_split.length; i++) {
			String pos = pos_split[i].split("#")[1];
			boolean add = false;
			if (pos.startsWith("V")) {
				boolean hasNsubj = false;
				for (TypedDependency dep : deps.allTypedDependencies()) {
					if (dep.gov().index()-1 == i && dep.reln().getShortName().equals("nsubj")) {
						if (pos_split[dep.dep().index()-1].split("#")[1].equals("PN") || pos_split[dep.dep().index()-1].split("#")[1].equals("NR")) { // makes it only PN and NR
							hasNsubj = true;
						}
					}
				}
				if (hasNsubj == false) { add = true; }
			}
			if (add) { noSubjVVs.add(i); }
		}
		return noSubjVVs;
	}

	protected static boolean validateSplits(List<String> split, String[] pos_split) {
		boolean isSame = true;
		try {
			for (int i = 0; i < split.size(); i++) {
				if (!split.get(i).equals(pos_split[i].split("#")[0])) {
					System.out.println("FAILED ON: ");
					System.out.println(split.get(i));
					System.out.println(pos_split[i].split("#")[0]);
					isSame = false;
				}
			}
		} catch (Exception e) {
			System.out.println("FAILED ON: ");
			e.printStackTrace();
			isSame = false;
		}
		return isSame;
	}
	protected static Datum<String, String> readFeatures(List<String> split, String[] pos_split, GrammaticalStructure deps, Set<Integer> noSubjVVs, int i, String label) {
		List<String> features = new ArrayList<String>();

		// what do i run into first walking left 5 steps, a verb or a PN/NR? if PN/NR, almost certainly NOT a zero, if verb, possible
		boolean left_walk = false;
		int num_walked = 0;
		boolean SOS = false;
		for (int j = i; j >= 0; j--) {
			num_walked++;
			if (pos_split[j].split("#")[1].equals("PN") || pos_split[j].split("#")[1].equals("NR") || pos_split[j].split("#")[1].equals("NN")) {
				left_walk = true;
				break;
			}
			if (pos_split[j].split("#")[1].startsWith("V") || split.get(j).equals(",") || split.get(j).equals(":") || split.get(j).equals("\"")) {
				break;
			}
			if (num_walked == 5) { 
				//left_walk = false;
				break; 
			}
			if (j == 0) { SOS = true; }
		}
		
		if (!SOS) { features.add("left_walk="+String.valueOf(left_walk)); }
		featureFireCounts.put("for_"+label+"_leftwalk_is_"+String.valueOf(left_walk), featureFireCounts.get("for_"+label+"_leftwalk_is_"+String.valueOf(left_walk))+1);
		

		// sentence-wide free verbs exist feature
		boolean sent_free;
		if (noSubjVVs.size() > 1) { sent_free=true; }
		else { sent_free=false; }
		features.add("sent_free="+String.valueOf(sent_free));
		featureFireCounts.put("for_"+label+"_sentfree_is_"+String.valueOf(sent_free), featureFireCounts.get("for_"+label+"_sentfree_is_"+String.valueOf(sent_free))+1);
		
		
		// free verb feature
		// logic: look at the first verb to the right of this gap, if it has an nsubj relation, false, if it doesn't, then it's a 'free verb' and return true
		boolean free_verb = false;
		String thisVerb = "";
		int verb_index = -1;
		for (int j = i; j < split.size(); j++) {    
			if (pos_split[j].split("#")[1].startsWith("V")) {
				if (noSubjVVs.contains(j)) { 
					free_verb = true;
					thisVerb = split.get(j);
				}
				verb_index = j;
				break;
			}
		}
		
		if (verb_index != -1) {
			freeVerbDeps.get(label+"_GOV").put("VERBCOUNT", freeVerbDeps.get(label+"_GOV").get("VERBCOUNT") + 1);
			// for counting what kind of dependnecies are occurring on the verbs to the right of is-zero examples
			for (TypedDependency dep : deps.allTypedDependencies()) {
				if (dep.gov().index()-1 == verb_index) {
					if (freeVerbDeps.get(label+"_GOV").containsKey(dep.reln().getShortName())) {
						freeVerbDeps.get(label+"_GOV").put(dep.reln().getShortName(), freeVerbDeps.get(label+"_GOV").get(dep.reln().getShortName())+1);
					} else {
						freeVerbDeps.get(label+"_GOV").put(dep.reln().getShortName(), 1);
					}
				}
				if (dep.dep().index()-1 == verb_index) {
					/*
					if (dep.reln().getShortName().equals("nn")) {
						System.out.println("for the verb" + thisVerb + " we have an nn dependency");
						for (String word: split) {
							System.out.print(word);
						}
						System.out.println();
						for (TypedDependency dep2 : deps.allTypedDependencies()) {
							System.out.println(dep2.toString());
						}
						
					}
					*/
					if (freeVerbDeps.get(label+"_DEP").containsKey(dep.reln().getShortName())) {
						freeVerbDeps.get(label+"_DEP").put(dep.reln().getShortName(), freeVerbDeps.get(label+"_DEP").get(dep.reln().getShortName())+1);
					} else {
						freeVerbDeps.get(label+"_DEP").put(dep.reln().getShortName(), 1);
					}
				}
			}
		}
		
		
		features.add("free_verb="+String.valueOf(free_verb));
		featureFireCounts.put("for_"+label+"_freeverb_is_"+String.valueOf(free_verb), featureFireCounts.get("for_"+label+"_freeverb_is_"+String.valueOf(free_verb))+1);
		if (free_verb) {
			if (freeVerbCounts.get(label).containsKey(thisVerb)) {
				freeVerbCounts.get(label).put(thisVerb, freeVerbCounts.get(label).get(thisVerb)+1);
			} else {
				freeVerbCounts.get(label).put(thisVerb, 1);
			}
		}
		
		if (free_verb) {
			features.add("free_verb_token="+thisVerb);
		}

		// left POSs
		String left_pos;
		if (i > 0) {  
			left_pos = pos_split[i-1].substring(pos_split[i-1].lastIndexOf("#") + 1);
			if (!(left_pos.equals("VV") || left_pos.equals("AD") || left_pos.equals("PU"))) {
				//left_pos = "OTHER";
			}
		} else { left_pos = "SOS"; } 
		//if (!left_pos.equals("SOS")) { features.add("left_POS="+left_pos); }
		
		
		String twoleft_pos;
		if (i - 1 > 0) {  
			twoleft_pos = pos_split[i-2].substring(pos_split[i-2].lastIndexOf("#") + 1);
			if (!(left_pos.equals("VV") || left_pos.equals("AD") || left_pos.equals("PU"))) {
				//twoleft_pos = "OTHER";
			}
		} else { twoleft_pos = "SOS"; } 
		
		// right POSs
		String right_pos;
		if (i < pos_split.length) {
			right_pos = pos_split[i].substring(pos_split[i].lastIndexOf("#")+1);
			if (!(right_pos.equals("VV") || right_pos.equals("AD"))) {
				//right_pos = "OTHER";
			}
		} else { right_pos = "EOS"; }
		//if(!right_pos.equals("EOS")) { features.add("right_POS="+right_pos); }

		String tworight_pos;
		if (i + 1 < pos_split.length) {
			tworight_pos = pos_split[i+1].substring(pos_split[i+1].lastIndexOf("#")+1);
			if (!(right_pos.equals("VV") || right_pos.equals("AD"))) {
				//tworight_pos = "OTHER";
			}
		} else { tworight_pos = "EOS"; }

		// left POS and right POS
		//features.add("l_and_r_POS="+left_pos+"+"+right_pos);

		
		// left and right POS bigrams
		if (!(left_pos.equals("SOS") && twoleft_pos.equals("SOS"))) { features.add("l_POS_bigram="+twoleft_pos+"+"+left_pos); }
		if (!(right_pos.equals("EOS") && tworight_pos.equals("EOS"))) { features.add("r_POS_bigram="+right_pos+"+"+tworight_pos); }
		
		
		// left word
		String left_word;
		if (i > 0) {  left_word = split.get(i-1); }
		else { left_word = "SOS"; }
		features.add("left_word="+left_word);

		// right word
		String right_word;    
		if (i == split.size()) { right_word="EOS"; }
		else { right_word = split.get(i); }
		features.add("right_word="+right_word);
		
		// left word and right word
		features.add("l_and_r_word="+left_word+"+"+right_word);
		
		// left bigram
		if (i > 1) { features.add("left_bigram=" + split.get(i-2) + "+" + split.get(i-1)); }
		else if (i > 0) { features.add("left_bigram=SOS+" + split.get(i-1)); }
		// else { features.add("left_bigram=SOS+SOS"); } // comment this out because it double-weights SOS for inserting a zero generally

		// right bigram
		if (i == split.size()) { // this is just EOS EOS, already captured elsewhere
		}
		else if (i == split.size() - 1) { features.add("right_bigram=" + split.get(i) + "+EOS"); }
		else { features.add("right_bigram=" + split.get(i) + "+" + split.get(i+1)); }
		
		if (PRINT) { 
			System.out.println("************"); 
			System.out.println(label);
			System.out.println(i);
			for (String feat : features) {
				System.out.println("Added: "+feat);
			}
		}

		// define the label for this example
		Datum<String,String> thisDatum = new BasicDatum<String, String>(features, label);
		return thisDatum;
	}

	public static void main(String[] args) throws Exception {
		// set up feature counter
		List<String> featureList = new ArrayList<String>();
		featureList.add("leftwalk");
		featureList.add("sentfree");
		featureList.add("freeverb");
		List<String> truthList = new ArrayList<String>();
		truthList.add("true");
		truthList.add("false");
		List<String> labelList = new ArrayList<String>();
		labelList.add("IS_ZERO");
		labelList.add("NOT_ZERO");
		for (String l: labelList) {
			for (String f: featureList) {
				for (String t: truthList) {
					featureFireCounts.put("for_"+l+"_"+f+"_is_"+t, 0);
				}
			}
		}
		
		// set up verb counters
		freeVerbCounts.put("IS_ZERO", new HashMap<String, Integer>());
		freeVerbCounts.put("NOT_ZERO", new HashMap<String, Integer>());
		freeVerbDeps.put("IS_ZERO_DEP", new HashMap<String, Integer>());
		freeVerbDeps.put("IS_ZERO_GOV", new HashMap<String, Integer>());
		freeVerbDeps.put("NOT_ZERO_DEP", new HashMap<String, Integer>());
		freeVerbDeps.put("NOT_ZERO_GOV", new HashMap<String, Integer>());
		freeVerbDeps.get("IS_ZERO_GOV").put("VERBCOUNT", 0);
		freeVerbDeps.get("NOT_ZERO_GOV").put("VERBCOUNT", 0);

		
		
		Map<Datum<String,String>, String> datumMap = new HashMap<Datum<String,String>,String>(); 
		Properties props = StringUtils.argsToProperties(args);
		String trainFile = props.getProperty("trainFile");
		String trainPOS = props.getProperty("trainPOS");
		String trainDeps = props.getProperty("trainDeps");
		String negFile = props.getProperty("negFile");
		String negPOS = props.getProperty("negPOS");
		String negDeps = props.getProperty("negDeps");
		String mode = props.getProperty("m", "test");
		String testFile = null,testPOS = null,testDeps = null,inFile = null,inPOS = null,inDeps = null,outFile = null;
		//Double writeThresh = Double.parseDouble(props.getProperty("threshold"));
		if (mode.equals("test")) {
			testFile = props.getProperty("testFile");
			testPOS = props.getProperty("testPOS");
			testDeps = props.getProperty("testDeps");
		} else {
			inFile = props.getProperty("inFile");
			inPOS = props.getProperty("inPOS");
			inDeps = props.getProperty("inDeps");
			outFile = props.getProperty("outFile");
		}

		//arg setup: positiveTrainingData testData
		// read training zeroes
		FileInputStream fstream = new FileInputStream(trainFile);
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader train_br = new BufferedReader(new InputStreamReader(in));
		String strLine;

		// read training POS tags
		FileInputStream pos_fstream = new FileInputStream(trainPOS);
		DataInputStream pos_in = new DataInputStream(pos_fstream);
		BufferedReader pos_br = new BufferedReader(new InputStreamReader(pos_in));
		String posLine;

		// read training dependency parse
		TreebankLangParserParams tlpp = new ChineseTreebankParserParams();
		tlpp.setInputEncoding("UTF-8");
		Iterable<GrammaticalStructure> gsBank = tlpp.readGrammaticalStructureFromFile(trainDeps);
		Iterator<GrammaticalStructure> gsIterator = gsBank.iterator();

		List<Datum<String,String>> trainingData = new ArrayList<Datum<String,String>>();
		List<Datum<String,String>> negTrainingData = new ArrayList<Datum<String,String>>();


		int posExamples = 0;
		int negExamples = 0;
		// for each training line
		int lineNumber = 1;
		while ((strLine = train_br.readLine()) != null) {
			posLine = pos_br.readLine();
			GrammaticalStructure deps = gsIterator.next();
			if (PRINT) {
				System.out.print("LINE NUM: ");
				System.out.println(lineNumber++);
				System.out.println(strLine);
				System.out.println(posLine);
				for (TypedDependency dep : deps.allTypedDependencies()) {
					System.out.println(dep.toString());
				}
			}

			String[] full_split = strLine.split(" ");
			String[] pos_split = posLine.split(" ");
			List<String> split = new ArrayList<String>(); // build up the split without any of the inserted zeroes, but record them
			Set<Integer> zero_locs = new HashSet<Integer>(); // set of indices of zero tokens
			for (int index = 0; index < full_split.length; index++) { 
				if (!full_split[index].matches("\\*[^ ]{1,4}\\*") && !full_split[index].contains("#Z")) { split.add(full_split[index]); }
				else { zero_locs.add(index - zero_locs.size()); }
			}
			
			if (split.size() < 4) { continue; }

			// loop through the sentence to find all VVs without nsubj
			Set<Integer> noSubjVVs = findNoSubjVVs(pos_split, deps);

			if (!validateSplits(split, pos_split)) { 
				continue; 
			}

			for (int i = 0; i < split.size(); i++) {
				// note: this doesn't look at EOS tokens, but there won't be subjects there anyway
				String pos = pos_split[i].split("#")[1];
				//if (pos.equals("VV") || pos.equals("AD") || split.get(i).equals("自己")) { // only consider spots where the POS to the right is a verb or adverb, or a ziji
				if (zero_locs.contains(i)) {
					trainingData.add(readFeatures(split,pos_split,deps,noSubjVVs, i,"IS_ZERO"));
					posExamples++;
				}
				else {
					//negative example
					continue; // for the moment, just trying negative examples from non-HIZ sentences
					//negTrainingData.add(readFeatures(split, pos_split,deps,noSubjVVs, i, "NOT_ZERO"));
				}
				//}
			}
		}     

		// read negative examples
		fstream = new FileInputStream(negFile);
		in = new DataInputStream(fstream);
		train_br = new BufferedReader(new InputStreamReader(in));

		// read training POS tags
		pos_fstream = new FileInputStream(negPOS);
		pos_in = new DataInputStream(pos_fstream);
		pos_br = new BufferedReader(new InputStreamReader(pos_in));
		
		// read training dependency parse
		gsBank = tlpp.readGrammaticalStructureFromFile(negDeps);
		gsIterator = gsBank.iterator();
		
		while ((strLine = train_br.readLine()) != null) {
			posLine = pos_br.readLine();
			GrammaticalStructure deps = gsIterator.next();
			String[] pos_split = posLine.split(" ");
			List<String> split = new ArrayList<String>();
			String[] full_split = strLine.split(" ");
			for (int index = 0; index < full_split.length; index++) {
				split.add(full_split[index]);
			}
			if (split.size() < 4) { continue; }
			Set<Integer> noSubjVVs = findNoSubjVVs(pos_split, deps);
			
			if (!validateSplits(split, pos_split)) { 
				continue; 
			}
			
			for (int i = 0; i < split.size(); i++) {
				//if (pos_split[i].split("#")[1].startsWith("V") || pos_split[i].split("#")[1].equals("AD")) { // only read negative examples that are next to VVs and ADs
					negTrainingData.add(readFeatures(split, pos_split,deps,noSubjVVs, i, "NOT_ZERO"));
				//}
			}
		}
		
		/* code to train on every negative training example
		boolean full = false;
		for (String arg : args) {
			if (arg.equals("full_data")) {
				full = true;
			}
		}
		*/
		
		// randomly select elements to make an equal-sized amount of positive and negative training data
		Collections.shuffle(negTrainingData);
		int size = trainingData.size(); // for 50-50 neg/pos split find how many positive examples we have now
		//if (full) { size = negTrainingData.size(); }
		for (int i = 0 ; i < size; i++) {
			trainingData.add(negTrainingData.get(i));
			negExamples++;
		}


		System.out.println("We had " + posExamples + " positive and " + negExamples + " negative training examples.");

		for (String key : featureFireCounts.keySet()) {
			System.out.println(key +"\t" + String.valueOf(featureFireCounts.get(key)));
		}
		
		System.out.println("PRINTING FREEVERBCOUNTS");
		for (String label : freeVerbCounts.keySet()) {
			for (String verb : freeVerbCounts.get(label).keySet()) {
				System.out.println("FVC:" + label+"\t"+verb+"\t"+freeVerbCounts.get(label).get(verb));
			}
		}
		
		System.out.println("PRINTING FREEVERBDEPS");
		for (String label : freeVerbDeps.keySet()) {
			for (String verb : freeVerbDeps.get(label).keySet()) {
				System.out.println("FVD:" +label+"\t"+verb+"\t"+freeVerbDeps.get(label).get(verb));
			}
		}
		
		// train the classifier
		LinearClassifierFactory<String,String> factory = new LinearClassifierFactory<String,String>();
		factory.useConjugateGradientAscent();
		// Turn on per-iteration convergence updates
		factory.setVerbose(true);
		//Small amount of smoothing
		factory.setSigma(10.0);
		// Build a classifier
		final LinearClassifier<String,String> classifier = factory.trainClassifier(trainingData);
		//System.out.println("classifier outputs");
		//classifier.dump();

		System.out.println("classifier trained");
		
		if (mode.equals("test")) {
			System.out.println("test mode");
			for (double THRESHOLD = 0.5; THRESHOLD <= 1.0; THRESHOLD+=0.025) { // wrap the whole thing, try various thresholds
				//double THRESHOLD = 0.80;
				// read test data
				fstream = new FileInputStream(testFile);
				in = new DataInputStream(fstream);
				BufferedReader test_br = new BufferedReader(new InputStreamReader(in));
				List<List<Datum<String,String>>> testData = new ArrayList<List<Datum<String,String>>>();

				// read test POS tags
				pos_fstream = new FileInputStream(testPOS);
				pos_in = new DataInputStream(pos_fstream);
				pos_br = new BufferedReader(new InputStreamReader(pos_in));

				// read test dependency parses
				gsBank = tlpp.readGrammaticalStructureFromFile(testDeps);
				gsIterator = gsBank.iterator();

				int posTestExamples = 0;
				int negTestExamples = 0;



				while ((strLine = test_br.readLine()) != null) {
					List<Datum<String,String>> thisSentence = new ArrayList<Datum<String,String>>(); // only pick two max per sentence

					posLine = pos_br.readLine();
					GrammaticalStructure deps = gsIterator.next();

					String[] full_split = strLine.split(" ");
					String[] pos_split = posLine.split(" ");

					List<String> split = new ArrayList<String>(); // build up the split without any of the inserted zeroes, but record them
					Set<Integer> zero_locs = new HashSet<Integer>(); // set of indices of zero tokens
					for (int index = 0; index < full_split.length; index++) { 
						if (!full_split[index].matches("\\*[^ ]{1,4}\\*") && !full_split[index].contains("#Z")) { split.add(full_split[index]); }
						else { zero_locs.add(index - zero_locs.size()); }
					}

					Set<Integer> noSubjVVs = findNoSubjVVs(pos_split, deps);

					if (!validateSplits(split, pos_split)) { 
						//System.err.println("splits didn't validate");
						continue; 
					}

					for (int i = 0; i < split.size(); i++) {
						//if (!(pos_split[i].split("#")[1].startsWith("V") || pos_split[i].split("#")[1].equals("AD"))) { continue; }
						if (zero_locs.contains(i)) {
							Datum<String,String> thisDatum = readFeatures(split, pos_split, deps, noSubjVVs, i, "IS_ZERO");
							datumMap.put(thisDatum, strLine);
							thisSentence.add(thisDatum);
							posTestExamples++;
						}
						else {
							//negative example
							Datum<String,String> thisDatum = readFeatures(split, pos_split, deps, noSubjVVs, i, "NOT_ZERO");
							datumMap.put(thisDatum, strLine);
							thisSentence.add(thisDatum);
							negTestExamples++;
						}
					}
					testData.add(thisSentence);
				}     

				System.out.println("We had " + (posTestExamples + negTestExamples) + " total test gaps, with " + posTestExamples + " real ZAs.");

				// Check out the learned weights
				//classifier.dump();
				// Test the classifier
				System.out.println("****TESTING****");

				// stats for identification only
				int truePositive = 0;
				int falsePositive = 0;
				int trueNegative = 0;
				int falseNegative = 0;
				
				for (List<Datum<String,String>> sentence : testData) {
					PriorityQueue<Datum<String,String>> pq = new PriorityQueue<Datum<String,String>>(sentence.size(),
              (d1, d2) -> {
                if (classifier.probabilityOf(d1).getCount("IS_ZERO") > classifier.probabilityOf(d2).getCount("IS_ZERO")) {
                  return -1;
                } else { return 1; }
              }
          );
					for (Datum<String, String> test: sentence) {
						pq.add(test);
					}
					int popped = 0;
					System.out.println("FOR THIS SENTENCE TOP SCORES WERE, in order:");
					while (popped < 2) {
						Datum<String,String> test = pq.poll();
						if (test == null) { break; }
						popped++; 
						String testLabel = test.label();
						double score = classifier.probabilityOf(test).getCount("IS_ZERO");
						System.out.println(String.valueOf(score));
						
						String classLabel;
						if (score > THRESHOLD) { classLabel = "IS_ZERO"; }
						else { classLabel = "NOT_ZERO"; }

						// heuristics for not picking zeroes
						/*
						boolean not = true;
						for (String feature : test.asFeatures()) {
							if (feature.equals("right_POS=VV") || feature.equals("right_POS=AD") || feature.equals("right_word=自己")) {
								not = false;
							}
						}
						if (not) { classLabel = "NOT_ZERO"; }
						*/
						
						// print justifications
						if (!(testLabel.equals("NOT_ZERO") && classLabel.equals("NOT_ZERO"))) {
							System.err.println("Marked " + testLabel + " got classified as " + classLabel);
							System.err.println(datumMap.get(test));
							classifier.justificationOf(test);
						}

						// tally prec/recall stats
						if (testLabel.equals("NOT_ZERO")) {
							if (classLabel.equals("NOT_ZERO")) {
								trueNegative++;
							} else {
								falsePositive++;
							}
						} else {
							if (classLabel.equals("NOT_ZERO")) {
								falseNegative++;
							} else {
								truePositive++;
							}
						}
					}
					while (!pq.isEmpty()) {
						Datum<String,String> test = pq.poll();
						//popped++; 
						String testLabel = test.label();
						String classLabel = "NOT_ZERO";

						// heuristics for not picking zeroes
						/*
						boolean not = true;
						for (String feature : test.asFeatures()) {
							if (feature.equals("right_POS=VV") || feature.equals("right_POS=AD") || feature.equals("right_word=自己")) {
								not = false;
							}
						}
						if (not) { classLabel = "NOT_ZERO"; }
						*/
						
						// print justifications
						if (!(testLabel.equals("NOT_ZERO") && classLabel.equals("NOT_ZERO"))) {
							System.err.println("Marked " + testLabel + " got classified as " + classLabel);
							System.err.println(datumMap.get(test));
							classifier.justificationOf(test);
						}

						// tally prec/recall stats
						if (testLabel.equals("NOT_ZERO")) {
							if (classLabel.equals("NOT_ZERO")) {
								trueNegative++;
							} else {
								falsePositive++;
							}
						} else {
							if (classLabel.equals("NOT_ZERO")) {
								falseNegative++;
							} else {
								truePositive++;
							}
						}
					}
				}
				double precision = (double)truePositive/((double)truePositive + (double)falsePositive);
				double recall = (double)truePositive/((double)truePositive + (double)falseNegative);
				double F1 = (2.0*precision*recall) / (precision+recall);
				System.out.println("Classified " + testData.size() + " data.");
				System.out.println("In the test data we had " + (truePositive+falseNegative) + " true zero examples and " + (falsePositive+trueNegative) + " true non-zero examples.");
				System.out.print("For a threshold of " + THRESHOLD + "\tPrecision: " + precision + "\tRecall: " + recall + "\tF1: " + F1);
				System.out.println("\t and we guessed "+ String.valueOf(falsePositive+truePositive) +" times");
			} // threshold wrap end
		}
		else if (mode.equals("write")) {
			System.out.println("write mode");
			// read file
			fstream = new FileInputStream(inFile);
			in = new DataInputStream(fstream);
			
			List<Datum<String,String>> testData = new ArrayList<Datum<String,String>>();

			// read POS tags
			pos_fstream = new FileInputStream(inPOS);
			pos_in = new DataInputStream(pos_fstream);
			

			// read test dependency parses
			gsBank = tlpp.readGrammaticalStructureFromFile(inDeps);
			
			
			pos_br = new BufferedReader(new InputStreamReader(pos_in));
			BufferedReader test_br = new BufferedReader(new InputStreamReader(in));
			gsIterator = gsBank.iterator();
			
			
			FileWriter out_fstream1 = new FileWriter(outFile+".85");
			BufferedWriter out1 = new BufferedWriter(out_fstream1);
			FileWriter out_fstream2 = new FileWriter(outFile+".90");
			BufferedWriter out2 = new BufferedWriter(out_fstream2);
			FileWriter out_fstream3 = new FileWriter(outFile+".95");
			BufferedWriter out3 = new BufferedWriter(out_fstream3);
			while ((strLine = test_br.readLine()) != null) {
				posLine = pos_br.readLine();
				GrammaticalStructure deps = gsIterator.next();

				String[] full_split = strLine.split(" ");
				String[] pos_split = posLine.split(" ");

				List<String> split = new ArrayList<String>(); // build up the split without any of the inserted zeroes, but record them
				Set<Integer> zero_locs = new HashSet<Integer>(); // set of indices of zero tokens
				for (int index = 0; index < full_split.length; index++) { 
					if (!full_split[index].matches("\\*[^ ]{1,4}\\*") && !full_split[index].contains("#Z")) { split.add(full_split[index]); }
					else { zero_locs.add(index - zero_locs.size()); }
				}

				Set<Integer> noSubjVVs = findNoSubjVVs(pos_split, deps);

				if (!validateSplits(split, pos_split)) { 
					out1.write(strLine + "\n");
					out2.write(strLine + "\n");
					out3.write(strLine + "\n");
					continue; 
				}

				List<Double> sentence = new ArrayList<Double>();
				for (int i = 0; i < split.size(); i++) {
					try {
						sentence.add(classifier.probabilityOf(readFeatures(split, pos_split, deps, noSubjVVs, i, "NOT_ZERO")).getCount("IS_ZERO"));
					} catch (Exception e) {
						sentence.add(0.0);
					}
				}
				Collections.sort(sentence);
				if (split.size() > 4) { System.out.println("top two probs in this sentence: " + String.valueOf(sentence.get(sentence.size()-1)) + " and " + String.valueOf(sentence.get(sentence.size()-2))); }
				for (int i = 0; i < split.size(); i++) {
					String cur_class;
					Double prob;
					try {
						prob = classifier.probabilityOf(readFeatures(split, pos_split, deps, noSubjVVs, i, "NOT_ZERO")).getCount("IS_ZERO");
					} catch(Exception e) {
						prob = 0.0;
					}
					System.out.println("this datum prob: " + String.valueOf(prob));
					if (split.size() > 4 && prob >= 0.85 && (prob >= sentence.get(sentence.size()-2))) { cur_class = "IS_ZERO"; }
					else { cur_class = "NOT_ZERO"; }
					if (cur_class.equals("IS_ZERO")) {
						out1.write("*zero* ");
						System.err.println("for 85 we added a zero for this one");
						System.err.println(strLine);
						System.err.println(posLine);
						classifier.justificationOf(readFeatures(split, pos_split, deps, noSubjVVs, i, "NOT_ZERO"));
					}
					
					if (split.size() > 4 && prob >= 0.90 && (prob >= sentence.get(sentence.size()-2))) { cur_class = "IS_ZERO"; }
					else { cur_class = "NOT_ZERO"; }
					if (cur_class.equals("IS_ZERO")) {
						out2.write("*zero* ");
					}
					
					if (split.size() > 4 && prob >= 0.95 && (prob >= sentence.get(sentence.size()-2))) { cur_class = "IS_ZERO"; }
					else { cur_class = "NOT_ZERO"; }
					if (cur_class.equals("IS_ZERO")) {
						out3.write("*zero* ");
					}
					
					
					if (i != split.size()) { 
						out1.write(split.get(i));
						out2.write(split.get(i));
						out3.write(split.get(i));
					}
					//System.out.println(cur_class);
					if (i < split.size()-1) { 
						out1.write(" ");
						out2.write(" ");
						out3.write(" ");
					}
				}   
				out1.write("\n");
				out2.write("\n");
				out3.write("\n");
			}
			out1.close();
			out2.close();
			out3.close();
		}
	}
}
