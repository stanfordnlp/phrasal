package edu.stanford.nlp.mt.tools.service.handlers;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.tools.service.Messages.TranslationRequest;
import edu.stanford.nlp.mt.tools.service.Messages.BaseReply;
import edu.stanford.nlp.mt.tools.service.Messages.Request;
import edu.stanford.nlp.mt.tools.service.PhrasalLogger.LogName;
import edu.stanford.nlp.mt.tools.service.PhrasalLogger;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;

/**
 * Message handler for the TranslationRequest message.
 * 
 * @author Spence Green
 *
 */
public class TranslationRequestHandler implements RequestHandler {

  private final Logger logger;
  
  
  public TranslationRequestHandler(String phrasalIniName) {
    logger = Logger.getLogger(TranslationRequestHandler.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);
    
    // Setup a threadpool for phrasal that wraps the servlet internals
    // needed to restart the request after processing.
    
    // Start a consumer thread for the results
  }

  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    
 
  }


  @Override
  public ServiceResponse handle(Request request) {
    throw new UnsupportedOperationException("This is an asynchronous handler");
  }

  private static class ResultConsumer extends Thread {
    
    public ResultConsumer(MulticoreWrapper threadpool) {
      // Do something
    }
    
    @Override
    public void run() {
      // TODO(spenceg)
    }
  }
  
}
