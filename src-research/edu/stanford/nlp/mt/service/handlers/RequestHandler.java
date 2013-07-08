package edu.stanford.nlp.mt.service.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.stanford.nlp.mt.service.Messages.Request;

/**
 * SHOULD BE RE-ENTRANT!!
 * 
 * The constructor of the handler should take references to whatever system
 * components are needed to handle the request.
 * 
 * @author rayder441
 *
 */
public interface RequestHandler {

  /**
   * Receive a request and return a response in json format.
   * 
   * @param request 
   * @param threadId
   * 
   * @return a resonse in json format
   */
  public ServiceResponse handle(Request request);
  
  public void handleAsynchronous(Request baseRequest, HttpServletRequest request, HttpServletResponse response);
}
