package edu.stanford.nlp.mt.decoder.inferer.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.mt.decoder.inferer.AbstractInferer;
import edu.stanford.nlp.mt.decoder.util.ConstrainedOutputSpace;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.OptionGrid;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.util.Pair;

import edu.berkeley.nlp.wordAlignment.EMWordAligner;
import edu.berkeley.nlp.wordAlignment.Model;
import edu.berkeley.nlp.wordAlignment.distortion.DistortionModel;
import edu.berkeley.nlp.wordAlignment.distortion.StringDistanceModel;
import edu.berkeley.nlp.wordAlignment.distortion.StateMapper.EndsStateMapper;
import edu.berkeley.nlp.wordAlignment.distortion.StateMapper;
import edu.berkeley.nlp.wordAlignment.SentencePairState;
import edu.berkeley.nlp.wa.mt.SentencePair;
//import edu.berkeley.nlp.mt.SentencePair;
import edu.berkeley.nlp.wa.mt.Alignment;
//import edu.berkeley.nlp.mt.Alignment;

/**
 * Prefix decoder
 *
 * @author daniel
 */
public class PrefixDecoder<FV> extends AbstractInferer<IString, FV> {
  public static boolean DEBUG = true;

  int maxDistortion = -1;
  EMWordAligner alignerRv;

  public PrefixDecoder(AbstractInferer<IString, FV> inferer, String reverseWAModel) {
    super(inferer);
    if (inferer instanceof MultiBeamDecoder) {
      MultiBeamDecoder<IString, FV> mbd = (MultiBeamDecoder<IString, FV>)inferer;
      maxDistortion = mbd.maxDistortion;
    } else if (inferer instanceof DTUDecoder) {
      throw new UnsupportedOperationException();
    }
    Model<DistortionModel> paramsRv = Model.load(reverseWAModel);
    System.err.printf("paramsRv: %s\n", paramsRv);
    System.err.printf("paramsRv.transProbs: %s\n", paramsRv.transProbs);

    paramsRv.transProbs.lock();
    StateMapper mapper = new EndsStateMapper();
    DistortionModel distModel = new StringDistanceModel(mapper);
    SentencePairState.Factory spsFactory = distModel.getSpsFactory();
    alignerRv = new EMWordAligner(spsFactory, null, true);
    alignerRv.trainingCache = distModel.getTrainingCache();
    alignerRv.params = paramsRv;
  }

  @Override
  public boolean shutdown() {
    return true;
  }

  public PhraseGenerator<IString,FV> getPhraseGenerator() {
    return phraseGenerator;
  }

  @Override
  public RichTranslation<IString, FV> translate(Sequence<IString> source,
                                                int translationId, ConstrainedOutputSpace<IString, FV> constrainedOutputSpace,
                                                List<Sequence<IString>> targets) {
    throw new UnsupportedOperationException();
  }


  @Override
  public RichTranslation<IString, FV> translate(Scorer<FV> scorer,
                                                Sequence<IString> source, int translationId,
                                                ConstrainedOutputSpace<IString, FV> constrainedOutputSpace,
                                                List<Sequence<IString>> targets) {
    throw new UnsupportedOperationException();
  }

