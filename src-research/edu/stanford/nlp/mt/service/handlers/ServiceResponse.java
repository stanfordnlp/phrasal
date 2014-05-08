package edu.stanford.nlp.mt.service.handlers;

import java.io.IOException;
import java.lang.reflect.Type;

import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import edu.stanford.nlp.mt.service.Messages.Reply;

/**
 * Service-internal object that contains the result of a request.
 * 
 * @author Spence Green
 *
 */
public class ServiceResponse {
  
  private static final Gson gson = new Gson();
  
  private final Reply reply;
  private final Type type;
  
  public ServiceResponse(Reply response, Type type) {
    this.reply = response;
    this.type = type;
  }
  
  public Reply getReply() { return reply; }
  
  @Override
  public String toString() {
    return gson.toJson(reply, type);
  }
  
  public static boolean intoHttpResponse(ServiceResponse serviceResponse, HttpServletResponse response) {
    String responseString = serviceResponse.toString();
    try {
      response.setContentType("application/x-javascript;charset=utf-8");     
      response.setCharacterEncoding("UTF-8");
      response.getWriter().println(responseString);
      response.setStatus(HttpServletResponse.SC_OK);

    } catch (IOException e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return false;
    }
    return true;
  }
  
  /**
   * Write BAD_REQUEST (HTTP 400) into the response.
   * 
   * @param response
   */
  public static void writeBadRequest(HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }
  
  /**
   * Write INTERNAL_SERVER_ERROR (HTTP 500) into the response.
   * 
   * @param response
   */
  public static void writeTimeout(HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }
}
