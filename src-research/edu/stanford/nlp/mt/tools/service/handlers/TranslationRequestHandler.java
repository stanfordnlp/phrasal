package edu.stanford.nlp.mt.tools.service.handlers;

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
import edu.stanford.nlp.mt.tools.service.Messages.TranslationReply;
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
 * @author Spence Green
 *
 */
public class TranslationRequestHandler implements RequestHandler {


  private final MulticoreWrapper<DecoderInput,Boolean> wrapper;

  public TranslationRequestHandler(Phrasal decoder) {
    // Setup a threadpool for phrasal that wraps the servlet internals
    // needed to restart the request after processing.
    wrapper = 
        new MulticoreWrapper<DecoderInput,Boolean>(decoder.getNumThreads(), 
            new DecoderService(0, decoder), false);
  }

  private static class DecoderInput {
    private final HttpServletRequest request;
    private final Continuation continuation;
    private final Sequence<IString> text;
    private final int n;
    private final long submitTime;
    public DecoderInput(String text, int n, HttpServletRequest request,
        Continuation continuation) {
      this.text = IStrings.tokenize(text);
      this.n = n;
      this.request = request;
      this.continuation = continuation;
      this.submitTime = System.nanoTime();
    }
  }

  private static class DecoderService implements ThreadsafeProcessor<DecoderInput,Boolean> {
    private final int threadId;
    private int childThreadId;
    private Phrasal phrasal;

    private static Logger logger;

    private static AtomicInteger inputId = new AtomicInteger();

    public DecoderService(int threadId, Phrasal phrasal) {
      this.threadId = threadId;
      this.childThreadId = threadId+1;
      this.phrasal = phrasal;
      if (threadId == 0) {
        logger = Logger.getLogger(TranslationRequestHandler.class.getName());
        PhrasalLogger.attach(logger, LogName.Service);
      }
    }

    @Override
    public Boolean process(DecoderInput input) {
      // Decode n-best list
      List<RichTranslation<IString,String>> translations = 
          phrasal.decode(input.text, inputId.incrementAndGet(), threadId, input.n); 

      List<String> translationList = Generics.newLinkedList();
      List<String> alignments = Generics.newLinkedList();
      for (RichTranslation<IString,String> translation : translations) {
        translationList.add(translation.translation.toString());
        alignments.add(translation.sourceTargetAlignmentString());
      }
      
      // Create the reply
      Type t = new TypeToken<TranslationReply>() {}.getType();
      TranslationReply baseResponse = new TranslationReply(translationList, alignments);
      ServiceResponse serviceResponse = new ServiceResponse(baseResponse, t);

      double querySeconds = ((double) System.nanoTime() - input.submitTime) / 1e9;
      logger.info(String.format("Elapsed time: %.3fs", querySeconds));
      
      input.request.setAttribute(PhrasalServlet.ASYNC_KEY, serviceResponse);   
      input.continuation.resume(); // Re-dispatch/ resume to generate response

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
    throw new UnsupportedOperationException("Synchronous call to asynchronous handler");
  }  
}
