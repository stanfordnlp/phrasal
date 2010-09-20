package edu.stanford.nlp.mt.decoder.efeat;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.StatefulFeaturizer;
import edu.stanford.nlp.mt.train.discrimdistortion.DistortionModel;

public class MultinomialDistortionFeaturizer extends StatefulFeaturizer<IString,String> implements IncrementalFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "MultinomialDistortion";

  static final String DEBUG_PROPERTY = "Debug" + FEATURE_NAME;
  static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private static final double[] paramCache = new double[DistortionModel.Class.values().length];
  
  public MultinomialDistortionFeaturizer(String... args) {
    assert args.length == 1;
    
    File paramsFile = new File(args[0]);
    if(!paramsFile.exists())
      throw new RuntimeException(this.getClass().getName() + ": Params file does not exits: " + paramsFile.getPath());
    
    loadParams(paramsFile);
    
    System.err.println(this.getClass().getName() + ": Multinomial (log) parameters");
    for(DistortionModel.Class c : DistortionModel.Class.values())
      System.err.printf(" %s:\t%f\n", c, paramCache[c.ordinal()]);
  }
  
  private void loadParams(File paramsFile) {
    LineNumberReader reader = IOTools.getReaderFromFile(paramsFile);
    try {
      
      while(reader.ready()) {
        String line = reader.readLine();
        String[] toks = line.split("\\s+");
        assert toks.length == 2;
        
        DistortionModel.Class thisClass = DistortionModel.Class.valueOf(toks[0]);
        assert thisClass.ordinal() < paramCache.length;
        
        //Parameters file contains raw probabilities
        paramCache[thisClass.ordinal()] = Math.log(Double.parseDouble(toks[1]));
      }
      
      reader.close();
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  private int getDistortion(int fromIdx, int toIdx) {
    int distortion = 0;
    if(fromIdx == -1)
      distortion = toIdx;
    else {
      distortion = fromIdx + 1 - toIdx;
      if(distortion > 0)
        distortion--; //Adjust for bias 
      distortion *= -1; //Turn it into a cost
    }

    return distortion;
  }
  
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    
    int lastSIdx = (f.prior == null) ? -1 : (Integer) f.prior.getState(this);

    final int sOffset = f.foreignPosition;

    double optScore = 0.0;      
    if(f.option.abstractOption.alignment.hasAlignment()) {
      final int tOptLen = f.translatedPhrase.size();
      for(int i = 0; i < tOptLen; i++) {

        final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
        if(sIndices == null || sIndices.length == 0)
          continue; //skip over null aligned target tokens

        final int sIdx = sOffset + sIndices[0];
        int distortion = getDistortion(lastSIdx,sIdx);
        DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(distortion);

        optScore += paramCache[thisClass.ordinal()];
        lastSIdx = sIdx;
      }

    } else {
      int distortion = getDistortion(lastSIdx,sOffset);
      DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(distortion);
      optScore += paramCache[thisClass.ordinal()];
      lastSIdx = sOffset + f.foreignPhrase.size() - 1;
    }
  
    f.setState(this, lastSIdx);

    return new FeatureValue<String>(FEATURE_NAME, optScore);
  }

  
  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {}
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  @Override
  public void reset() {}
}
