package edu.stanford.nlp.mt.base;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;

import java.util.LinkedList;

import tokyocabinet.*;

/**
 * @author danielcer
 *
 */
public class DynamicPhraseTable<FV> extends
    AbstractPhraseGenerator<IString, FV> {
  static final boolean DEBUG = false;
  static final int MAX_ABSOLUTE_DISTORTION = 12;

  private BDB bdb;

  Set<String> currentSequence;

  // IBMModel1 model1S2T, model1T2S;

  public DynamicPhraseTable(
      IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer,
      String phraseTableName, String model1S2T,
      String model1T2S) {
    super(phraseFeaturizer);
    currentSequence = new HashSet<String>();
    initdb(phraseTableName);
  }

  public DynamicPhraseTable(
      IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer,
      String phraseTableName) {
    super(phraseFeaturizer);
    currentSequence = new HashSet<String>();
    initdb(phraseTableName);
  }

  private void initdb(String phraseTableName) {
    try {
      File f = new File(phraseTableName);
      if (!f.exists()) {
        f.mkdir();
      }

      bdb = new BDB();
      if (!bdb.open(phraseTableName, BDB.OREADER))
        throw new RuntimeException();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static final int phraseLengthLimit = 5;

  @Override
  public String getName() {
    return "DynaPhraseTable";
  }

  public static final String[] labs = { "pc(e|f)", "pc(f|e)",
      "lex(e|f)", "lex(f|e)" };

  @Override
  public List<TranslationOption<IString>> getTranslationOptions(
      Sequence<IString> sequence) {
    try {
      List<TranslationOption<IString>> opts = new LinkedList<TranslationOption<IString>>();

      RawSequence<IString> rawSequence = new RawSequence<IString>(sequence);

      @SuppressWarnings({ "unchecked", "rawtypes" })
      List<byte[]> listByteOpts = (List) bdb.getlist(sequence.toString()
          .getBytes("UTF-8"));

      if (listByteOpts == null)
        return opts;

      for (byte[] byteOpts : listByteOpts) {
        DataInputStream distrm = new DataInputStream(new ByteArrayInputStream(
            byteOpts));
        String trans = distrm.readUTF();
        float pcEgF = distrm.readFloat();
        float pcFgE = distrm.readFloat();
        float pLexEgF = distrm.readFloat();
        float pLexFgE = distrm.readFloat();
        RawSequence<IString> transSeq = new RawSequence<IString>(
            IStrings.toIStringArray(trans.split("\\s+")));
        String mappingKey = sequence + "=:=>" + trans;
        opts.add(new TranslationOption<IString>(new float[] { pcEgF, pcFgE,
            pLexEgF, pLexFgE }, labs, transSeq, rawSequence, null,
            currentSequence.contains(mappingKey)));
      }

      return opts;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int longestForeignPhrase() {
    return phraseLengthLimit;
  }

  public void close() {
    try {
      // db.close();
      // dbEnv.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setCurrentSequence(Sequence<IString> foreign,
      List<Sequence<IString>> tranList) {
    currentSequence = new HashSet<String>();
    if (tranList == null)
      return;
    int pairSpecificPhrases = 0;
    for (Sequence<IString> trans : tranList) {
      for (int fStart = 0; fStart < foreign.size(); fStart++) {
        for (int fEnd = fStart; fEnd < foreign.size()
            && fEnd < fStart + phraseLengthLimit; fEnd++) {
          Sequence<IString> fPhrase = foreign.subsequence(fStart, fEnd + 1);
          int phraseSpecificTranslations = 0;

          int tEquivFStart = (int) ((fStart / (double) foreign.size()) * trans
              .size());
          for (int tStart = Math.max(0, tEquivFStart - MAX_ABSOLUTE_DISTORTION); tStart < Math
              .min(trans.size(), tEquivFStart + MAX_ABSOLUTE_DISTORTION); tStart++) {
            for (int tEnd = tStart; tEnd < trans.size()
                && tEnd < tStart + phraseLengthLimit; tEnd++) {

              Sequence<IString> tPhrase = trans.subsequence(tStart, tEnd + 1);
              String featRep = fPhrase + "=:=>" + tPhrase;
              currentSequence.add(featRep);
              // System.err.printf("putting '%s=:=>%s'\n", fPhrase, tPhrase);
              pairSpecificPhrases++;
              phraseSpecificTranslations++;
            }
          }
        }
      }
    }
  }

  private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

  private static int extractFromSequence(BDB tmpF2E, BDB tmpE2F,
      BDB tmpFPhrases, Sequence<IString> foreign, Sequence<IString> trans)
      throws Exception {
    int pairSpecificPhrases = 0;
    for (int fStart = 0; fStart < foreign.size(); fStart++) {
      for (int fEnd = fStart; fEnd < foreign.size()
          && fEnd < fStart + phraseLengthLimit; fEnd++) {
        Sequence<IString> fPhrase = foreign.subsequence(fStart, fEnd + 1);
        int phraseSpecificTranslations = 0;

        int tEquivFStart = (int) ((fStart / (double) foreign.size()) * trans
            .size());
        for (int tStart = Math.max(0, tEquivFStart - MAX_ABSOLUTE_DISTORTION); tStart < Math
            .min(trans.size(), tEquivFStart + MAX_ABSOLUTE_DISTORTION); tStart++) {
          for (int tEnd = tStart; tEnd < trans.size()
              && tEnd < tStart + phraseLengthLimit; tEnd++) {

            Sequence<IString> tPhrase = trans.subsequence(tStart, tEnd + 1);
            byte[] fBytes = fPhrase.toString().getBytes("UTF-8");
            byte[] tBytes = tPhrase.toString().getBytes("UTF-8");
            tmpF2E.putdup(fBytes, tBytes);
            tmpE2F.putdup(tBytes, fBytes);
            tmpFPhrases.put(fBytes, EMPTY_BYTE_ARRAY);
            // System.err.printf("putting '%s=:=>%s'\n", fPhrase, tPhrase);
            pairSpecificPhrases++;
            phraseSpecificTranslations++;
          }
        }
      }
    }
    return pairSpecificPhrases;
  }

  @SuppressWarnings("unchecked")
  static public void extractDB(String fCorpus, String eCorpus, String tcFile,
      IBMModel1 model1F2E, IBMModel1 model1E2F) throws Exception {
    BufferedReader fReader = new BufferedReader(new FileReader(fCorpus));
    BufferedReader eReader = new BufferedReader(new FileReader(eCorpus));
    BDB tmpF2E = new BDB();
    String fileTmpF2E = "tmp." + (new File(fCorpus)).getName() + ".2."
        + (new File(eCorpus)).getName();
    String fileTmpE2F = "tmp." + (new File(eCorpus)).getName() + ".2."
        + (new File(fCorpus)).getName();
    String fileTmpFPhrases = "tmp." + (new File(fCorpus)).getName()
        + ".phrases";

    System.err.printf("tmp f2e: %s\n", fileTmpF2E);
    System.err.printf("tmp e2f: %s\n", fileTmpE2F);
    System.err.printf("tmp fPhrases: %s\n", fileTmpFPhrases);

    if (!tmpF2E.open(fileTmpF2E, BDB.OWRITER | BDB.OTRUNC | BDB.OCREAT))
      throw new RuntimeException();
    BDB tmpE2F = new BDB();
    if (!tmpE2F.open(fileTmpE2F, BDB.OWRITER | BDB.OTRUNC | BDB.OCREAT))
      throw new RuntimeException();

    BDB tmpFPhrases = new BDB();
    if (!tmpFPhrases.open(fileTmpFPhrases, BDB.OWRITER | BDB.OTRUNC
        | BDB.OCREAT))
      throw new RuntimeException();

    int lineno = -1;
    long phrasesExtracted = 0;
    for (String fLine = fReader.readLine(), eLine = eReader.readLine(); fLine != null
        && eLine != null; fLine = fReader.readLine(), eLine = eReader
        .readLine()) {
      lineno++;
      Sequence<IString> fSeq = new RawSequence<IString>(
          IStrings.toIStringArray(fLine.split("\\s+")));
      Sequence<IString> eSeq = new RawSequence<IString>(
          IStrings.toIStringArray(eLine.split("\\s+")));

      phrasesExtracted += extractFromSequence(tmpF2E, tmpE2F, tmpFPhrases,
          fSeq, eSeq);
      if (lineno % 100 == 0) {
        System.err.printf("lineno > %d (phrases: %d)\n", lineno,
            phrasesExtracted);
      }
    }
    System.err
        .printf("Phrases Extracted pre-filtering: %d\n", phrasesExtracted);

    // syncing before close prevents weird hanging behavior
    System.err.printf("Closing tmpF2E\n");
    tmpF2E.sync();
    tmpF2E.close();
    System.err.printf("Closing tmpE2F\n");
    tmpE2F.sync();
    tmpE2F.close();
    System.err.printf("Closing tmpFPhrases\n");
    tmpFPhrases.sync();
    tmpFPhrases.close();

    tmpF2E = new BDB();
    tmpE2F = new BDB();
    tmpFPhrases = new BDB();

    tmpF2E.open(fileTmpF2E, BDB.OREADER);
    tmpE2F.open(fileTmpE2F, BDB.OREADER);
    tmpFPhrases.open(fileTmpFPhrases, BDB.OREADER);

    String fileProbE2F = "tmp.prob." + (new File(eCorpus)).getName() + ".2."
        + (new File(fCorpus)).getName();
    System.err.printf("fileProbE2F: %s\n", fileProbE2F);
    BDB tmpProbE2F = new BDB();
    if (!tmpProbE2F.open(fileProbE2F, BDB.OWRITER | BDB.OTRUNC | BDB.OCREAT
        | BDB.OREADER))
      throw new RuntimeException();

    BDB dbPT = new BDB();
    if (!dbPT.open(tcFile, BDB.OWRITER | BDB.OTRUNC | BDB.OCREAT))
      throw new RuntimeException();

    BDBCUR fcur = new BDBCUR(tmpFPhrases);
    fcur.first();
    byte[] key;
    int lowCntsRemoved = 0;
    int ptSize = 0;
    while ((key = fcur.key()) != null) {
      String fphrase = new String(key, "UTF-8");
      System.err.printf("%s\n", fphrase);
      @SuppressWarnings("rawtypes")
      List<byte[]> transByteList = (List) tmpF2E.getlist(fcur.key());
      ClassicCounter<String> pF2E = new ClassicCounter<String>();

      for (byte[] transByte : transByteList) {
        String trans = new String(transByte, "UTF-8");
        pF2E.incrementCount(trans);
      }

      Set<String> lowCnt = new HashSet<String>();
      for (Map.Entry<String, Double> entry : pF2E.entrySet()) {
        if (entry.getValue() < 3) {
          lowCnt.add(entry.getKey());
        }
      }

      double oldTotalCnt = pF2E.totalCount();

      for (String lowCntKey : lowCnt) {
        System.err.println("Skipping: " + fphrase + " ||| " + lowCntKey
            + " ||| " + pF2E.getCount(lowCntKey));
        pF2E.remove(lowCntKey);
      }

      lowCntsRemoved += lowCnt.size();

      if (pF2E.size() == 0) {
        fcur.next();
        continue;
      }

      Counters.divideInPlace(pF2E, oldTotalCnt);

      ClassicCounter<String> pE2F = new ClassicCounter<String>();

      for (String trans : pF2E.keySet()) {
        String probStr = tmpProbE2F.get(fphrase + ":::" + trans);
        if (probStr == null) {
          @SuppressWarnings("rawtypes")
          List<byte[]> forByteList = (List) tmpE2F.getlist(
                  trans.getBytes("UTF-8"));
          ClassicCounter<String> allE2F = new ClassicCounter<String>();
          for (byte[] forByte : forByteList) {
            String eForeign = new String(forByte, "UTF-8");
            allE2F.incrementCount(eForeign + ":::" + trans);
          }
          double allE2Ftc = allE2F.totalCount();
          for (Map.Entry<String, Double> entry : allE2F.entrySet()) {
            if (entry.getValue() < 3)
              continue;
            tmpProbE2F.put(entry.getKey(), Double.toString(entry.getValue()
                    / allE2Ftc));
          }
          probStr = tmpProbE2F.get(fphrase + ":::" + trans);
        } else {
          System.err.println("ProbStr Match!!!!! >" + fphrase + ":::" + trans);
        }
        pE2F.setCount(trans, Double.parseDouble(probStr));
      }

      for (String trans : pF2E.keySet()) {
        float pcEgF = (float) Math.log(pF2E.getCount(trans)); // pcEgF
        float pcFgE = (float) Math.log(pE2F.getCount(trans)); // pcFgE
        RawSequence<IString> fphraseSeq = new RawSequence<IString>(
            IStrings.toIStringArray(fphrase.split("\\s+")));
        RawSequence<IString> transSeq = new RawSequence<IString>(
            IStrings.toIStringArray(trans.split("\\s+")));
        float pLexEgF = (float) model1F2E.score(fphraseSeq, transSeq); // pLexEgF
        float pLexFgE = (float) model1E2F.score(transSeq, fphraseSeq); // pLexFgE

        System.err.printf("Inserting %s ||| %s %f %f %f %f\n", fphrase, trans,
            Math.exp(pcEgF), Math.exp(pcFgE), Math.exp(pLexEgF),
            Math.exp(pLexFgE));

        ByteArrayOutputStream bostrm = new ByteArrayOutputStream();
        DataOutputStream dostrm = new DataOutputStream(bostrm);

        dostrm.writeUTF(trans);
        dostrm.writeFloat(pcEgF);
        dostrm.writeFloat(pcFgE);
        dostrm.writeFloat(pLexEgF);
        dostrm.writeFloat(pLexFgE);
        dostrm.flush();
        dbPT.putdup(fphrase.getBytes("UTF-8"), bostrm.toByteArray());
        ptSize++;
      }

      fcur.next();
    }
    System.err.printf("Low cnts removed: %d\n", lowCntsRemoved);
    System.err.printf("Phrase table size: %d\n", ptSize);
    tmpProbE2F.sync();
    tmpProbE2F.close();
    dbPT.sync();
    dbPT.close();
  }

  /**
	 */
  public static void main(String[] args) throws Exception {
    String strModel1F2E = args[0].split(":")[1];
    System.out.printf("Loading F2E: %s\n", strModel1F2E);
    IBMModel1 model1F2E = IBMModel1.load(strModel1F2E);

    String strModel1E2F = args[1].split(":")[1];
    System.out.printf("Loading E2F: %s\n", strModel1E2F);
    IBMModel1 model1E2F = IBMModel1.load(strModel1E2F);
    extractDB(args[0].split(":")[0], args[1].split(":")[0], args[2], model1F2E,
        model1E2F);
  }
}
