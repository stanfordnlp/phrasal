package edu.stanford.nlp.mt.tools.service;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.gson.Gson;

import edu.stanford.nlp.util.Pair;

/**
 * Serializable messages supported by the translation web service.
 * 
 * @author Spence Green
 *
 */
public final class Messages {
  
  // Supported languages in iso-639-1 format
  // TODO(spenceg) Make this more robust.
  public static enum Language {EN,AR,ZH,DE,FR};
  
  private static final Gson gson = new Gson();
  
  private Messages() {}
  
  public static enum MessageType {
    // Requests
    TRANSLATION_REQUEST("translationRequest", TranslationRequest.class),
    
    // Responses
    BASE_REPLY("baseReply", BaseReply.class),
    
    // Error
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
        return messageType;
      }
    }
    return MessageType.UNKNOWN;
  }
  
  @SuppressWarnings("unchecked")
  public static Pair<MessageType,Request>requestToMessage(HttpServletRequest request) {
    MessageType type = getMessageType(request);
    Request message = null;
    if (type != MessageType.UNKNOWN) {
      String jsonString = request.getParameter(type.keyName());
      System.err.println("JSON: " + jsonString);
      message = (Request) gson.fromJson(jsonString, type.msgClass());
    }
    return new Pair<MessageType,Request>(type, message);
  }
  
  /**********************************************
   * Request classes
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
    // The id field *must* correspond to the ordinal()
    // method of the associate MessageType.
    protected transient int id;
    public Request(Language sourceLang, Language targetLang, String source) {
      this.src = sourceLang;
      this.tgt = targetLang;
      this.text = source;
    }
    
    // Should this request be handled asynchronously?
    public abstract boolean isAsynchronous();
  }
  
  public static class TranslationRequest extends Request {
    // The number of translations to generate
    public final int n;
    public TranslationRequest(Language sourceLang, Language targetLang, String source, int n) {
      super(sourceLang, targetLang, source);
      this.n = n;
      this.id = MessageType.TRANSLATION_REQUEST.ordinal();
    }
    @Override
    public boolean isAsynchronous() {
      return true;
    }
    @Override
    public String toString() {
      if (src == null) System.err.println("Src");
      if (tgt == null) System.err.println("Tgt");
      if (text == null) System.err.println("Text");
      
      return String.format("[%s-%s (%d) %s]", src.toString(), tgt.toString(), n, text);
    }
  }
  
  /**********************************************
   * Response classes
   **********************************************/
  
  /**
   * 
   * @author Spence Green
   *
   */
  public static abstract class Reply {
    public final List<String> tgtList;
    public Reply(List<String> targets) {
      this.tgtList = targets;
    }
  }
  
  public static class BaseReply extends Reply {
    public final List<String> alignments;
    public BaseReply(List<String> targets, List<String> alignments) {
      super(targets);
      this.alignments = alignments;
    }
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      String nl = System.getProperty("line.separator");
      for (int i = 0; i < tgtList.size(); ++i) {
        sb.append(tgtList.get(i)).append("\t").append(alignments.get(i)).append(nl);
      }
      return sb.toString();
    }
  }

}
