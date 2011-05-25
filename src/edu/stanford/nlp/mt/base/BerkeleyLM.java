package edu.stanford.nlp.mt.base;

import scala.actors.threadpool.Arrays;
import edu.berkeley.nlp.lm.BackoffLm;
import edu.berkeley.nlp.lm.StringWordIndexer;
import edu.berkeley.nlp.lm.WordIndexer;
import edu.berkeley.nlp.lm.io.LmReaders;
import edu.berkeley.nlp.lm.map.ConfigOptions;

/**
 * Interface to BerkeleyLM
 * 
 * @author daniel
 *
 */
public class BerkeleyLM implements LanguageModel<IString> {
  final BackoffLm<String> berkeleylm;
  final int order;
  final int[] indexToIndexConv;
  final WordIndexer<String> berkeleyIndexer;
  final String name;
  public final IString startToken;
  public final IString endToken;
  public final IString unknownWord;
  
  @Override
  public double score(Sequence<IString> sequence) {
    int[] ngram = new int[sequence.size()];
    toBerkeleyNgram(sequence, ngram);
    return (double)berkeleylm.getLogProb(ngram);
  }
  
  private void toBerkeleyNgram(Sequence<IString> sequence, int[] ngram) {
    for (int i = 0; i < ngram.length; i++) {
      IString token = sequence.get(i);
      int istringId = token.id;
      if (istringId >= indexToIndexConv.length) {
        istringId = unknownWord.id;
      } else if (indexToIndexConv[istringId] == -1) {
         int berkeleyIndex = berkeleylm.getWordIndexer().getIndexPossiblyUnk(token.toString());
         indexToIndexConv[istringId] = berkeleyIndex;
      }
      ngram[i] = indexToIndexConv[istringId];
    }
  }
              
  @Override
  public IString getStartToken() {
    return startToken;
  }

  @Override
  public IString getEndToken() {
    return endToken;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int order() {
    return order;
  }

  @Override
  public boolean releventPrefix(Sequence<IString> sequence) {
    int[] ngramBig, ngramSmall;
    if (sequence.size() >= order) return false;
    ngramBig = new int[sequence.size()];
    ngramSmall = new int[sequence.size()-1];
    toBerkeleyNgram(sequence, ngramBig);
    System.arraycopy(ngramBig, 1, ngramSmall, 0, ngramSmall.length);
    toBerkeleyNgram(sequence, ngramSmall);
    return berkeleylm.getLogProb(ngramBig) != berkeleylm.getLogProb(ngramSmall);
  }
  
  public BerkeleyLM(String filename, int order) {
     this.berkeleylm = LmReaders.readArpaLmFile(new ConfigOptions(), filename, order, new StringWordIndexer());
     this.order = order;     
     this.name = String.format("BLM(%s)", filename);
     this.berkeleyIndexer = berkeleylm.getWordIndexer();
     this.startToken = new IString(berkeleyIndexer.getStartSymbol());
     this.endToken = new IString(berkeleyIndexer.getEndSymbol());
     this.unknownWord = new IString(berkeleyIndexer.getUnkSymbol());
     this.indexToIndexConv = new int[IString.index.size()];
     Arrays.fill(this.indexToIndexConv, -1);
  }

}
