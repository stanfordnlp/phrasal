package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.sparse.CompRowMatrix;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.MosesNBestList;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.SparseFeatureValueCollection;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.tune.NBestOptimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.svd.ReducedSVD;
import edu.stanford.nlp.util.Ptr;

/**
 * @author danielcer
 */
public class SVDReducedObj extends AbstractNBestOptimizer {

  public enum SVDOptChoices {
    exact, evalue
  }

  static Ptr<DenseMatrix> pU = null;
  static Ptr<DenseMatrix> pV = null;

  int rank;
  SVDOptChoices opt;

  public SVDReducedObj(MERT mert, int rank, SVDOptChoices opt) {
    super(mert);
    this.rank = rank;
    this.opt = opt;
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    Ptr<Matrix> pFeatDocMat = new Ptr<Matrix>();
    Ptr<Map<String, Integer>> pFeatureIdMap = new Ptr<Map<String, Integer>>();
    System.err.println("Creating feature document matrix");
    nbestListToFeatureDocumentMatrix(nbest, pFeatDocMat, pFeatureIdMap);

    if (pU == null) {
      pU = new Ptr<DenseMatrix>();
      pV = new Ptr<DenseMatrix>();
      System.err.printf("Doing SVD rank: %d\n", rank);
      ReducedSVD.svd(pFeatDocMat.deref(), pU, pV, rank, false);
      System.err.println("SVD done.");
    }

    Counter<String> reducedInitialWts = weightsToReducedWeights(initialWts,
        pU.deref(), pFeatureIdMap.deref());

    System.err.println("Initial Wts:");
    System.err.println("====================");
    System.err.println(Counters.toString(initialWts, 35));

    System.err.println("Reduced Initial Wts:");
    System.err.println("====================");
    System.err.println(Counters.toString(reducedInitialWts, 35));

    System.err.println("Recovered Reduced Initial Wts");
    System.err.println("=============================");
    Counter<String> recoveredInitialWts = reducedWeightsToWeights(
        reducedInitialWts, pU.deref(), pFeatureIdMap.deref());
    System.err.println(Counters.toString(recoveredInitialWts, 35));

    Counter<String> reducedWts;
    switch (opt) {
    case exact:
      System.err.println("Using exact MERT");
      NBestOptimizer opt = new KoehnStyleOptimizer(mert);
      reducedWts = opt.optimize(reducedInitialWts);
      break;
    case evalue:
      System.err.println("Using E(Eval) MERT");
      NBestOptimizer opt2 = new MCMCELossObjectiveCG(mert);
      reducedWts = opt2.optimize(reducedInitialWts);
      break;
    default:
      throw new UnsupportedOperationException();
    }
    System.err.println("Reduced Learned Wts:");
    System.err.println("====================");
    System.err.println(Counters.toString(reducedWts, 35));

    Counter<String> recoveredWts = reducedWeightsToWeights(reducedWts,
        pU.deref(), pFeatureIdMap.deref());
    System.err.println("Recovered Learned Wts:");
    System.err.println("======================");
    System.err.println(Counters.toString(recoveredWts, 35));

    double wtSsd = MERT.wtSsd(reducedInitialWts, reducedWts);
    System.out.printf("reduced wts ssd: %e\n", wtSsd);

    double twtSsd = MERT.wtSsd(initialWts, recoveredWts);
    System.out.printf("recovered wts ssd: %e\n", twtSsd);
    return recoveredWts;
  }

  static public Counter<String> weightsToReducedWeights(Counter<String> wts,
      Matrix reducedRepU, Map<String, Integer> featureIdMap) {

    Matrix vecWtsOrig = new DenseMatrix(featureIdMap.size(), 1);
    for (Map.Entry<String, Integer> entry : featureIdMap.entrySet()) {
      vecWtsOrig.set(entry.getValue(), 0, wts.getCount(entry.getKey()));
    }

    Matrix vecWtsReduced = new DenseMatrix(reducedRepU.numColumns(), 1);
    reducedRepU.transAmult(vecWtsOrig, vecWtsReduced);

    Counter<String> reducedWts = new ClassicCounter<String>();
    for (int i = 0; i < vecWtsReduced.numRows(); i++) {
      reducedWts.setCount((Integer.valueOf(i)).toString(),
          vecWtsReduced.get(i, 0));
    }

    return reducedWts;
  }

