package edu.stanford.nlp.mt.tools.aPTM;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.gson.Gson;

import edu.stanford.nlp.util.Pair;

public final class Messages {
  
  private Messages() {}
  
  public static enum MessageType {
    INIT("ptmInit", PTMInitRequest.class), 
    SEND_OOV("ptmOOV", PTMOOVRequest.class), 
    OOV("ptmOOV2", PTMOOVResponse.class), 
    PREDICTION("ptmPredict", PTMPredictionRequest.class), 
    DONE("ptmDone", PTMDoneRequest.class), 
    UNKNOWN("unkXXX", null);
    
    private final String keyName;
    @SuppressWarnings("rawtypes")
    private final Class msgClass;
    
    @SuppressWarnings("rawtypes")
    MessageType(String keyName, Class className) {
      this.keyName = keyName;
      this.msgClass = className;
    }
    @SuppressWarnings("rawtypes")
    public Class msgClass() { return msgClass; }
    public String keyName() { return keyName; }
  };
  
  public static MessageType getMessageType(HttpServletRequest request) {
    for (MessageType messageType : MessageType.values()) {
      String param = request.getParameter(messageType.keyName());
      if (param != null && param.length() > 0) {
        // System.err.printf("%s: %s\n", paramName, param);
        return messageType;
      }
    }
    return MessageType.UNKNOWN;
  }
  
  @SuppressWarnings("unchecked")
  public static Pair<MessageType,PTMBaseRequest>requestToMessage(HttpServletRequest request) {
    MessageType type = getMessageType(request);
    PTMBaseRequest message = null;
    if (type != MessageType.UNKNOWN) {
      Gson gson = new Gson();
      message = (PTMBaseRequest) gson.fromJson(request.getParameter(type.keyName()), type.msgClass());
    }
    return new Pair<MessageType,PTMBaseRequest>(type, message);
  }
  
  public static abstract class PTMBaseRequest {
    public final String sourceLang;
    public final String targetLang;
    public final String source;
    public PTMBaseRequest(String sourceLang, String targetLang, String source) {
      this.sourceLang = sourceLang;
      this.targetLang = targetLang;
      this.source = source;
    }
  }

  public static class PTMInitRequest extends PTMBaseRequest {
    public PTMInitRequest(String sourceLang, String targetLang, String source) {
      super(sourceLang, targetLang, source);
    }
  }

  public static class PTMOOVPhrasePair {
    public final String sourcePhrase;
    public final String targetPhrase;
    public PTMOOVPhrasePair(String sourcePhrase, String targetPhrase) {
      this.sourcePhrase = sourcePhrase;
      this.targetPhrase = targetPhrase;
    }
  }

  public static class PTMOOVRequest extends PTMBaseRequest {
    final List<PTMOOVPhrasePair> OOVPhrasePairs;
    public PTMOOVRequest(String sourceLang, String targetLang, String source, 
        List<PTMOOVPhrasePair> OOVPhrasePairs) {
      super(sourceLang, targetLang, source);
      this.OOVPhrasePairs = new ArrayList<PTMOOVPhrasePair>(OOVPhrasePairs);
    }
  }

  public static class PTMPredictionRequest extends PTMBaseRequest {
    public final String prefix;
    public int maxPredictions;
    public PTMPredictionRequest(String sourceLang, String targetLang,
        String source, String prefix, int maxPredictions) {
      super(sourceLang, targetLang, source);
      this.prefix = prefix;
      this.maxPredictions = maxPredictions;
    }
  }

  public static class PTMCompletionSelectionRequest extends PTMBaseRequest {
    public final String prefix;
    public final String completion;
    public PTMCompletionSelectionRequest(String sourceLang, String targetLang,
        String source, String prefix, String completion) {
      super(sourceLang, targetLang, source);
      this.prefix = prefix;
      this.completion = completion;
    }

  }

  public static class PTMDoneRequest extends PTMBaseRequest {
    public final String finishedTarget;
    public final int numKeyStrokes;

    public PTMDoneRequest(String sourceLang, String targetLang, String source, String finishedTarget, int numKeyStrokes) {
      super(sourceLang, targetLang, source);
      this.finishedTarget = finishedTarget;
      this.numKeyStrokes  = numKeyStrokes;
    }

    @Override 
    public String toString() {
      return String.format("ptmDone: sourceLang: %s targetLang: %s source: %s finishedTarget: %s numKeyStrokes: %d",
          sourceLang, targetLang, source, finishedTarget, numKeyStrokes);
    }
  }

  public static class PTMStatusOk {
    public final String status = "ok";
  }

  public static class PTMOOVResponse {
    final List<String> OOVs;
    public PTMOOVResponse(List<String> OOVs) {
      this.OOVs = new ArrayList<String>(OOVs);
    }
  }

  public static class PTMPredictionResponse {
    final String prefix;
    final List<Prediction> predictions;
    public PTMPredictionResponse(String prefix, List<Prediction> predictions) {
      this.prefix = prefix;
      this.predictions = predictions;
    }
  }

  public static class PTMError {
    public final String errorMsg;
    public PTMError(String errorMsg) {
      this.errorMsg = errorMsg;
    }
  }
}
