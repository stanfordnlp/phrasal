package edu.stanford.nlp.mt.service;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.gson.Gson;

import edu.stanford.nlp.mt.service.handlers.RuleQuery;
import edu.stanford.nlp.mt.service.handlers.TranslationQuery;
import edu.stanford.nlp.util.Pair;

/**
 * Serializable messages supported by the translation web service.
 * 
 * @author Spence Green
 *
 */
public final class Messages {
  
  // Supported languages in iso-639-1 format
  public static enum Language {UNK,AR,EN,DE,FR,ES};
  
  private static final Gson gson = new Gson();
  
  private Messages() {}
  
  @SuppressWarnings("rawtypes")
  public static enum MessageType {
    // Requests -- convention is camelcase letters followed by
    // the "Req" suffix.
    TRANSLATION_REQUEST("tReq", TranslationRequest.class),
    RULE_QUERY_REQUEST("rqReq", RuleQueryRequest.class),
    // Error catch-all request
    UNKNOWN_REQUEST("unkReq", null),
    
    // Responses -- convention is camelcase letters followed by
    // the "Rep" suffix.
    TRANSLATION_REPLY("tRep", TranslationReply.class),
    RULE_QUERY_REPLY("rqRep", RuleQueryReply.class);
        
    private final String keyName;
    private final Class msgClass;
    
    MessageType(String keyName, Class className) {
      this.keyName = keyName;
      this.msgClass = className;
    }
    
    public Class msgClass() { return msgClass; }
    public String keyName() { return keyName; }
  };
  
  private static MessageType getMessageType(HttpServletRequest request) {
    for (MessageType messageType : MessageType.values()) {
      String param = request.getParameter(messageType.keyName());
      if (param != null && param.length() > 0) {
        return messageType;
      }
    }
    return MessageType.UNKNOWN_REQUEST;
  }
  
  @SuppressWarnings("unchecked")
  public static Pair<MessageType,Request>parseRequest(HttpServletRequest request) {
    MessageType type = getMessageType(request);
    Request message = new UnknownRequest();
    if (type != MessageType.UNKNOWN_REQUEST) {
      String jsonString = request.getParameter(type.keyName());
      message = (Request) gson.fromJson(jsonString, type.msgClass());
    }
    return new Pair<MessageType,Request>(type, message);
  }
  
  /**********************************************
   * Request classes
   * 
   * These are received from the client and deserialized
   * by the servlet.
   * 
   **********************************************/
  
  /**
   * 
   * @author Spence Green
   *
   */
  public static abstract class Request {
    public final Language src;
    public final Language tgt;
    public final String text;
    public final String inputProperties;
    // The id field *must* correspond to the ordinal()
    // method of the associate MessageType.
    protected transient int id;
    public Request(Language sourceLang, Language targetLang, String source, String inputProperties) {
      this.src = sourceLang == null ? Language.UNK : sourceLang;
      this.tgt = targetLang == null ? Language.UNK : targetLang;
      this.text = source == null ? "" : source.trim();
      this.inputProperties = inputProperties == null ? "" : inputProperties;
    
      // Sanity checking
      assert src != null;
      assert tgt != null;
      assert text != null;
    }
    
    // Should this request be handled asynchronously?
    public abstract boolean isAsynchronous();
  }
  
  public static class TranslationRequest extends Request {
    // The number of translations to generate
    public final int n;
    public final String tgtPrefix;
    public TranslationRequest(Language sourceLang, Language targetLang, String source, String inputProps, int n, String tgtPrefix) {
      super(sourceLang, targetLang, source, inputProps);
      this.n = (n <= 0 || n > 50) ? 10 : n;
      this.id = MessageType.TRANSLATION_REQUEST.ordinal();
      this.tgtPrefix = tgtPrefix == null || tgtPrefix.length() == 0 ? "" : tgtPrefix.trim();
    
      // Sanity checking
      assert this.tgtPrefix != null;
    }
    @Override
    public boolean isAsynchronous() {
      return true;
    }
    @Override
    public String toString() {
      // Sanity checking
      assert src != null;
      assert tgt != null;
      assert text != null;

      return String.format("[%s-%s (%d) %s]", src.toString(), tgt.toString(), n, text);
    }
  }
  
  public static class RuleQueryRequest extends Request {
    public final int spanLimit;
    public final String leftContext;
    public RuleQueryRequest(Language sourceLang, Language targetLang,
        String source, String inputProps, int spanLimit, String leftContext) {
      super(sourceLang, targetLang, source, inputProps);
      this.spanLimit = (spanLimit <= 0 || spanLimit > 500) ? 10 : spanLimit;
      this.leftContext = leftContext;
      this.id = MessageType.RULE_QUERY_REQUEST.ordinal();
    }
    @Override
    public boolean isAsynchronous() {
      return false;
    }
    @Override
    public String toString() {
      return String.format("[%d]", this.spanLimit);
    }
  }
  
  public static class UnknownRequest extends Request {
    public UnknownRequest() {
      super(null, null, null, null);
    }

    @Override
    public boolean isAsynchronous() {
      return false;
    }
    @Override
    public String toString() {
      return "Unknown request";
    }
  }
  
  /**********************************************
   * Response classes
   * 
   * These are generated by the servlet.
   * 
   **********************************************/
  
  /**
   * 
   * @author Spence Green
   *
   */
  public static interface Reply {}
  
  public static class TranslationReply implements Reply {
    public final List<TranslationQuery> result;
    public TranslationReply(List<TranslationQuery> queryList) {
      result = queryList;
    }
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (TranslationQuery query : result) {
        sb.append(String.format("%.5f\t%s\t%s%n", query.score(), query.tgt, query.align));
      }
      if (sb.length() == 0) {
        sb.append("<< DECODER FAILURE >>");
      }
      return sb.toString();
    }
  }
  
  public static class RuleQueryReply implements Reply {
    public final List<RuleQuery> result;
    public RuleQueryReply(List<RuleQuery> ruleList) {
      this.result = ruleList;
    }
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (RuleQuery query : result) {
        if (sb.length() > 0) sb.append(" ");
        sb.append("[ ").append(query.toString()).append(" ]");
      }
      return sb.toString();
    }
  }
}
