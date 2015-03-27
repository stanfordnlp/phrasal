package edu.stanford.nlp.mt.service;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.decoder.util.SparseScorer;
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
 * 
 * @author Spence Green
 *
 */
public class PhrasalServlet extends HttpServlet {

  private static final long serialVersionUID = -2229782317949182871L;

  private static final Logger logger = LogManager.getLogger(PhrasalServlet.class.getName());
  
  public static final String ASYNC_KEY = "As#R";
  
  // Time in ms that an asynchronous response can be suspended.
  private static final long ASYNC_TIMEOUT = 30000;

  private final RequestHandler[] requestHandlers;
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
    boolean debugMode = (phrasalIniName == null);

    if (!debugMode) {
      try {
        decoder = Phrasal.loadDecoder(phrasalIniName);
      } catch (IOException e) {
        logger.fatal("Unable to load phrasal from: " + phrasalIniName, e);
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
          new RuleQueryRequestHandler(decoder.getPhraseTable(), new SparseScorer(decoder.getModel()),
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
    logger.info("Receive: {} {}", messageType, baseRequest);
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
        logger.warn("Bad asynchronous request received: {}", request);
      }

    } else if (continuation.isExpired()) {
      // Asynchronous request timed out
      ServiceResponse.writeTimeout(response);
      logger.warn("Request timeout: {}", request);
      
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
        logger.warn("Bad synchronous request received: {}", request);
      }
    }

    // If a ServiceResponse exists, the convert it to HttpServletResponse
    if (serviceResponse != null) {
      boolean responseOk = ServiceResponse.intoHttpResponse(serviceResponse, response);
      if (responseOk) {
        logger.info("Response status {}: {}", response.getStatus(), 
            serviceResponse.getReply());
      } else {
        String status = String.format("Response status %d: %s", response.getStatus(), 
            serviceResponse.getReply().toString());
        logger.error(status);
      }
    }
  }
}
