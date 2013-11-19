/**
 * 
 */
package edu.stanford.nlp.mt.neural;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Generics;

/**
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Nov 15, 2013
 *
 */
public class BilingualEmbedding {
  private Embedding srcEmbedding;
  private Embedding tgtEmbedding;
  private int embeddingSize;

  public BilingualEmbedding(Map<String, SimpleMatrix> srcWordVectors, Map<String, SimpleMatrix> tgtWordVectors){
    srcEmbedding = new Embedding(srcWordVectors);
    tgtEmbedding = new Embedding(tgtWordVectors);
    setSize();
  }

  public BilingualEmbedding(String srcWordFile, String srcVectorFile, String tgtWordFile, String tgtVectorFile) {
    srcEmbedding = new Embedding(srcWordFile, srcVectorFile);
    tgtEmbedding = new Embedding(tgtWordFile, tgtVectorFile);
    setSize();
  }

  private void setSize(){
    if(srcEmbedding.getEmbeddingSize()!=tgtEmbedding.getEmbeddingSize()){
      System.err.println("! src and tgt embeddings have different sizes: " + srcEmbedding.getEmbeddingSize()
          + " vs. " + tgtEmbedding.getEmbeddingSize());
    } else {
      embeddingSize = srcEmbedding.getEmbeddingSize();
    }}

  public double scorePhraseEntry(Sequence<IString> source, Sequence<IString> target, String targetConstellation, 
      int option, int operator){
    double score = Double.NaN;
    if (option==0){ // average word vectors on each side and compute a similarity scores
      SimpleMatrix srcVector = new SimpleMatrix(embeddingSize, 1);
      for(IString src : source){
        srcVector = srcVector.plus(getSrcVector(src.word()));
      }
      
      SimpleMatrix tgtVector = new SimpleMatrix(embeddingSize, 1);
      for(IString tgt : target){
        tgtVector = tgtVector.plus(getTgtVector(tgt.word()));
      }

      if (operator==0){
        score = NeuralUtils.cosine(srcVector, tgtVector);
      } else {
        score = NeuralUtils.dot(srcVector, tgtVector);
      }
    } else if (option==1) { // consider only target alignment
      score = 0;

      if (!targetConstellation.equals("")) {
        PhraseAlignment tgtAlignment = PhraseAlignment.getPhraseAlignment(targetConstellation);
        for(int i=0; i<target.size(); i++){
          int[] alignments = tgtAlignment.t2s(i);
          
          // sum cosine scores of aligned words
          if (alignments!=null && alignments.length>0){
            String tgtWord = target.get(i).word();
            
            for (int alignment : alignments){ // sum score(srcVector, tgtVector)
              String srcWord = source.get(alignment).word();
              if (operator==0){
                score += cosine(srcWord, tgtWord);
              } else {
                score += dot(srcWord, tgtWord);
              }
            }
          }
        }
      }
    }
    
    return score;
  }
  
  public double scorePhraseEntry(String line, int option, int operator){
    List<List<String>> fields = StringUtils.splitFieldsFast(line, FlatPhraseTable.FIELD_DELIM);
    
    // The standard format has five fields
    assert fields.size() == 5 : String.format("line has %d fields. Expected: 5 fields.\n", fields.size());
    Sequence<IString> source = IStrings.toIStringSequence(fields.get(0));
    Sequence<IString> target = IStrings.toIStringSequence(fields.get(1));
//    String sourceConstellation = fields[2];
    String targetConstellation = StringUtils.join(fields.get(3));
    
    return scorePhraseEntry(source, target, targetConstellation, option, operator);
  }
  
  public List<Double> scorePhraseTable(String phraseTableFile, int option, int operator) throws IOException{
    LineNumberReader reader = IOTools.getReaderFromFile(new File(phraseTableFile));
    
    List<Double> scores = new ArrayList<Double>();
    for (String line; (line = reader.readLine()) != null;) {
      scores.add(scorePhraseEntry(line, option, operator));
    }
    
    return scores;
  }
  
  public double cosine(String srcWord, String tgtWord){
    SimpleMatrix srcVector = srcEmbedding.get(srcWord);
    SimpleMatrix tgtVector = tgtEmbedding.get(tgtWord);
    return NeuralUtils.cosine(srcVector, tgtVector);
  }
  
  public double dot(String srcWord, String tgtWord){
    SimpleMatrix srcVector = srcEmbedding.get(srcWord);
    SimpleMatrix tgtVector = tgtEmbedding.get(tgtWord);
    return NeuralUtils.dot(srcVector, tgtVector);
  }
  
  public SimpleMatrix getSrcVector(String word){
    return srcEmbedding.get(word);
  }
  
  public SimpleMatrix getTgtVector(String word){
    return tgtEmbedding.get(word);
  }
  
  public int getEmbeddingSize() {
    return embeddingSize;
  }

  
}
