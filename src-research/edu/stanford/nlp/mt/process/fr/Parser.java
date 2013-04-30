package edu.stanford.nlp.mt.process.fr;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * Use this class to run the french parser
 * @author kevinreschke
 *
 */
public class Parser {

	/**
	 * Runs the french corenlp parser; writes penn-style trees to stdout
	 * 
	 * Parses one sentence per line.
	 * NOTE: each input line is assumed already tokenized
	 * 
	 * Standard parse model is used.
	 * 
	 * Must add tagger to classpath
	 *   (java -cp $CLASSPATH:/u/nlp/data/pos-tagger/distrib/ )
	 * 
	 * args[0] = input file
	 * 
	 */
	public static void main(String args[]) {
		String inFile = args[0];
		
		Properties props = new Properties();
		props.put("tokenize.whitespace","true");
		props.put("ssplit.isOneSentence","true");
		props.put("pos.model", "french.tagger");
		props.put("parse.model",
				"edu/stanford/nlp/models/lexparser/frenchFactored.ser.gz");
		props.put("parse.flags", "");
		props.put("annotators", "tokenize, ssplit, pos, parse");
		
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		
		int lineNo = 0;
		for(String line : ObjectBank.getLineIterator(inFile)) {
			System.err.println(lineNo +": "+line);
			
			Annotation document = new Annotation(line);
			pipeline.annotate(document);
			List<CoreMap> sentences = document.get(SentencesAnnotation.class);
			
			if(sentences.size() != 1) {
				//This shouldn't happen, since we're treating each line as exactly one sentence
				throw new RuntimeException("sentences.size() = " + sentences.size()
						+ " input = " + line);
			}
			
			Tree tree = sentences.get(0).get(TreeAnnotation.class);
			
			System.out.println(lineNo + ": " + tree.pennString());
			
			lineNo++;
		}
		
		System.err.println("Parser Done.");
	}
}
