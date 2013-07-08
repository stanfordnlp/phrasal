package edu.stanford.nlp.mt.service.handlers;

import java.io.IOException;
import java.lang.reflect.Type;

import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import edu.stanford.nlp.mt.service.Messages.Reply;


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
  
  public void writeInto(HttpServletResponse response) throws IOException {
    String responseString = this.toString();
    response.getWriter().println(responseString);
    response.setContentType("application/x-javascript;charset=utf-8");     
    response.setStatus(HttpServletResponse.SC_OK);
  }
}