  static public void nbestListToFeatureDocumentMatrix(MosesNBestList nbest,
      Ptr<Matrix> pFeatDocMat, Ptr<Map<String, Integer>> pFeatureIdMap) {

    // build map from feature names to consecutive unique integer ids
    Map<String, Integer> featureIdMap = new HashMap<String, Integer>();
    for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
        .nbestLists()) {
      for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
        for (FeatureValue<String> fv : trans.features) {
          if (!featureIdMap.containsKey(fv.name)) {
            featureIdMap.put(fv.name, featureIdMap.size());
          }
        }
      }
    }

    // build list representation of feature document matrix
    List<List<Integer>> listFeatDocMapId = new ArrayList<List<Integer>>(
        featureIdMap.size());
    List<List<Double>> listFeatDocMapVal = new ArrayList<List<Double>>(
        featureIdMap.size());
    for (int i = 0; i < featureIdMap.size(); i++) {
      listFeatDocMapId.add(new ArrayList<Integer>());
      listFeatDocMapVal.add(new ArrayList<Double>());
    }

    int nbestId = -1;
    for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
        .nbestLists()) {
      for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
        nbestId++;
        for (FeatureValue<String> fv : trans.features) {
          int featureId = featureIdMap.get(fv.name);
          listFeatDocMapId.get(featureId).add(nbestId);
          listFeatDocMapVal.get(featureId).add(fv.value);
        }
      }
    }

    // prepare to create compressed row matrix
    int[][] nonZeros = new int[listFeatDocMapId.size()][];
    for (int i = 0; i < nonZeros.length; i++) {
      int[] row = new int[listFeatDocMapId.get(i).size()];
      for (int j = 0; j < row.length; j++) {
        row[j] = listFeatDocMapId.get(i).get(j);
      }
      nonZeros[i] = row;
    }

    Matrix featDocMat = new CompRowMatrix(nonZeros.length, nbestId + 1,
        nonZeros);
    for (int i = 0; i < nonZeros.length; i++) {
      for (int j = 0; j < nonZeros[i].length; j++) {
        featDocMat.set(i, nonZeros[i][j], listFeatDocMapVal.get(i).get(j));
      }
    }

    pFeatDocMat.set(featDocMat);
    pFeatureIdMap.set(featureIdMap);
  }

  static public Counter<String> reducedWeightsToWeights(
      Counter<String> reducedWts, Matrix reducedRepU,
      Map<String, Integer> featureIdMap) {

    int col = reducedRepU.numColumns();
    Vector vecReducedWts = new DenseVector(col);
    for (int i = 0; i < col; i++) {
      vecReducedWts
          .set(i, reducedWts.getCount((Integer.valueOf(i)).toString()));
    }

    Vector vecWts = new DenseVector(reducedRepU.numRows());
    reducedRepU.mult(vecReducedWts, vecWts);

    Counter<String> wts = new ClassicCounter<String>();
    for (Map.Entry<String, Integer> entry : featureIdMap.entrySet()) {
      wts.setCount(entry.getKey(), vecWts.get(entry.getValue()));
    }

    return wts;
  }

  static public MosesNBestList nbestListToDimReducedNbestList(
      MosesNBestList nbest, Matrix reducedRepV) {

    List<List<ScoredFeaturizedTranslation<IString, String>>> oldNbestLists = nbest
        .nbestLists();
    List<List<ScoredFeaturizedTranslation<IString, String>>> newNbestLists = new ArrayList<List<ScoredFeaturizedTranslation<IString, String>>>(
        oldNbestLists.size());

    int nbestId = -1;
    int numNewFeat = reducedRepV.numColumns();
    // System.err.printf("V rows: %d cols: %d\n", reducedRepV.numRows(),
    // reducedRepV.numColumns());
    for (List<ScoredFeaturizedTranslation<IString, String>> oldNbestlist : oldNbestLists) {
      List<ScoredFeaturizedTranslation<IString, String>> newNbestlist = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
          oldNbestlist.size());
      newNbestLists.add(newNbestlist);
      for (ScoredFeaturizedTranslation<IString, String> anOldNbestlist : oldNbestlist) {
        nbestId++;
        List<FeatureValue<String>> reducedFeatures = new ArrayList<FeatureValue<String>>(
            numNewFeat);
        for (int featId = 0; featId < numNewFeat; featId++) {
          // System.err.printf("%d:%d\n", featId, nbestId);
          reducedFeatures.add(new FeatureValue<String>(
              (Integer.valueOf(featId)).toString(), reducedRepV.get(nbestId,
                  featId)));
        }
        ScoredFeaturizedTranslation<IString, String> newTrans = new ScoredFeaturizedTranslation<IString, String>(
            anOldNbestlist.translation,
            new SparseFeatureValueCollection<String>(reducedFeatures,
                MERT.featureIndex), 0);
        newNbestlist.add(newTrans);
      }
    }

    return new MosesNBestList(newNbestLists, false);
  }
}