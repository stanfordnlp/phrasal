package mt;

import static java.lang.System.err;

import java.util.*;

/**
 * 
 * @author danielcer
 *
 */
public class GreedyDecoder<TK,FV> extends AbstractInferer<TK, FV> {
	
	@Override
	public List<RichTranslation<TK,FV>> nbest(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int size) {
		throw new UnsupportedOperationException();
	}

	@Override
	public RichTranslation<TK,FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace) {
		
		if (constrainedOutputSpace != null) {
			throw new UnsupportedOperationException();
		}
		
		List<ConcreteTranslationOption<TK>> options = phraseGenerator.translationOptions(foreign, translationId);
		Hypothesis<TK,FV> hyp = new Hypothesis<TK,FV>(translationId, foreign, heuristic, options);
		while (hyp.untranslatedTokens != 0) {
			Hypothesis<TK,FV> nextHyp = null;
			for (ConcreteTranslationOption<TK> option : options) {
				if (hyp.foreignCoverage.intersects(option.foreignCoverage)) {
					continue; 
				}
				Hypothesis<TK,FV> workingHyp = new Hypothesis<TK,FV>(translationId, option, hyp.length, hyp, featurizer, scorer, heuristic);
				if (nextHyp == null || nextHyp.finalScoreEstimate() < workingHyp.finalScoreEstimate()) {
					nextHyp = workingHyp;
				}
			}
			if (nextHyp == null) {
				// unable to cover all works in foreign sequence, just return what is available
				break;
			}
			
			//System.out.printf("nextHyp: %s\n", nextHyp.featurizable.partialTranslation);
			//System.out.printf("coverage: %s\n", nextHyp.foreignCoverage);
			
			hyp = nextHyp;
		}
		
		return new RichTranslation<TK,FV>(hyp.featurizable, hyp.score, collectFeatureValues(hyp));
	}
	
	/**
	 * 
	 * @param builder
	 */
	protected GreedyDecoder(AbstractInfererBuilder<TK, FV> builder) {
		super(builder);
		err.println("--------------------------------------------------------");
		err.println("Warning: Creating instance of GreedyDecoder.");
		err.println();
		err.println("This class primarily exists for diagnostic purposes.");
		err.println();
		err.println("Otherwise, you'll probably want to use something like");
		err.println("MultiBeamDecoder.");
		err.println("--------------------------------------------------------");
	}
	
	/**
	 * 
	 * @param <TK>
	 * @param <FV>
	 * @return
	 */
	static public <TK,FV> InfererBuilder<TK, FV> builder() {
		return new GreedyDecoderBuilder<TK,FV>();
	}
	
	/**
	 * 
	 * @author danielcer
	 *
	 * @param <TK>
	 * @param <FV>
	 */
	private static class GreedyDecoderBuilder<TK,FV> extends AbstractInfererBuilder<TK,FV> {
		@Override
		public GreedyDecoder<TK,FV> build() {
			return new GreedyDecoder<TK,FV>(this); 
		}	
	}
}

