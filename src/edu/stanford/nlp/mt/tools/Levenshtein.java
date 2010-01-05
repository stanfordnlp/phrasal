package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.mt.base.HasIntegerIdentity;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.train.DTUPhraseExtractor;

import java.util.List;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import edu.stanford.nlp.objectbank.ObjectBank;

/**
 * @author Michel Galley
 */
public class Levenshtein<T extends HasIntegerIdentity> {

  // TODO: make this more generic and move this to core.

  private static final byte U = 2;
  private static final byte L = 3;
  private static final byte M = 5;

  protected Sequence<T> x;
  protected Sequence<T> y;

  final byte[][] c = new byte[256][256];
  final byte[][] b = new byte[256][256];
  final BitSet bitset = new BitSet();

  public void init(Sequence<T> x, Sequence<T> y) {

    this.x = x;
    this.y = y;

    int m = x.size(), n = y.size();

    // leading zeroes:
		int mX = m + 1;
		int nY = n + 1;

    for (int j=1; j<nY; j++) {
      c[0][j] = 0;
      b[0][j] = 0;
    }

    // Calculate the LCS:
		for (int i=1; i<mX; i++) {
      c[i][0] = 0;
      b[i][0] = 0;
      for (int j=1; j<nY; j++) {
        //c[i][j] = 0;
        b[i][j] = 0;
        if (x.get(i-1).getId() == y.get(j-1).getId()) {
					c[i][j] = (byte) (c[i-1][j-1] + 1);
          b[i][j] = M;
				}
				else if (c[i-1][j] >= c[i][j-1]) {
					c[i][j] = c[i-1][j];
					b[i][j] = U;
				}
				else {
					c[i][j] = c[i][j-1];
          b[i][j] = L;
				}
			}
		}
    bitset.clear();
  }

  private boolean hasFlag(byte val, byte flag) {
    return val == flag;
  }

  public BitSet longestCommonSubsequence() {
    longestCommonSubsequence(bitset, x.size(), y.size());
    return bitset;
  }

  private void longestCommonSubsequence(BitSet bitset, int i, int j) {
    if (0 == i || 0 == j)
      return;

    if (hasFlag(b[i][j],M)) {
      longestCommonSubsequence(bitset, i-1, j-1);
      bitset.set(i-1);
    }
    else if (hasFlag(b[i][j],U)) {
      longestCommonSubsequence(bitset, i-1, j);
    }
    else {
      longestCommonSubsequence(bitset, i, j-1);
    }
  }

  public static void addSubsequences(BitSet in, Set<BitSet> out, int maxSpan) {
    int startIdx = in.nextSetBit(0);
    while (startIdx >= 0) {
      int endIdx = in.nextSetBit(in.nextClearBit(startIdx));
      BitSet tmpSet = new BitSet();
      for(int i=startIdx; i<=endIdx; ++i) {
        tmpSet.set(i, in.get(i));
      }
      while(startIdx < endIdx && endIdx-startIdx+1 <= maxSpan) {
        tmpSet.set(endIdx);
        out.add((BitSet)tmpSet.clone());
        endIdx = in.nextSetBit(endIdx+1);
      }
      startIdx = in.nextSetBit(startIdx+1);
    }
  }

  public static void main(String[] args) {
    List<Sequence<IString>> test = new ArrayList<Sequence<IString>>();

    for(String line : ObjectBank.getLineIterator(args[1])) {
      Sequence<IString> sent = new SimpleSequence<IString>(true, IStrings.toIStringArray(line.split("\\s+")));
      test.add(sent);
    }

    int lineNb = -1;
    long startStepTimeMillis = System.currentTimeMillis();
    Levenshtein<IString> l = new Levenshtein<IString>();
    for(String line : ObjectBank.getLineIterator(args[0])) {
      Sequence<IString> x = new SimpleSequence<IString>(true, IStrings.toIStringArray(line.split("\\s+")));
      if(lineNb == 3000)
        break;
      if(++lineNb % 1000 == 0) {
        double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
        startStepTimeMillis = System.currentTimeMillis();
        System.err.printf("line %d, secs = %.3f...\n", lineNb, totalStepSecs);
      }
      Set<BitSet> bitsets = new HashSet<BitSet>();
      for (int j=0; j < test.size(); ++j) {
        Sequence<IString> y = test.get(j);
        System.out.println("x: "+x);
        System.out.println("y: "+y);
        l.init(x,y);
        BitSet bitset = l.longestCommonSubsequence();
        addSubsequences(bitset, bitsets, 12);
      }
      for(BitSet bs : bitsets) {
        System.out.print(bs);
        System.out.print(" :");
        for(int i : DTUPhraseExtractor.bitSetToIntSet(bs)) {
          System.out.print(" "+x.get(i));
        }
        System.out.println();
      }
    }
  }
}
