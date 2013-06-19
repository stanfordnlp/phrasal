package edu.stanford.nlp.mt.decoder.inferer;

import java.util.Map;

import edu.stanford.nlp.mt.base.FactoryUtil;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.inferer.impl.CubePruningDecoder;
import edu.stanford.nlp.mt.decoder.inferer.impl.DTUDecoder;
import edu.stanford.nlp.mt.decoder.inferer.impl.MultiBeamDecoder;
import edu.stanford.nlp.mt.decoder.util.HypothesisBeamFactory;

/**
 * Creates an approximate search procedure.
 * 
 * @author danielcer
 * @author Spence Green
 *
 */
public class InfererBuilderFactory {
  public static final String MULTIBEAM_DECODER = "multibeam";
  public static final String DTU_DECODER = "dtu";
  public static final String CUBE_PRUNING_DECODER = "cube";
//  public static final String DEFAULT_INFERER = MULTIBEAM_DECODER;
  public static final String DEFAULT_INFERER = CUBE_PRUNING_DECODER;
  public static final String BEAM_SIZE_OPT = "beamcapacity";
  public static final String BEAM_TYPE_OPT = "beamtype";


  public static InfererBuilder<IString, String> factory(String...infererSpecs) {
    String infererName = infererSpecs.length == 0 ? DEFAULT_INFERER : infererSpecs[0].toLowerCase();
    Map<String, String> paramPairs = FactoryUtil.getParamPairs(infererSpecs);

    HypothesisBeamFactory.BeamType beamType = null;
    String beamTypeStr = paramPairs.get(BEAM_TYPE_OPT);
    if (beamTypeStr != null) {
      beamType = Enum.valueOf(HypothesisBeamFactory.BeamType.class,
          beamTypeStr);
    }

    int beamSize = -1;
    String beamSizeStr = paramPairs.get(BEAM_SIZE_OPT);
    if (beamSizeStr != null) {
      try {
        beamSize = Integer.parseInt(beamSizeStr);
      } catch (NumberFormatException e) {
        throw new RuntimeException(
            String
            .format(
                "Error: given beam size, %s:%s, can not be converted into an integer value",
                BEAM_SIZE_OPT, beamSizeStr));
      }
    }

    if (infererName.equals(MULTIBEAM_DECODER)) {
      MultiBeamDecoder.MultiBeamDecoderBuilder<IString, String> builder = MultiBeamDecoder
          .builder();
      if (beamSize != -1)
        builder.setBeamCapacity(beamSize);
      if (beamType != null)
        builder.setBeamType(beamType);
      return builder;
    }
    
    if (infererName.equals(CUBE_PRUNING_DECODER)) {
      CubePruningDecoder.CubePruningDecoderBuilder<IString, String> builder = CubePruningDecoder
          .builder();
      if (beamSize != -1)
        builder.setBeamCapacity(beamSize);
      if (beamType != null)
        builder.setBeamType(beamType);
      return builder;
    }

    if (infererName.equals(DTU_DECODER)) {
      DTUDecoder.DTUDecoderBuilder<IString, String> builder = DTUDecoder
          .builder();
      if (beamSize != -1)
        builder.setBeamCapacity(beamSize);
      if (beamType != null)
        builder.setBeamType(beamType);
      return builder;
    }

    throw new RuntimeException(String.format("Unrecognized Inferer '%s'",
        infererName));
  }
}
