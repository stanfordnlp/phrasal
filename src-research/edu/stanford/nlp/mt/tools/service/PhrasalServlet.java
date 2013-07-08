package edu.stanford.nlp.mt.tools.service;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.stanford.nlp.mt.tools.service.Messages.MessageType;
import edu.stanford.nlp.mt.tools.service.Messages.Request;
import edu.stanford.nlp.mt.tools.service.PhrasalLogger.LogName;
import edu.stanford.nlp.mt.tools.service.handlers.RequestHandler;
import edu.stanford.nlp.mt.tools.service.handlers.ServiceResponse;
import edu.stanford.nlp.mt.tools.service.handlers.TranslationRequestHandler;
import edu.stanford.nlp.mt.tools.service.handlers.TranslationRequestHandlerMock;
import edu.stanford.nlp.util.Pair;

/**
 * Servlet that loads Phrasal as a private member.
 *
 * TODO:
 *  PT query message
 *  Unknown message handler
 *  Pre-processing of input
 *  Post-processing of output
 *  Do we need to do forced word alignment?
 *  Add statistics about decoding time.
 *  Graceful exception handling. This servlet can't ever crash....
 * 
 * @author Spence Green
 *
 */
public class PhrasalServlet extends HttpServlet {

  private static final long serialVersionUID = -2229782317949182871L;
  
  public static final String ASYNC_KEY = "as_result";
  
  private final RequestHandler[] requestHandlers;

  private final Logger logger;

  public PhrasalServlet() {
    this(null);
  }
  
  public PhrasalServlet(String phrasalIniName){
    logger = Logger.getLogger(PhrasalServlet.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);

    boolean debugMode = phrasalIniName == null;
    
    requestHandlers = loadHandlers(debugMode, phrasalIniName);
  }
  
  /**
   * Setup request handlers.
   * 
   * @param loadMock
   * @param phrasalIniName
   * @return
   */
  private RequestHandler[] loadHandlers(boolean loadMock, String phrasalIniName) {
    RequestHandler[] handlers = new RequestHandler[MessageType.values().length];
    for (MessageType type : MessageType.values()) {
      if (type == MessageType.TRANSLATION_REQUEST) {
        handlers[type.ordinal()] = loadMock ? new TranslationRequestHandlerMock() :
           new TranslationRequestHandler(phrasalIniName);
      }
      // TODO(spenceg): Add more handlers
    }
    return handlers;
  }
  
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    // Parse the message
    Pair<MessageType,Request> message = Messages.parseRequest(request);
    MessageType messageType = message.first();
    Request baseRequest = message.second();
    logger.info(String.format("Recv: %s %s", messageType.toString(), baseRequest.toString()));

    Object result = request.getAttribute(ASYNC_KEY);
    if (result == null && baseRequest.isAsynchronous()) {
      // Asynchronous request that will be suspended by the handler
      requestHandlers[messageType.ordinal()].handleAsynchronous(baseRequest, request, response);

    } else {
      // Synchronous message
      ServiceResponse r = result == null ? requestHandlers[messageType.ordinal()].handle(baseRequest) :
        (ServiceResponse) result;
      try {
        r.writeInto(response);
        logger.info(String.format("Send: %s", r.getReply().toString()));
      } catch (IOException e) {
        logger.warning("Unable to serialize response for: " + response.toString());
      }      
    }
  }
}
