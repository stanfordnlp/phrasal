package edu.stanford.nlp.mt.tools.service.handlers;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.tools.service.Messages.TranslationRequest;
import edu.stanford.nlp.mt.tools.service.Messages.BaseReply;
import edu.stanford.nlp.mt.tools.service.Messages.Request;
import edu.stanford.nlp.mt.tools.service.PhrasalLogger.LogName;
import edu.stanford.nlp.mt.tools.service.PhrasalLogger;
import edu.stanford.nlp.mt.tools.service.PhrasalServlet;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Message handler for the TranslationRequest message.
 * 
 * TODO(spenceg): Shutdown procedure for threadpool and consumer?
 * 
 * @author Spence Green
 *
 */
public class TranslationRequestHandler implements RequestHandler {

  private final Logger logger;
  
  private final Phrasal decoder;
  
  private final MulticoreWrapper<DecoderInput,Boolean> wrapper;
  
//  private final Thread resultConsumer;
  
  public TranslationRequestHandler(String phrasalIniName) {
    logger = Logger.getLogger(TranslationRequestHandler.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);
    
    try {
      decoder = Phrasal.loadDecoder(phrasalIniName);
    } catch (IOException e) {
      e.printStackTrace();
      logger.severe("Unable to load phrasal from: " + phrasalIniName);
      throw new RuntimeException();
    }
    logger.info("Loaded phrasal from: " + phrasalIniName);
    
    // Setup a threadpool for phrasal that wraps the servlet internals
    // needed to restart the request after processing.
    wrapper = 
        new MulticoreWrapper<DecoderInput,Boolean>(decoder.getNumThreads(), 
            new DecoderService(0, decoder), false);
    
    // Start a consumer thread for the results
//    this.resultConsumer = new ResultConsumer(wrapper);
//    resultConsumer.start();
  }

  private static class DecoderInput {
    private final HttpServletRequest request;
    private final Continuation continuation;
    private final Sequence<IString> text;
    private final int n;
    public DecoderInput(String text, int n, HttpServletRequest request,
        Continuation continuation) {
      this.text = IStrings.tokenize(text);
      this.n = n;
      this.request = request;
      this.continuation = continuation;
    }
  }
  
//  private static class DecoderOutput {
//    public final ServiceResponse serviceResponse;
//    public final HttpServletRequest request;
//    public final Continuation continuation;
//    public DecoderOutput(ServiceResponse serviceResponse,
//        HttpServletRequest request, Continuation continuation) {
//      this.serviceResponse = serviceResponse;
//      this.request = request;
//      this.continuation = continuation;
//    }
//  }
  
  private static class DecoderService implements ThreadsafeProcessor<DecoderInput,Boolean> {
    private final int threadId;
    private int childThreadId;
    private Phrasal phrasal;
    
    private static AtomicInteger inputId = new AtomicInteger();
    
    public DecoderService(int threadId, Phrasal phrasal) {
      this.threadId = threadId;
      this.childThreadId = threadId+1;
      this.phrasal = phrasal;
    }
    
    @Override
    public Boolean process(DecoderInput input) { 
      // Do decoding
      List<RichTranslation<IString,String>> translations = 
          phrasal.decode(input.text, inputId.incrementAndGet(), threadId, input.n); 
      
      List<String> translationList = Generics.newLinkedList();
      List<String> alignments = Generics.newLinkedList();
      for (RichTranslation<IString,String> translation : translations) {
        translationList.add(translation.translation.toString());
        alignments.add(translation.sourceTargetAlignmentString());
      }
      Type t = new TypeToken<BaseReply>() {}.getType();
      BaseReply baseResponse = new BaseReply(translationList, alignments);
      ServiceResponse serviceResponse = new ServiceResponse(baseResponse, t);
      
      input.request.setAttribute(PhrasalServlet.ASYNC_KEY, serviceResponse);   
      input.continuation.resume(); // Re-dispatch/ resume to generate response
      
//      return new DecoderOutput(serviceResponse, input.request, input.continuation);
      return true;
    }

    @Override
    public ThreadsafeProcessor<DecoderInput, Boolean> newInstance() {
      return new DecoderService(childThreadId++, phrasal);
    }
  }
  
  
  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    // Suspend the request
    Continuation continuation = ContinuationSupport.getContinuation(request);
    continuation.suspend(response);

    // Submit to the decoder
    TranslationRequest translationRequest = (TranslationRequest) baseRequest;
    DecoderInput input = new DecoderInput(translationRequest.text, translationRequest.n,
        request, continuation);
    wrapper.put(input);
  }

  @Override
  public ServiceResponse handle(Request request) {
    throw new UnsupportedOperationException("This is an asynchronous handler");
  }
//
//  private static class ResultConsumer extends Thread {
//    
//    private final MulticoreWrapper<DecoderInput, DecoderOutput> threadpool;
//    
//    public ResultConsumer(MulticoreWrapper<DecoderInput, DecoderOutput> threadpool) {
//      this.threadpool = threadpool;
//    }
//
//    @Override
//    public void run() {
//      System.err.println("Thread start");
//      while(threadpool.peek()) {
//        DecoderOutput result = threadpool.poll();
//        result.request.setAttribute(PhrasalServlet.ASYNC_KEY, result.serviceResponse);   
//        result.continuation.resume(); // Re-dispatch/ resume to generate response
//      }
//    }
//  }
  
}
