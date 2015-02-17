package edu.stanford.nlp.mt.service.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.stanford.nlp.mt.service.Messages.Request;

/**
 * A catch-all handler for unparseable client requests.
 * 
 * @author Spence Green
 *
 */
public class UnknownRequestHandler implements RequestHandler {

  @Override
  public ServiceResponse handle(Request request) {
    return null;
  }

  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {}

  @Override
  public boolean validate(Request request) {
    return false;
  }
}
