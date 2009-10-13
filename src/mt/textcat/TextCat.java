package mt.textcat;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.io.FileSequentialCollection;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.process.ChineseDocumentToSentenceProcessor;

import java.util.*;
import java.io.*;

public class TextCat {

	@SuppressWarnings("unchecked") 
  public static void main(String[] args) throws Exception {
    Properties prop = StringUtils.argsToProperties(args);

    String loadPath = prop.getProperty("load");
    LinearClassifier classifier;
    
    if (loadPath == null) {

      String trainDir = prop.getProperty("train", ".");
      String suffix = prop.getProperty("suffix", "");
      
      ObjectBank<List<String>> nw = getWebData(Collections.singleton(trainDir+"/nw"+suffix+"/c/"));
      ObjectBank<List<String>> wl = getWebData(Collections.singleton(trainDir+"/wl"+suffix+"/c/"));
      ObjectBank<List<String>> ng = getWebData(Collections.singleton(trainDir+"/ng"+suffix+"/c/"));
      
      Map<String,ObjectBank<List<String>>> data = new HashMap<String,ObjectBank<List<String>>>();
      data.put("nw", nw);
      data.put("wl", wl);
      data.put("ng", ng);
      
      Pair<GeneralDataset, float[]> p = getDataset(data);
      GeneralDataset dataset = p.first();    
      dataset.summaryStatistics();
      float[] dataWeights = p.second();
    
      LogPrior prior = new LogPrior(LogPrior.LogPriorType.QUADRATIC, 0.10, 1.0);
      
      LinearClassifierFactory cf = new LinearClassifierFactory();
      cf.setPrior(prior);
      cf.useQuasiNewton(true);
      classifier = (LinearClassifier)cf.trainClassifier(dataset, dataWeights, prior);

      String savePath = prop.getProperty("save");
      if (savePath != null) {
        try {
          IOUtils.writeObjectToFile(classifier, savePath);
        } catch (Exception e) {
          System.err.println("Error saving classifier to: "+savePath+".");
          e.printStackTrace();
        }
      }
      
    } else {
      classifier = (LinearClassifier)IOUtils.readObjectFromFile(loadPath);
    }

    String labelDir = prop.getProperty("label");

    if (labelDir == null) {
      String testDir = prop.getProperty("test", ".");
      String suffix = prop.getProperty("suffix", "");
      ObjectBank<List<String>> nwTest = getWebData(Collections.singleton(testDir+"/mt06/nw"+suffix+"/"));
      ObjectBank<List<String>> webTest = getWebData(Collections.singleton(testDir+"/mt06/ng"+suffix+"/"));
      Map<String,ObjectBank<List<String>>> data = new HashMap<String,ObjectBank<List<String>>>();
      data.put("nw", nwTest);
      data.put("web", webTest);
      
      int right = 0, wrong = 0;
      
      for (String label : data.keySet()) {
        int right1 = 0, wrong1 = 0;
        for (List<String> doc : data.get(label)) {
          String guess = label(doc, classifier).first();
          System.err.println(label(doc, classifier));
          if (guess.equals(label)) { right1++; }
          else { wrong1++; }
        }
        System.err.println(label+"\t"+right1+"\t"+wrong1);
        right += right1;
        wrong += wrong1;
      }
      
      System.err.println(right+"\t"+wrong);
      System.err.println((double)right/(right+wrong));
    } else {
      FileSequentialCollection files = new FileSequentialCollection(Collections.singleton(labelDir), ".norm", true);
      for (File file : files) {
        List<String> doc = Arrays.asList(StringUtils.slurpFile(file.getAbsolutePath(), "utf-8").split("\\s+"));
//        EncodingPrintWriter.err.println(doc);
        Pair<String,Counter<String>> probs = label(doc, classifier);
        System.out.println(file+"\t"+probs.first()+"\t"+probs.second());
      }
    }
  }

  @SuppressWarnings("unchecked")
	private static Pair<String,Counter<String>> label(List<String> doc, LinearClassifier classifier) {
    ClassicCounter<String> features = getFeatures(doc);
    RVFDatum datum = new RVFDatum(features);
    Counter<String> probs = classifier.probabilityOf(datum);
    String guess = Counters.argmax(probs);
    if (!guess.equals("nw")) { guess = "web"; }
    return new Pair<String,Counter<String>>(guess, probs);
  }
  
  private static ObjectBank<List<String>> getWebData(Collection<String> dirPath) {
    
    IteratorFromReaderFactory<List<String>> ifrf = new IteratorFromReaderFactory<List<String>>() {

      public Iterator<List<String>> getIterator(Reader r) {
        String doc = StringUtils.slurpReader(r);
        List<String> words = Arrays.asList(doc.split("\\s+"));
        return Collections.singleton(words).iterator();
      }        
    };
    
    FileSequentialCollection files = new FileSequentialCollection(dirPath, ".norm", true);
    List<File> files1 = new ArrayList<File>(files);
    if (files1.size() > 600) {
      Random random = new Random(42);
      Collections.shuffle(files1, random);
      files1 = files1.subList(0,600);
    }
    ReaderIteratorFactory rif = new ReaderIteratorFactory(files1, "UTF-8");
    ObjectBank<List<String>> ob = new ObjectBank<List<String>>(rif, ifrf);
    
    return ob;
  }

  @SuppressWarnings("unchecked")
  private static Pair<GeneralDataset,float[]> getDataset(Map<String,ObjectBank<List<String>>> data) {

    List<Float> dataWeights = new ArrayList<Float>();
    GeneralDataset dataset = new RVFDataset();
    
    for (String label : data.keySet()) {
      System.err.println(label);
      ObjectBank<List<String>> docs = data.get(label);
      for (List<String> doc : docs) {
        ClassicCounter<String> features = getFeatures(doc);
        RVFDatum datum = new RVFDatum(features, label);        
        dataset.add(datum);        
        if (!label.equals("nw")) {
          dataWeights.add(1.0f);
        } else {
          dataWeights.add(1.0f);
        }
      }
    }

    float[] dataWeightsArray = new float[dataWeights.size()];
    for (int i = 0; i < dataWeights.size(); i++) {
      dataWeightsArray[i] = dataWeights.get(i);
    }
    
    return new Pair<GeneralDataset,float[]>(dataset, dataWeightsArray);
  }

  private static ClassicCounter<String> getFeatures(List<String> doc) {
    ClassicCounter<String> features = new ClassicCounter<String>();

    for (String word : doc) {
      features.setCount(word, 1.0);
    }

    String concat = StringUtils.join(doc, " ");
    List<String> sentences;
    try {
      sentences = ChineseDocumentToSentenceProcessor.fromPlainText(concat, true);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    double avgLength = ((double)doc.size())/sentences.size();
    
    features.incrementCount("avg sent length", Math.log(avgLength));
    features.incrementCount("###");

    return features;
  }  
}