  public Map<Pair<Sequence<IString>,Sequence<IString>>,PriorityQueue<Hypothesis<IString, FV>>> hypCache = new
          HashMap<Pair<Sequence<IString>,Sequence<IString>>,PriorityQueue<Hypothesis<IString, FV>>>();

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public List<RichTranslation<IString, FV>> nbest(Scorer<FV> scorer,
                                                  Sequence<IString> source, int sourceInputId,
                                                  ConstrainedOutputSpace<IString, FV> constrainedOutputSpace,
                                                  List<Sequence<IString>> targets, int size) {

    PriorityQueue<Hypothesis<IString, FV>> agenda = new PriorityQueue<Hypothesis<IString,FV>>();
    PriorityQueue<Hypothesis<IString, FV>> paused = new PriorityQueue<Hypothesis<IString,FV>>();
    int windowSize = 100;
    int maxPrefixCompletion = 0;

    List<ConcreteTranslationOption<IString,FV>> options = phraseGenerator.translationOptions(source, targets, sourceInputId, scorer);
    List<ConcreteTranslationOption<IString,FV>> filteredOptions = constrainedOutputSpace.filterOptions(options);
    float[] autoInsertScores = new float[options.get(0).abstractOption.scores.length];
    String[] scoreNames = options.get(0).abstractOption.phraseScoreNames;

    if (DEBUG) {
      System.err.println("filtered options (for prefix)");
      System.err.println("========================================");
      for (ConcreteTranslationOption<IString,FV> cto : filteredOptions) {
        System.err.printf(" - %s -> %s (%s)\n", cto.abstractOption.source, cto.abstractOption.target, cto.sourcePosition);
      }

      System.err.println("unfiltered options (for suffix)");
      System.err.println("========================================");
      for (ConcreteTranslationOption<IString,FV> cto : options) {
        System.err.printf(" - %s -> %s (%s)\n", cto.abstractOption.source, cto.abstractOption.target, cto.sourcePosition);
      }
    }

    Arrays.fill(autoInsertScores, -10000);
    OptionGrid<IString,FV> optionGrid = new OptionGrid<IString,FV>(options, source);
    OptionGrid<IString,FV> filteredOptionGrid = new OptionGrid<IString,FV>(filteredOptions, source);


    // use *UNFILTERED* options for heuristic calculation
    Hypothesis<IString, FV> nullHyp = new Hypothesis<IString, FV>(sourceInputId, source, heuristic, scorer, annotators, Arrays.asList(options));
    featurizer.initialize(sourceInputId, options, source, scorer.getFeatureIndex());
    if (DEBUG) {
      System.err.printf("Adding initial hypothesis: %s\n", nullHyp);
    }
    /*
    EnumeratedConstrainedOutputSpace<IString, FV> ecos = (EnumeratedConstrainedOutputSpace<IString, FV>)constrainedOutputSpace;
    Sequence<IString> prefix = ecos.allowableSequences.get(0);
    for (int i = prefix.size(); i > 0; i--) {
      Sequence<IString> subSeq = prefix.subsequence(0, i);
      PriorityQueue<Hypothesis<IString, FV>> savedHyps = hypCache.get(
          new Pair<Sequence<IString>,Sequence<IString>>(foreign, subSeq));
      if (savedHyps != null) {
        System.err.printf("Recovering saved hyps for prefix: %s\n", subSeq);
        agenda.addAll(savedHyps);
        break;
      }
    }
    if (agenda.size() == 0) {
      System.err.printf("Could not recover saved hyps for prefix: %s\n", prefix);
      //System.err.println("Only have saved hyps for prefixes:");
      //for (Sequence<IString> prefix )
      agenda.add(nullHyp);
    } */
    Hypothesis waHyp = nullHyp;
    int sourceSz = source.size();


    List<String> fWords = new ArrayList<String>(source.size());
    List<String> eWords;
    if (targets == null || targets.isEmpty()) {
      eWords = new ArrayList<String>(0);
    } else {
      eWords = new ArrayList<String>(targets.get(0).size());
    }

    for (int i = 0; i < source.size(); i++) {
      fWords.add(source.get(i).toString());
    }

    for (int i = 0; targets != null && i < targets.get(0).size(); i++) {
      eWords.add(targets.get(0).get(i).toString());
    }

    if (DEBUG) {
      System.err.println("Targets:\n"+targets);
    }

    SentencePair sp = new SentencePair(0, "none", eWords, fWords);
    if (targets != null && targets.size() > 0 && !targets.get(0).toString().equals("")) {
      Alignment aRv = alignerRv.alignSentencePair(sp);
      System.out.println("Alignment:");
      System.out.println(aRv);
      int lastF = 0;
      for (int i = 0; i < targets.get(0).size(); i++) {
        List<Integer> e2fA = aRv.getAlignmentsToEnglish(i);
        int sureAlignment = -1;
        for (Integer a : e2fA) {
          if (sureAlignment == -1 ||
                  (Math.abs(sureAlignment - lastF)  > Math.abs(a - lastF) &&
                          waHyp.sourceCoverage.get(a) == false)) {
            sureAlignment = a;
          }
            /* if (aRv.containsSureAlignment(i, a)) {
              sureAlignment = a;
              break;
            } */
        }
        if (DEBUG) {
          System.out.printf("e.%d -> f.%d\n", i, sureAlignment);
        }
        CoverageSet sourceCoverage = new CoverageSet();
        TranslationOption<IString> fakeOpt;
        if (sureAlignment == -1) {
          fakeOpt =
                  new TranslationOption<IString>(
                          new float[0], new String[0],
                          new RawSequence<IString>(new IString[]{targets.get(0).get(i)}),
                          new RawSequence<IString>(new IString[]{new IString("")}), null);
        } else {
          lastF = sureAlignment;
          fakeOpt =
                  new TranslationOption<IString>(
                          new float[0], new String[0],
                          new RawSequence<IString>(new IString[]{targets.get(0).get(i)}),
                          new RawSequence<IString>(new IString[]{source.get(sureAlignment)}), null);
          sourceCoverage.set(sureAlignment);
        }
        ConcreteTranslationOption<IString,FV> fakeConcreteOpt =
                new ConcreteTranslationOption<IString,FV>(
                        fakeOpt,
                        sourceCoverage,
                        featurizer, scorer,
                        source, "forcedAlignment", 0);

        waHyp = new Hypothesis<IString, FV>(sourceInputId,
                fakeConcreteOpt, waHyp.length, waHyp, featurizer, scorer, heuristic);
        if (DEBUG) {
          System.out.printf("new waHyp: %s\n", waHyp.featurizable.targetPrefix);
          System.out.printf("Coverage: %s\n", waHyp.sourceCoverage);
        }

      }
    }
    agenda.clear();
    if (DEBUG) {
      if (waHyp.featurizable != null) {
        System.out.printf("waHyp: %s\n", waHyp.featurizable.targetPrefix);
        System.out.printf("Coverage: %s\n", waHyp.sourceCoverage);
      } else {
        System.out.printf("waHyp: null hyp\n");
      }
    }

    agenda.add(waHyp);
    List<Hypothesis<IString, FV>> predictions = new ArrayList<Hypothesis<IString,FV>>(PREDICTIONS);
    do {
      Hypothesis<IString, FV> hyp = agenda.remove();
      if (DEBUG) {
        System.err.printf("[pred loop] Removing hyp from agenda: %s\n", hyp);
      }
      int firstCoverageGap = hyp.sourceCoverage.nextClearBit(0);
      for (int startPos = firstCoverageGap; startPos < sourceSz; startPos++) {
        int endPosMax = hyp.sourceCoverage.nextSetBit(startPos);

        // check distortion limit
        if (endPosMax < 0) {
          if (maxDistortion >= 0 && startPos != firstCoverageGap) {
            endPosMax = Math.min(firstCoverageGap + maxDistortion + 1,
                    sourceSz);
          } else {
            endPosMax = sourceSz;
          }
        }
        for (int endPos = startPos; endPos < endPosMax; endPos++) {
          // use *UNFILTERED* options for prefix hypothesis expansion predictions
          List<ConcreteTranslationOption<IString,FV>> applicableOptions = optionGrid
                  .get(startPos, endPos);
          for (ConcreteTranslationOption<IString,FV> option : applicableOptions) {
            if (option.abstractOption.source.equals(option.abstractOption.target)) {
              if (DEBUG) {
                System.err.println("ignoring option since source phrase == target phrase");
                System.err.printf("'%s'='%s'\n", option.abstractOption.source, option.abstractOption.target);
              }
              continue;
            }
            Hypothesis<IString, FV> newHyp = new Hypothesis<IString, FV>(sourceInputId,
                    option, hyp.length, hyp, featurizer, scorer, heuristic);
            if (DEBUG) {
              System.out.printf("constructed new hyp: %s\n", newHyp);
            }
            predictions.add(newHyp);
            agenda.add(newHyp);
          }
        }
      }
    } while (predictions.size() < PREDICTIONS && agenda.size() > 0);

    List<RichTranslation<IString, FV>> nbest = new ArrayList<RichTranslation<IString,FV>>(predictions.size());
    for (Hypothesis<IString,FV> hyp : predictions) {
      nbest.add(new RichTranslation<IString, FV>(hyp.featurizable, hyp.finalScoreEstimate(), null));
    }

    ///System.err.println("Alignments\n==========");
    for (int i = 0; i < Math.min(10, predictions.size()); i++) {
      //System.err.printf("Hypothesis: %d (score: %f)\n", i, predictions.get(i).score);
      List<String> alignments = new LinkedList<String>();
      for (Hypothesis<IString, FV> hyp = predictions.get(i); hyp.featurizable != null; hyp = hyp.preceedingHyp) {
        alignments.add(String.format("f:'%s' => e: '%s' [%s]", hyp.featurizable.sourcePhrase,
                hyp.featurizable.targetPhrase, Arrays.toString(hyp.translationOpt.abstractOption.scores)));
      }
      Collections.reverse(alignments);
    /*  for (String alignment : alignments) {
         System.err.print("   ");
         System.err.println(alignment);
      } */
    }

    return nbest;
  }

  public static final int PREFIX_ALIGNMENTS = 100;
  public static final int PREDICTIONS = 100;

  @Override
  public List<RichTranslation<IString, FV>> nbest(Sequence<IString> source,
                                                  int translationId, ConstrainedOutputSpace<IString, FV> constrainedOutputSpace,
                                                  List<Sequence<IString>> targets, int size) {
    return nbest(scorer, source, translationId, constrainedOutputSpace, targets, size);
  }

}

