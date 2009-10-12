package mt.base;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import mt.decoder.feat.IsolatedPhraseFeaturizer;
import mt.decoder.util.Scorer;

abstract public class DTUTable<FV> extends PharaohPhraseTable<FV> {

	public static final String GAP_STR = "@@GAP@@";

	public DTUTable(IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer, Scorer<FV> scorer, String filename) throws IOException {
		super(phraseFeaturizer, scorer, filename);
	}

	// TODO: rewrite
  @Override
	public List<ConcreteTranslationOption<IString>> translationOptions(Sequence<IString> sequence, List<Sequence<IString>> targets, int translationId) {
		List<ConcreteTranslationOption<IString>> opts = new LinkedList<ConcreteTranslationOption<IString>>();
		int sequenceSz = sequence.size();
		int longestForeignPhrase = this.longestForeignPhrase();
		if (longestForeignPhrase < 0) longestForeignPhrase = -longestForeignPhrase;
		for (int startIdx = 0; startIdx < sequenceSz; startIdx++) {
			for (int len = 1; len <= longestForeignPhrase; len++) {
				int endIdx = startIdx+len;					
				if (endIdx > sequenceSz) break;
				CoverageSet foreignCoverage = new CoverageSet(sequenceSz);
				foreignCoverage.set(startIdx, endIdx);
				Sequence<IString>foreignPhrase = sequence.subsequence(startIdx, endIdx);
        List<TranslationOption<IString>> abstractOpts = this.getTranslationOptions(foreignPhrase);
				if (abstractOpts == null) continue;
				for (TranslationOption<IString> abstractOpt : abstractOpts) {
					opts.add(new ConcreteTranslationOption<IString>(abstractOpt, foreignCoverage, phraseFeaturizer, scorer, sequence, this.getName(), translationId));
				}
			}
		}
		return opts;
  }

	// TODO: rewrite
	@Override
	public List<TranslationOption<IString>> getTranslationOptions(Sequence<IString> foreignSequence) {
		RawSequence<IString> rawForeign = new RawSequence<IString>(foreignSequence);
		int[] foreignInts = Sequences.toIntArray(foreignSequence, IString.identityIndex());
		int fIndex = foreignIndex.indexOf(foreignInts);
		if (fIndex == -1) return null;
		List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
		List<TranslationOption<IString>> transOpts = new ArrayList<TranslationOption<IString>>(intTransOpts.size());
		//for (int i = 0; i < intTransOptsSize; i++) {
		for (IntArrayTranslationOption intTransOpt : intTransOpts) {
			RawSequence<IString> translation = new RawSequence<IString>(intTransOpt.translation, 
					IString.identityIndex());
			transOpts.add( 
					new TranslationOption<IString>(intTransOpt.scores, scoreNames,
							translation,
							rawForeign, intTransOpt.alignment));
    }
		return transOpts;
	}
}
