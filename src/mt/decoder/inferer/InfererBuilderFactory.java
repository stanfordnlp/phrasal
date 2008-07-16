package mt.decoder.inferer;

import java.util.*;

import mt.base.*;
import mt.decoder.inferer.impl.*;
import mt.decoder.util.*;

import edu.stanford.nlp.util.IString;

public class InfererBuilderFactory {
	public static final String GREEDY_DECODER = "greedy";
	public static final String MULTIBEAM_DECODER = "multibeam";
	public static final String COVERAGEBEAM_DECODER = "coveragebeam";
	public static final String UNIBEAM_DECODER = "unibeam";
	public static final String DEFAULT_INFERER = MULTIBEAM_DECODER;
	public static final String BEAM_SIZE_OPT = "beamcapacity";
	public static final String BEAM_TYPE_OPT = "beamtype";
	
	public static final Set<String> BEAM_INFERERS = new HashSet<String>();
	
	static {
		BEAM_INFERERS.add(UNIBEAM_DECODER);
		BEAM_INFERERS.add(MULTIBEAM_DECODER);
		BEAM_INFERERS.add(COVERAGEBEAM_DECODER);
	}

	public static InfererBuilder<IString,String> factory(String... infererSpecs) {
		String infererName;
		
		if (infererSpecs.length == 0) {
			infererName = DEFAULT_INFERER;
		} else {
			infererName = infererSpecs[0].toLowerCase();
		}
		
		Map<String,String> paramPairs = FactoryUtil.getParamPairs(infererSpecs);
		
		int beamSize = -1;
		HypothesisBeamFactory.BeamType beamType = null;
		
		if (BEAM_INFERERS.contains(infererName)) {
			String beamTypeStr = paramPairs.get(BEAM_TYPE_OPT);
			
			if (beamTypeStr != null) {
				beamType = Enum.valueOf(HypothesisBeamFactory.BeamType.class, beamTypeStr);
			}

			String beamSizeStr = paramPairs.get(BEAM_SIZE_OPT);
			
			if (beamSizeStr != null) {
				try {
					beamSize = Integer.parseInt(beamSizeStr);
				} catch (NumberFormatException e) {
					throw new RuntimeException(String.format( 
							"Error: given beam size, %s:%s, can not be converted into an integer value", BEAM_SIZE_OPT, beamSizeStr));
				}
			}
		}
		
		if (infererName.equals(GREEDY_DECODER)) {
			return GreedyDecoder.builder();
		} if (infererName.equals(UNIBEAM_DECODER)) {
			UnibeamDecoder.UnibeamDecoderBuilder<IString, String> builder = UnibeamDecoder.builder();
			if (beamSize != -1) builder.setBeamCapacity(beamSize);
			if (beamType != null) builder.setBeamType(beamType);
			return builder;
		} else if (infererName.equals(MULTIBEAM_DECODER)) {
			MultiBeamDecoder.MultiBeamDecoderBuilder<IString,String> builder = MultiBeamDecoder.builder();
			if (beamSize != -1) builder.setBeamCapacity(beamSize);
			if (beamType != null) builder.setBeamType(beamType);
			return builder;
		} else if (infererName.equals(COVERAGEBEAM_DECODER)) {
			CoverageBeamDecoder.CoverageBeamDecoderBuilder<IString, String> builder = CoverageBeamDecoder.builder();
			if (beamSize != -1) builder.setBeamCapacity(beamSize);
			if (beamType != null) builder.setBeamType(beamType);
			return builder;
		}
		
		throw new RuntimeException(String.format( 
				"Unrecognized Inferer '%s'", infererName));
	}

}
