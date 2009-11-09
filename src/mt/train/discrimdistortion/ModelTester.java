package mt.train.discrimdistortion;

import java.io.*;
import java.util.List;
import java.util.StringTokenizer;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import mt.base.IOTools;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;

public class ModelTester {

	//Uses GNU getopt() syntax
	private final static OptionParser op = new OptionParser("sl");
	private final static int MIN_ARGS = 2;
	
	public static void main(String[] args) {
		OptionSet opts = null;
		List<String> parsedArgs = null;
		try {
			opts = op.parse(args);

			parsedArgs = opts.nonOptionArguments();

			if(parsedArgs == null || parsedArgs.size() < MIN_ARGS)
				System.exit(-1);

		} catch (OptionException e) {
			System.err.println(e.toString());
			System.exit(-1);
		}
		
		String modelFile = parsedArgs.get(0);
		String evalFile = parsedArgs.get(1);
		
		DistortionModel m = null;
		try {
			m = (DistortionModel) IOUtils.readObjectFromFile(modelFile);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		System.out.println("POS Tag Index:");
		System.out.println(m.tagIndex.toString());
		
		if(opts.has("s"))
			evalSingle(m,evalFile);
		else if(opts.has("l"))
		  evalLogLik(m,evalFile);
		else
			evalTestSet(m,evalFile);	
	}
	
	private static void evalLogLik(DistortionModel m, String testPrefix) {
    
	  System.out.println(">> Evaluating Log Likelihood <<");
	  
	  File algnFile = new File(testPrefix + ".algn");
    File arFile = new File(testPrefix + ".f");
    File enFile = new File(testPrefix + ".e");
    
	  FeatureExtractor fe = new FeatureExtractor(1,arFile,enFile,algnFile);
	  fe.setMinWordCount(1);
	  fe.setVerbose(true);
	  fe.setExtractOnly();
	  fe.initializeWithModel(m);
	  
	  double logLik = 0.0;
	  TrainingSet ts = fe.extract(m.featureIndex, m.classIndex, 120000);
	  int exampleNum = 1;
	  for(Datum d : ts) {
	    boolean isOOV = false;
	    if(m.featureIndex.contains(DistortionModel.Feature.Word)) {
	      int wordIdx = m.featureIndex.indexOf(DistortionModel.Feature.Word);
	      isOOV = (d.get(wordIdx) == -1.0f);
	    }
	    
	    Pair<Double, DistortionModel.Class> thisClass = m.argmax(d, isOOV);
	    double logProb = m.prob(d, thisClass.second(), isOOV);
	    logLik += logProb;
	  }
	  
	  System.out.printf("Test alignments: %d\n", ts.getNumExamples());
	  System.out.printf("logLik: %f\n", logLik);  
  }

  private static void evalTestSet(DistortionModel model, String testFile) {
		LineNumberReader reader = IOTools.getReaderFromFile(testFile);
		try {
			int totalExamples = 0;
			int totalCorrect = 0;
			int OOVexamples = 0;
			int OOVcorrect = 0;
			
			PrintStream debug = IOTools.getWriterFromFile("tester.debug");
			while(reader.ready()) {
				final String inputExample = reader.readLine();
				StringTokenizer st = new StringTokenizer(inputExample);
				
				float[] feats = new float[model.getFeatureDimension()];
				DistortionModel.Class goldClass = DistortionModel.Class.valueOf(st.nextToken());
				
				int i = 0;
				boolean isOOV = false;
				for(DistortionModel.Feature feat : model.featureIndex) {
					if(!st.hasMoreTokens()) throw new RuntimeException(String.format("Bad feature format at line ",reader.getLineNumber()));
					
					final String tok = st.nextToken();
					if(feat == DistortionModel.Feature.Word) {
						if(model.wordIndex.contains(tok)) {
							feats[i] = (float) model.wordIndex.indexOf(tok);
						} else {
							isOOV = true;
						}
					} else if(feat == DistortionModel.Feature.CurrentTag)
						feats[i] = (float) model.tagIndex.indexOf(tok);
					else if(feat == DistortionModel.Feature.RelPosition)
						feats[i] = Float.parseFloat(tok);
					else if(feat == DistortionModel.Feature.SourceLen)
						feats[i] = Float.parseFloat(tok);
					i++;
				}
				
				Datum datum = new Datum(0.0f,feats);
				
				if(isOOV) {
					OOVexamples++;
					Pair<Double,DistortionModel.Class> prediction = model.argmax(datum, true);
					if(prediction.second() == goldClass) {
						totalCorrect++;
						OOVcorrect++;
					}
				} else {
					Pair<Double,DistortionModel.Class> prediction = model.argmax(datum, false);
					if(prediction.second() == goldClass)
						totalCorrect++;
					else {
					//	double goldScore = model.modelScore(datum, goldClass, false);
						double goldProb = model.prob(datum, goldClass, false);
						double predProb = model.prob(datum, prediction.second(), false);
						debug.printf("gold: %s (%f)\tguess: %s (%f)\t[ %s ]\n", goldClass.toString(), goldProb, prediction.second().toString(),predProb, inputExample.trim());
					}
				}
			
				totalExamples++;
			}
			debug.close();
			reader.close();
			
			//Print results
			float accuracy = (float) totalCorrect / (float) totalExamples;
			float OOVaccuracy = (float) OOVcorrect / (float) OOVexamples;
			System.out.printf("Results for %s:\n",testFile);
			System.out.printf("Total: %f\t( %d / %d )\n", accuracy, totalCorrect, totalExamples);
			System.out.printf("OOV  : %f\t( %d / %d )\n", OOVaccuracy, OOVcorrect, OOVexamples);
			System.out.println();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void evalSingle(DistortionModel model, String evalFile) {
		//Read the featureSpec(s)
		LineNumberReader reader = IOTools.getReaderFromFile(evalFile);
		try {
			while(reader.ready()) {
				
				String input = reader.readLine();
				if(input.trim().equals("")) continue;
				
				PrintStream ps = IOTools.getWriterFromFile(evalFile + ".out." + Integer.toString(reader.getLineNumber()));
				
				StringTokenizer st = new StringTokenizer(input);
				String word = null,tag = null,rTag = null, lTag = null;
				int slen = 0;
				for(int j = 0; st.hasMoreTokens(); j++) {
					if(j == 0) word = st.nextToken();
					else if(j == 1) tag = st.nextToken();
					else if(j == 2) slen = Integer.parseInt(st.nextToken());
          else if(j == 3) lTag = st.nextToken();
					else if(j == 4) rTag = st.nextToken();
				}

				//Assumes these words and tags are in the model...exception otherwise
        boolean isOOV = !model.wordIndex.contains(word);
				float[] feats = new float[model.getFeatureDimension()];
				int featPtr = 0;
	      for(DistortionModel.Feature feat : model.featureIndex) {
	        if(feat == DistortionModel.Feature.Word && !isOOV)
	          feats[featPtr++] = (float) model.wordIndex.indexOf(word);
	        else if(feat == DistortionModel.Feature.CurrentTag)
	          feats[featPtr++] = (float) model.tagIndex.indexOf(tag);
	        else if(feat == DistortionModel.Feature.RelPosition)
	          feats[featPtr++] = 0.0f;
	        else if(feat == DistortionModel.Feature.SourceLen)
	          feats[featPtr++] = (float) DistortionModel.getSlenBin(slen);
	        else if(feat == DistortionModel.Feature.RightTag)
              feats[featPtr++] = (float) model.tagIndex.indexOf(rTag, true);
          else if(feat == DistortionModel.Feature.LeftTag)
            feats[featPtr++] = (float) model.tagIndex.indexOf(lTag, true);
          }
				
				for(int rpos = 0; rpos < 100; rpos += 20) {
					feats[3] = DistortionModel.getSlocBin((float) rpos / 100.0f);
					Datum d = new Datum(0.0f,feats);
					double totalMass = 0.0;
					ps.println(feats[3]);
					for(DistortionModel.Class c : DistortionModel.Class.values()) {
						double prob = model.prob(d, c, isOOV);
						totalMass += prob;
						ps.printf("%d %f\n", c.ordinal(), prob);
					}
					System.out.printf("pos: %f mass: %f\n", feats[3], totalMass);
				}
				
				ps.close();
			}

			reader.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
