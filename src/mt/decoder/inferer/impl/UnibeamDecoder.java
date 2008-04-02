package mt.decoder.inferer.impl;

import java.util.*;

import mt.base.*;
import mt.decoder.inferer.*;
import mt.decoder.recomb.*;
import mt.decoder.util.*;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class UnibeamDecoder<TK, FV> extends AbstractBeamInferer<TK, FV> {
	// class level constants
	public static final int DEFAULT_BEAM_SIZE = 2000;
	public static final HypothesisBeamFactory.BeamType DEFAULT_BEAM_TYPE = HypothesisBeamFactory.BeamType.treebeam;
	public static final int DEFAULT_MAX_DISTORTION = 4;
	
	int maxDistortion = DEFAULT_MAX_DISTORTION;
	 	
	static public <TK,FV> UnibeamDecoderBuilder<TK,FV> builder() {
		return new UnibeamDecoderBuilder<TK,FV>();
	}
	
	protected UnibeamDecoder(UnibeamDecoderBuilder<TK, FV> builder) {
		super(builder);
	}
	
	public static class UnibeamDecoderBuilder<TK,FV> extends AbstractBeamInfererBuilder<TK,FV> {
		
		public UnibeamDecoderBuilder() {
			super(DEFAULT_BEAM_SIZE, DEFAULT_BEAM_TYPE);
		}

		@Override
		public Inferer<TK, FV> build() {
			return new UnibeamDecoder<TK,FV>(this);
		}
	}
	
	@Override
	protected Beam<Hypothesis<TK,FV>> decode(Sequence<TK> foreign, int translationId, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int nbest) {
		
		if (constrainedOutputSpace != null) {
			throw new UnsupportedOperationException();
		}
		
		// create beams
		Beam<Hypothesis<TK,FV>> beam = HypothesisBeamFactory.factory(beamType, filter, beamCapacity, recombinationHistory);
		Beam<Hypothesis<TK,FV>> closedBeam = HypothesisBeamFactory.factory(beamType, new NoRecombination<Hypothesis<TK, FV>>(), nbest, recombinationHistory);
		
		// retrieve translation options
		List<ConcreteTranslationOption<TK>> options = phraseGenerator.translationOptions(foreign, translationId);
		
		Hypothesis<TK,FV> nullHyp = new Hypothesis<TK,FV>(translationId, foreign, heuristic, options);
		beam.put(nullHyp);
		
		for (Hypothesis<TK,FV> hyp; (hyp = beam.remove()) != null; ) {
			if (hyp.isDone()) {
				closedBeam.put(hyp);
				if (closedBeam.size() >= nbest) break;
			}
			for (ConcreteTranslationOption<TK> option : options) {
				if (hyp.foreignCoverage.intersects(option.foreignCoverage)) {
					continue; 
				}
				int distortion = (hyp.translationOpt == null ? option.foreignPos : hyp.translationOpt.linearDistortion(option));
				
				if (Math.abs(distortion) > maxDistortion) {
					continue;
				}					
				Hypothesis<TK,FV> newHyp = new Hypothesis<TK,FV>(translationId, option, hyp.length, hyp, featurizer, scorer, heuristic);
				beam.put(newHyp);
			}
		}
		
		if (closedBeam.size() == 0) throw new RuntimeException("Decoder failure");
		
		return closedBeam;
	}
}
