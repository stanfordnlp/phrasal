package edu.stanford.nlp.mt.service.handlers;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
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
import edu.stanford.nlp.mt.process.Postprocessor;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory;
import edu.stanford.nlp.mt.process.ProcessorFactory.Language;
import edu.stanford.nlp.mt.service.PhrasalLogger;
import edu.stanford.nlp.mt.service.PhrasalServlet;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.Messages.TranslationReply;
import edu.stanford.nlp.mt.service.Messages.TranslationRequest;
import edu.stanford.nlp.mt.service.PhrasalLogger.LogName;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
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

  /**
   * Constructor.
   * 
   * @param decoder
   */
  public TranslationRequestHandler(Phrasal decoder) {
    // Setup a threadpool for phrasal that wraps the servlet internals
    // needed to restart the request after processing.
    wrapper = new MulticoreWrapper<DecoderInput,Boolean>(decoder.getNumThreads(), 
        new DecoderService(0, decoder), false);
  }

  private static class DecoderInput {
    private final HttpServletRequest request;
    private final Continuation continuation;
    private final String text;
    private final String tgtPrefix;
    private final Language targetLanguage;
    private final int n;
    private final long submitTime;
    public DecoderInput(String text, String prefix, int n, Language targetLanguage, HttpServletRequest request,
        Continuation continuation) {
      this.text = text;
      this.tgtPrefix = prefix;
      this.targetLanguage = targetLanguage;
      this.n = n;
      this.request = request;
      this.continuation = continuation;
      this.submitTime = System.nanoTime();
    }
  }

  private static class DecoderService implements ThreadsafeProcessor<DecoderInput,Boolean> {
    private final int threadId;
    private int childThreadId;
    private final Phrasal decoder;
    private final Preprocessor sourcePreprocessor;
    private final Postprocessor postprocessor;

    private static Logger logger;
    private static AtomicInteger inputId = new AtomicInteger();

    public DecoderService(int threadId, Phrasal decoder) {
      this.threadId = threadId;
      this.childThreadId = threadId+1;
      this.decoder = decoder;
      this.sourcePreprocessor = decoder.getPreprocessor();
      this.postprocessor = decoder.getPostprocessor();
      
      if (threadId == 0) {
        logger = Logger.getLogger(TranslationRequestHandler.class.getName());
        PhrasalLogger.attach(logger, LogName.Service);
      }
    }

    @Override
    public Boolean process(DecoderInput input) {
      // Source pre-processing
      Sequence<IString> source;
      SymmetricalWordAlignment s2sPrime = null;
      if (sourcePreprocessor == null) {
        source = IStrings.tokenize(input.text);
        s2sPrime = identityAlignment(source);
      
      } else {
        s2sPrime = sourcePreprocessor.processAndAlign(input.text);
        source = s2sPrime.e();
      }
      
      // Target prefix pre-processing
      boolean decodeWithPrefix = input.tgtPrefix != null;
      List<Sequence<IString>> targets = null;
      if (decodeWithPrefix) {
        // TODO(spenceg): Add try/catch
        Preprocessor targetPreprocessor = ProcessorFactory.getPreprocessor(input.targetLanguage.name());
        SymmetricalWordAlignment t2t = targetPreprocessor.processAndAlign(input.tgtPrefix);
        targets = Generics.newLinkedList();
        targets.add(t2t.e());
      }
      
      // Decode
      List<RichTranslation<IString,String>> translations = 
          decoder.decode(source, inputId.incrementAndGet(), threadId, input.n, targets, decodeWithPrefix); 

      // Result extraction and post-processing
      List<String> translationList = Generics.newLinkedList();
      List<String> alignments = Generics.newLinkedList();
      for (RichTranslation<IString,String> translation : translations) {
        SymmetricalWordAlignment sPrime2tPrime = translation.alignmentGrid();
        SymmetricalWordAlignment tPrime2t = postprocessor == null ?
            identityAlignment(translation.translation) :
              postprocessor.process(translation.translation);
        String alignmentString = mapAlignments(s2sPrime, sPrime2tPrime, tPrime2t);
        translationList.add(tPrime2t.e().toString());
        alignments.add(alignmentString);
      }
      assert translationList.size() == alignments.size();
      
      // Create the service reply
      Type t = new TypeToken<TranslationReply>() {}.getType();
      TranslationReply baseResponse = new TranslationReply(translationList, alignments);
      ServiceResponse serviceResponse = new ServiceResponse(baseResponse, t);

      double querySeconds = ((double) System.nanoTime() - input.submitTime) / 1e9;
      logger.info(String.format("Elapsed time: %.3fs", querySeconds));
      
      input.request.setAttribute(PhrasalServlet.ASYNC_KEY, serviceResponse);   
      input.continuation.resume(); // Re-dispatch/ resume to generate response

      return true;
    }

    /**
     * Map alignments from source input to target output, with several
     * possible processing steps along the way.
     * 
     * @param s2sPrime
     * @param sPrime2tPrime
     * @param tPrime2t
     * @return
     */
    private static String mapAlignments(SymmetricalWordAlignment s2sPrime,
        SymmetricalWordAlignment sPrime2tPrime,
        SymmetricalWordAlignment tPrime2t) {
      assert s2sPrime.e().size() == sPrime2tPrime.f().size();
      assert sPrime2tPrime.e().size() == tPrime2t.f().size();
      
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < s2sPrime.fSize(); ++i) {
        Set<Integer> alignments = s2sPrime.f2e(i);
        for (int j : alignments) {
          Set<Integer> alignments2 = sPrime2tPrime.f2e(j);
          for (int k : alignments2) {
            Set<Integer> alignments3 = tPrime2t.f2e(k);
            for (int q : alignments3) {
              if (sb.length() > 0) sb.append(" ");
              sb.append(String.format("%d-%d",i,q));
            }
          }
        }
      }
      return sb.toString();
    }

    private static SymmetricalWordAlignment identityAlignment(Sequence<IString> sequence) {
      SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sequence,sequence);
      int sequenceLength = sequence.size();
      for (int i = 0; i < sequenceLength; ++i) {
        alignment.addAlign(i, i);
      }
      return alignment;
    }

    @Override
    public ThreadsafeProcessor<DecoderInput, Boolean> newInstance() {
      return new DecoderService(childThreadId++, decoder);
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
    DecoderInput input = new DecoderInput(translationRequest.text, translationRequest.tgtPrefix, 
        translationRequest.n, translationRequest.tgt, request, continuation);
    wrapper.put(input);
  }

  @Override
  public ServiceResponse handle(Request request) {
    throw new UnsupportedOperationException("Synchronous call to asynchronous handler");
  }

  @Override
  public boolean validate(Request baseRequest) {
    TranslationRequest request = (TranslationRequest) baseRequest;
    if (request.src == Language.UNK || request.tgt == Language.UNK)
      return false;
    if (request.text == null || request.text.length() == 0)
      return false;
    return true;
  }  
}
