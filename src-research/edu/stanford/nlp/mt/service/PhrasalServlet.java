package edu.stanford.nlp.mt.service;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.service.Messages.MessageType;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.PhrasalLogger.LogName;
import edu.stanford.nlp.mt.service.handlers.RequestHandler;
import edu.stanford.nlp.mt.service.handlers.RuleQueryRequestHandler;
import edu.stanford.nlp.mt.service.handlers.RuleQueryRequestHandlerMock;
import edu.stanford.nlp.mt.service.handlers.ServiceResponse;
import edu.stanford.nlp.mt.service.handlers.TranslationRequestHandler;
import edu.stanford.nlp.mt.service.handlers.TranslationRequestHandlerMock;
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

  private Phrasal decoder;

  public PhrasalServlet() {
    this(null);
  }
  
  public PhrasalServlet(String phrasalIniName){
    logger = Logger.getLogger(PhrasalServlet.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);

    boolean debugMode = phrasalIniName == null;

    if (!debugMode) {
      try {
        decoder = Phrasal.loadDecoder(phrasalIniName);
      } catch (IOException e) {
        logger.severe("Unable to load phrasal from: " + phrasalIniName);
        RuntimeException re = new RuntimeException();
        re.initCause(e);
        throw re;
      }
      logger.info("Loaded phrasal from: " + phrasalIniName);
    }
    
    requestHandlers = loadHandlers(debugMode);
  }
  
  /**
   * Setup request handlers.
   * 
   * @param loadMock
   * @return
   */
  private RequestHandler[] loadHandlers(boolean loadMock) {
    RequestHandler[] handlers = new RequestHandler[MessageType.values().length];
    for (MessageType type : MessageType.values()) {
      if (type == MessageType.TRANSLATION_REQUEST) {
        handlers[type.ordinal()] = loadMock ? new TranslationRequestHandlerMock() :
           new TranslationRequestHandler(decoder);
      } else if (type == MessageType.RULE_QUERY_REQUEST) {
        handlers[type.ordinal()] = loadMock ? new RuleQueryRequestHandlerMock() :
          new RuleQueryRequestHandler(decoder.getPhraseTable(), decoder.getScorer(0));
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
