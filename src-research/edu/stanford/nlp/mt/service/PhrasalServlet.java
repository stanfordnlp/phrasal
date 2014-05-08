package edu.stanford.nlp.mt.service;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.SystemLogger;
import edu.stanford.nlp.mt.base.SystemLogger.LogName;
import edu.stanford.nlp.mt.service.Messages.MessageType;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.handlers.RequestHandler;
import edu.stanford.nlp.mt.service.handlers.RuleQueryRequestHandler;
import edu.stanford.nlp.mt.service.handlers.RuleQueryRequestHandlerMock;
import edu.stanford.nlp.mt.service.handlers.ServiceResponse;
import edu.stanford.nlp.mt.service.handlers.TranslationRequestHandler;
import edu.stanford.nlp.mt.service.handlers.TranslationRequestHandlerMock;
import edu.stanford.nlp.mt.service.handlers.UnknownRequestHandler;
import edu.stanford.nlp.util.Pair;

/**
 * Servlet that loads Phrasal as a private member.
 *
 * TODO(spenceg):
 * 
 * @author Spence Green
 *
 */
public class PhrasalServlet extends HttpServlet {

  private static final long serialVersionUID = -2229782317949182871L;

  public static final String ASYNC_KEY = "As#R";
  
  // Time in ms that an asynchronous response can be suspended.
  private static final long ASYNC_TIMEOUT = 30000;

  private final RequestHandler[] requestHandlers;
  private final Logger logger;
  private Phrasal decoder;

  public PhrasalServlet() {
    this(null);
  }

  /**
   * Constructor.
   * 
   * @param phrasalIniName
   */
  public PhrasalServlet(String phrasalIniName){
    logger = Logger.getLogger(PhrasalServlet.class.getName());
    SystemLogger.attach(logger, LogName.SERVICE);

    boolean debugMode = (phrasalIniName == null);

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
   * @param phrasalIniName
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
          new RuleQueryRequestHandler(decoder.getPhraseTable(), decoder.getScorer(0),
              decoder.getPreprocessor(), decoder.getPostprocessor());

      } else if (type == MessageType.UNKNOWN_REQUEST) {
        handlers[type.ordinal()] = new UnknownRequestHandler();
      }
      // Add more request handlers here
    }
    return handlers;
  }

  /**
   * Handle HTTP GET requests.
   */
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    // Parse the message
    Pair<MessageType,Request> message = Messages.parseRequest(request);
    MessageType messageType = message.first();
    Request baseRequest = message.second();
    if (logger.getLevel() == Level.INFO) {
      // Expensive logging message, so wrap in a conditional
      logger.info(String.format("Receive: %s %s", messageType.toString(), baseRequest.toString()));
    }
    Continuation continuation = ContinuationSupport.getContinuation(request);
    
    // Asynchronous request that will be suspended by the handler
    ServiceResponse serviceResponse = null;
    if (baseRequest.isAsynchronous() && continuation.isInitial()) {
      continuation.setTimeout(ASYNC_TIMEOUT);
      RequestHandler handler = requestHandlers[messageType.ordinal()];
      if (handler.validate(baseRequest)) {
        handler.handleAsynchronous(baseRequest, request, response);

      } else {
        // Invalid asynchronous request. Do not suspend. Send the response immediately.
        ServiceResponse.writeBadRequest(response);
        logger.warning("Bad asynchronous request received: " + request.toString());
      }

    } else if (continuation.isExpired()) {
      // Asynchronous request timed out
      ServiceResponse.writeTimeout(response);
      logger.warning("Request timeout: " + request.toString());
      
    } else if (continuation.isResumed()) {
      // result will be non-null if this is an asynchronous request that has been dispatched
      // by the service after processing completed
      Object asyncResult = request.getAttribute(ASYNC_KEY);
      
      // First create a ServiceResponse
      serviceResponse = (ServiceResponse) asyncResult;

    } else {
      // Synchronous message
      RequestHandler handler = requestHandlers[messageType.ordinal()];
      if (handler.validate(baseRequest)) {
        serviceResponse = handler.handle(baseRequest);
      } else {
        ServiceResponse.writeBadRequest(response);
        logger.warning("Bad synchronous request received: " + request.toString());
      }
    }

    // If a ServiceResponse exists, the convert it to HttpServletResponse
    if (serviceResponse != null) {
      boolean responseOk = ServiceResponse.intoHttpResponse(serviceResponse, response);
      if (responseOk) {
        if (logger.getLevel() == Level.INFO) {
          // Expensive logging message, so wrap in a conditional
          String status = String.format("Response status %d: %s", response.getStatus(), 
              serviceResponse.getReply().toString());
          logger.info(status);
        }
      } else {
        String status = String.format("Response status %d: %s", response.getStatus(), 
            serviceResponse.getReply().toString());
        logger.severe(status);
      }
    }
  }
}
