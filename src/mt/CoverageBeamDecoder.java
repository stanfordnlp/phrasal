package mt;

import java.util.List;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class CoverageBeamDecoder<TK, FV> extends AbstractBeamInferer<TK, FV> {
	static public final String DEBUG_PROPERTY = "CoverageBeamDecoderDebug";
	static public final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	static public final int DEFAULT_BEAM_SIZE = 20;
	public static final HypothesisBeamFactory.BeamType DEFAULT_BEAM_TYPE = HypothesisBeamFactory.BeamType.treebeam;
	public static final int DEFAULT_MAX_DISTORTION = 3;
	
	int maxDistortion = DEFAULT_MAX_DISTORTION;
	
	static public <TK,FV> CoverageBeamDecoderBuilder<TK,FV> builder() {
		return new CoverageBeamDecoderBuilder<TK,FV>();
	}
	
	protected CoverageBeamDecoder(CoverageBeamDecoderBuilder<TK, FV> builder) {
		super(builder);
	}
	
	public static class CoverageBeamDecoderBuilder<TK,FV> extends AbstractBeamInfererBuilder<TK,FV> {	
		public CoverageBeamDecoderBuilder() {
			super(DEFAULT_BEAM_SIZE, DEFAULT_BEAM_TYPE);
		}

		@Override
		public Inferer<TK, FV> build() {
			return new CoverageBeamDecoder<TK,FV>(this);
		}
	}

	@Override
	protected Beam<Hypothesis<TK, FV>> decode(Sequence<TK> foreign, int translationId, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int nbest) {
		
		if (constrainedOutputSpace != null) {
			throw new UnsupportedOperationException();
		}
		
		// create beams
		if (DEBUG) System.err.println("Creating beams");
		CoverageBeams beams = new CoverageBeams(foreign.size(),recombinationHistory);
		Beam<Hypothesis<TK,FV>>[] closedBeams = createBeamsForCoverageCounts(foreign.size()+1, nbest, new NoRecombination<Hypothesis<TK, FV>>(), recombinationHistory);
		
		// retrieve translation options
		if (DEBUG) System.err.println("Generating Translation Options");
		List<ConcreteTranslationOption<TK>> options = phraseGenerator.translationOptions(foreign, translationId);
		System.err.printf("Translation options: %d foreign size %d\n", options.size(), foreign.size());
		
		// insert initial hypothesis
		Hypothesis<TK,FV> nullHyp = new Hypothesis<TK,FV>(translationId, foreign, heuristic, options);
		beams.put(nullHyp);
		
		// main translation loop
		if (DEBUG) System.err.println("CoverageBeamDecorder translating loop");
		for (int coverageCount = 0, maxCoverage = 0; coverageCount <= foreign.size(); coverageCount++) {
			List<Hypothesis<TK,FV>> hypothesisList = beams.getHypotheses(coverageCount);
			System.err.printf("Doing coverage count: %d Hypotheses: %d\n", coverageCount, hypothesisList.size());
			List<ConcreteTranslationOption<TK>> applicableOptions = ConcreteTranslationOptions.filterOptions(HypothesisBeams.coverageIntersection(hypothesisList), foreign.size(), options);
			if (DEBUG) System.err.printf("Entries: %d Filtered Options: %d (from %d)\n", hypothesisList.size(), applicableOptions.size(), options.size() );
			
			for (Hypothesis<TK,FV> hyp : hypothesisList) {
				int optionsApplied = 0;
				// for each translation option
				for (ConcreteTranslationOption<TK> option : applicableOptions) {
					if (hyp.foreignCoverage.intersects(option.foreignCoverage)) continue; 
					
					int distortion = (hyp.translationOpt == null ? option.foreignPos : hyp.translationOpt.linearDistortion(option));
					
					if (Math.abs(distortion) > maxDistortion) continue;
			
					Hypothesis<TK,FV> newHyp = new Hypothesis<TK,FV>(translationId, option, hyp.length, hyp, featurizer, scorer, heuristic);
					
					int foreignWordsCovered = newHyp.foreignCoverage.cardinality();
					if (newHyp.isDone()) {
						closedBeams[foreignWordsCovered].put(newHyp); 
					} else {
						if (foreignWordsCovered > maxCoverage) {
							maxCoverage = foreignWordsCovered;
							System.err.printf("New maximum foreign coverage: %d\n", maxCoverage);
						}
						beams.put(newHyp);
					}
					optionsApplied++;
				}
				if (optionsApplied == 0) {
					closedBeams[hyp.foreignCoverage.cardinality()].put(hyp);
				}
			}
		}
		
		if (DEBUG) System.err.println("Main translation loop done");
		
		for (int i = closedBeams.length -1; i >= 0; i--) {
			if (closedBeams[i].size() != 0) return closedBeams[i];
		} 
		
		throw new RuntimeException("Decoder failure");
	}
}
