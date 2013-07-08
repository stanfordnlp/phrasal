package edu.stanford.nlp.mt.tools.service.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.stanford.nlp.mt.tools.service.Messages.Request;
import edu.stanford.nlp.mt.tools.service.Messages.RuleQueryRequest;

/**
 * Mock handler for rule query requests.
 * 
 * @author Spence Green
 *
 */
public class RuleQueryRequestHandlerMock implements RequestHandler {

  @Override
  public ServiceResponse handle(Request request) {
    RuleQueryRequest ruleRequest = (RuleQueryRequest) request;
    
    return null;
  }

  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    throw new UnsupportedOperationException("Asynchronous call to synchronous handler");
  }

}
