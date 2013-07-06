package edu.stanford.nlp.mt.tools.service.handlers;

import java.lang.reflect.Type;
import java.util.List;
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
  
  public TranslationRequestHandler() {
    logger = Logger.getLogger(TranslationRequestHandler.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);
    
    // Setup a threadpool for phrasal that wraps the servlet internals
    // needed to restart the request after processing.
    
    // Start a consumer thread for the results
  }
  
  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void mockHandleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    
    // Jetty continuations
    // TODO(spenceg) Try Servlet 3.0 if this doesn't work
    Continuation continuation = ContinuationSupport.getContinuation(request);
    continuation.suspend(response); //Start Async Processing

    TranslationRequest translationRequest = (TranslationRequest) baseRequest;
    
    // Translate to uppercase!
    String translation = String.format("%s -> %s %s",  
        translationRequest.src.toString(),
        translationRequest.tgt.toString(),
        translationRequest.text.toUpperCase());
    List<String> translations = Generics.newLinkedList();
    translations.add(translation);
    List<String> alignments = Generics.newLinkedList();
    alignments.add("1-1 2-2 3-3 4-4");
    Type t = new TypeToken<BaseReply>() {}.getType();
    BaseReply baseResponse = new BaseReply(translations, alignments);

    // Simulate a long call to the MT system
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    ServiceResponse serviceResponse = new ServiceResponse(baseResponse, t);
    request.setAttribute("result", serviceResponse);   
    continuation.resume(); // Re-dispatch/ resume to generate response
  }


  @Override
  public ServiceResponse handle(Request request) {
    throw new UnsupportedOperationException("This is an asynchronous handler");
  }


  @Override
  public ServiceResponse mockHandle(Request request) {
    throw new UnsupportedOperationException();
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
