package edu.stanford.nlp.mt.service.handlers;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.SystemLogger;
import edu.stanford.nlp.mt.base.SystemLogger.LogName;
import edu.stanford.nlp.mt.process.Postprocessor;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory;
import edu.stanford.nlp.mt.process.ProcessorFactory.Language;
import edu.stanford.nlp.mt.service.PhrasalServlet;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.Messages.TranslationReply;
import edu.stanford.nlp.mt.service.Messages.TranslationRequest;
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

  private static final int DIVERSITY_WINDOW = 3;
  private static final int NBEST_MULTIPLIER = 10;
  private static final int MAX_RETRIES_PER_REQUEST = 2;
  
  private MulticoreWrapper<DecoderInput,DecoderOutput> wrapper;
  private final Phrasal decoder;

  // Threadsafe fields shared among decoding threads
  private static final ConcurrentHashMap<Language,Preprocessor> targetPreprocessorCache =
      new ConcurrentHashMap<Language,Preprocessor>();
  private static Logger logger;
  private static AtomicInteger inputId = new AtomicInteger();

  /**
   * Constructor.
   * 
   * @param decoder
   */
  public TranslationRequestHandler(Phrasal decoder) {
    this.decoder = decoder;
    // Setup a threadpool for phrasal that wraps the servlet internals
    // needed to restart the request after processing.
    wrapper = new MulticoreWrapper<DecoderInput,DecoderOutput>(decoder.getNumThreads(), 
        new DecoderService(0, decoder), false);
    logger = Logger.getLogger(TranslationRequestHandler.class.getName());
    SystemLogger.attach(logger, LogName.SERVICE);
  }

  private static class DecoderInput {
    private final int inputId;
    private final HttpServletRequest request;
    private final Continuation continuation;
    private final String text;
    private final String tgtPrefix;
    private final Language targetLanguage;
    private final int n;
    private final long submitTime;
    public DecoderInput(int inputId, String text, String prefix, int n, Language targetLanguage, HttpServletRequest request,
        Continuation continuation) {
      this.inputId = inputId;
      this.text = text;
      this.tgtPrefix = prefix;
      this.targetLanguage = targetLanguage;
      this.n = n;
      this.request = request;
      this.continuation = continuation;
      this.submitTime = System.nanoTime();
    }
    @Override
    public String toString() {
      return String.format("id: %d n: %d tgt_lang: %s%nsrc: %s%ntgt: %s", inputId, n, targetLanguage.toString(), text, tgtPrefix);
    }
  }
  
  private static class DecoderOutput {
    private final int inputId;
    private final boolean success;
    public DecoderOutput(int inputId, boolean status) {
      this.inputId = inputId;
      this.success = status;
    }
  }

  private static class DecoderService implements ThreadsafeProcessor<DecoderInput,DecoderOutput> {
    private final int threadId;
    private int childThreadId;
    private final Phrasal decoder;
    private final boolean dropUnknownWords;
    private final Preprocessor sourcePreprocessor;
    private final Postprocessor postprocessor;

    public DecoderService(int threadId, Phrasal decoder) {
      this.threadId = threadId;
      this.childThreadId = threadId+1;
      this.decoder = decoder;
      this.dropUnknownWords = decoder.isDropUnknownWords();
      this.sourcePreprocessor = decoder.getPreprocessor();
      this.postprocessor = decoder.getPostprocessor();
    }

    @Override
    public DecoderOutput process(DecoderInput input) {
      logger.info(String.format("Input %d: %s", input.inputId, input.text));
      try {
        // Source pre-processing
        final long preprocStart = System.nanoTime();
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
        List<Sequence<IString>> targets = null;
        if (input.tgtPrefix != null && input.tgtPrefix.length() > 0) {
          SymmetricalWordAlignment t2t;
          try {
            Language targetLanguage = input.targetLanguage;
            if ( ! targetPreprocessorCache.contains(targetLanguage)) {
              targetPreprocessorCache.putIfAbsent(targetLanguage, ProcessorFactory.getPreprocessor(input.targetLanguage.name()));
            }
            t2t = targetPreprocessorCache.get(targetLanguage).processAndAlign(input.tgtPrefix);
            
          } catch (Exception e) {
            logger.log(Level.WARNING, "Prefix preprocessor threw an exception", e);
            Sequence<IString> prefix = IStrings.tokenize(input.tgtPrefix);
            t2t = identityAlignment(prefix);
          }
          targets = Generics.newLinkedList();
          targets.add(t2t.e());
        }
        
        // Decode
        final long decodeStart = System.nanoTime();
        final int numRequestedTranslations = input.n;
        final int numTranslationsToGenerate = input.n * NBEST_MULTIPLIER;
        List<RichTranslation<IString,String>> translations = 
            decoder.decode(source, input.inputId, threadId, numTranslationsToGenerate, targets, targets != null); 
        logger.info(String.format("Input %d decoder: #translations: %d",
            input.inputId, translations.size()));
        
        // Result extraction and post-processing
        final long postprocStart = System.nanoTime();
        List<Sequence<IString>> translationList = Generics.newArrayList(numRequestedTranslations);
        List<List<String>> alignments = Generics.newArrayList(numRequestedTranslations);
        List<Double> scoreList = Generics.newArrayList(numRequestedTranslations);
        
        // Introduce additional diversity
        Set<Sequence<IString>> diversityPool = Generics.newHashSet(translations.size());
        final int startIndex = targets == null ? 0 : targets.get(0).size();
        for (RichTranslation<IString,String> translation : translations) {
          if (translationList.size() == numRequestedTranslations) {
            break;
          }
          if (translation.translation.size() == 0) {
            // Input was simply deleted by the OOV model
            continue;
          }
          
          // Encourage diversity off the end of the prefix
          final int maxIndex = Math.min(startIndex+DIVERSITY_WINDOW, translation.translation.size());
          if (startIndex < maxIndex) {
            Sequence<IString> window = translation.translation.subsequence(startIndex, maxIndex);
            if (diversityPool.contains(window)) {
              continue;
            }
            diversityPool.add(window);
          }
          
          // Try phrase alignments instead of word alignments
//          SymmetricalWordAlignment sPrime2tPrime = translation.alignmentGrid();
          SymmetricalWordAlignment sPrime2tPrime = phraseAlignmentToWordAlignmentGrid(translation);
          
          SymmetricalWordAlignment tPrime2t = postprocessor == null ?
              identityAlignment(translation.translation) :
                postprocessor.process(translation.translation);
          List<String> alignmentString = mapAlignments(s2sPrime, sPrime2tPrime, tPrime2t);
          
          // Keep this translation
          translationList.add(tPrime2t.e());
          alignments.add(alignmentString);
          scoreList.add(Math.exp(translation.score));
        }

        // Timing statistics
        final long doneTime = System.nanoTime();
        double preprocSeconds = (decodeStart - preprocStart) / 1e9;
        double decodeSeconds = (postprocStart - decodeStart) / 1e9;
        double postprocSeconds = (doneTime - postprocStart) / 1e9;
        double querySeconds = (doneTime - input.submitTime) / 1e9;
        logger.info(String.format("Input %d timing: elapsed %.3fs (pre: %.3fs  decode: %.3fs  post: %.3fs)",
            input.inputId, querySeconds, preprocSeconds, decodeSeconds, postprocSeconds));

        // Create the service reply
        TranslationRequestHandler.populateRequest(input.request, translationList, alignments, scoreList);
        input.continuation.resume(); // Re-dispatch/ resume to generate response

        return new DecoderOutput(input.inputId, true);
      
      } catch(Exception e) {
        // Catch all exception handler. Generate an empty response.
        logger.log(Level.SEVERE, "Decoding of request failed: " + input.toString(), e);
        TranslationRequestHandler.populateRequest(input.request, new LinkedList<Sequence<IString>>(), 
            new LinkedList<List<String>>(), new LinkedList<Double>());
        input.continuation.resume(); // Re-dispatch/ resume to generate response
      }
      return new DecoderOutput(input.inputId, false);
    }

    /**
     * Convert a phrase alignment to a dense word alignment.
     * 
     * @param translation
     * @return
     */
    private SymmetricalWordAlignment phraseAlignmentToWordAlignmentGrid(
        RichTranslation<IString, String> translation) {
      final Featurizable<IString,String> featurizable = translation.getFeaturizable();
      SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(featurizable.sourceSentence, 
          translation.translation);
      // Walk back through the featurizables.
      for (Featurizable<IString,String> f = featurizable; f != null; f = f.prior) {
        int srcPosition = f.sourcePosition;
        int tgtPosition = f.targetPosition;
        int tgtLength = f.targetPhrase.size();
        int srcLength = f.sourcePhrase.size();
        for (int i = 0, size = Math.min(srcLength, tgtLength); i < size; ++i) {
          final int tgtIndex = tgtPosition + i;
          int srcIndex = srcPosition + i;
          alignment.addAlign(srcIndex, tgtIndex);
        }
        if (srcLength < tgtLength) {
          final int srcIndex = srcPosition + srcLength - 1;
          for (int i = srcLength; i < tgtLength; ++i) {
            final int tgtIndex = tgtPosition + i;
            alignment.addAlign(srcIndex, tgtIndex);
          }
          
        } else {
          final int tgtIndex = tgtPosition + tgtLength - 1;
          for (int i = tgtLength; i < srcLength; ++i) {
            final int srcIndex = srcPosition + i;
            alignment.addAlign(srcIndex, tgtIndex);
          }
        }
      }
      return alignment;
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
    private List<String> mapAlignments(SymmetricalWordAlignment s2sPrime,
        SymmetricalWordAlignment sPrime2tPrime,
        SymmetricalWordAlignment tPrime2t) {
      
      // If the decoder drops unknown words, then we need to create a monotonic
      // alignment.
      int[] sPrime2sPrimePrime = null;
      if (dropUnknownWords && s2sPrime.e().size() != sPrime2tPrime.f().size()) {
        Sequence<IString> sPrime = s2sPrime.e();
        int srcSize = sPrime.size();
        sPrime2sPrimePrime = new int[srcSize];
        Arrays.fill(sPrime2sPrimePrime, -1);
        // Decoder filtered some words from this string
        Sequence<IString> sPrimePrime = sPrime2tPrime.f();
        assert sPrimePrime.size() < sPrime.size();
        for (int i = 0, tgtSize = sPrimePrime.size(); i < tgtSize; ++i) {
          IString tgtToken = sPrimePrime.get(i);
          for (int j = i; j < srcSize; ++j) {
            if (tgtToken == sPrime.get(j)) {
              sPrime2sPrimePrime[j] = i;
              break;
            }
          }
        }
      }
      
      List<String> alignmentList = Generics.newLinkedList();
      for (int i = 0, size = s2sPrime.fSize(); i < size; ++i) {
        Set<Integer> alignments = s2sPrime.f2e(i);
        for (int j : alignments) {
          if (sPrime2sPrimePrime != null) {
            j = sPrime2sPrimePrime[j];
            if (j < 0) continue;
          }
          Set<Integer> alignments2 = sPrime2tPrime.f2e(j);
          for (int k : alignments2) {
            Set<Integer> alignments3 = tPrime2t.f2e(k);
            for (int q : alignments3) {
              alignmentList.add(String.format("%d-%d",i,q));
            }
          }
        }
      }
      return alignmentList;
    }

    private static SymmetricalWordAlignment identityAlignment(Sequence<IString> sequence) {
      SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sequence,sequence);
      for (int i = 0, sequenceLength = sequence.size(); i < sequenceLength; ++i) {
        alignment.addAlign(i, i);
      }
      return alignment;
    }

    @Override
    public ThreadsafeProcessor<DecoderInput, DecoderOutput> newInstance() {
      return new DecoderService(childThreadId++, decoder);
    }
  }

  /**
   * Package the output of the decoder and post-processor for return by the service.
   * 
   * @param translationList
   * @param alignments
   * @param scoreList
   * @return
   */
  private static List<TranslationQuery> toQuery(List<Sequence<IString>> translationList,
      List<List<String>> alignments, List<Double> scoreList) {
    final int nTranslations = translationList.size();
    double normalizer = 0.0;
    for (double d : scoreList) normalizer += d;
    List<TranslationQuery> sortedList = Generics.newArrayList(nTranslations);
    for (int i = 0; i < nTranslations; ++i) {
      TranslationQuery query = new TranslationQuery(Sequences.toStringList(translationList.get(i)),
          alignments.get(i), scoreList.get(i) / normalizer);
      sortedList.add(query);
    }
    return sortedList;
  }

  /**
   * Populate a servlet request with the result of processing the input.
   * 
   * @param request
   * @param translationList
   * @param alignments
   * @param scoreList
   */
  private static void populateRequest(HttpServletRequest request, List<Sequence<IString>> translationList,
      List<List<String>> alignments, List<Double> scoreList) {
    Type t = new TypeToken<TranslationReply>() {}.getType();
    List<TranslationQuery> queryList = toQuery(translationList, alignments, scoreList);
    TranslationReply baseResponse = new TranslationReply(queryList);
    ServiceResponse serviceResponse = new ServiceResponse(baseResponse, t);
    request.setAttribute(PhrasalServlet.ASYNC_KEY, serviceResponse);   
  }
  
  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    logger.info(wrapper.toString());
    
    // Suspend the request
    Continuation continuation = ContinuationSupport.getContinuation(request);
    continuation.suspend(response);

    // Create the input to the translation service
    TranslationRequest translationRequest = (TranslationRequest) baseRequest;
    int sourceId = inputId.incrementAndGet();
    DecoderInput input = new DecoderInput(sourceId, translationRequest.text, translationRequest.tgtPrefix, 
        translationRequest.n, translationRequest.tgt, request, continuation);

    // Try to submit the request to the service.
    boolean requestSubmitted = false;
    for (int numRetries = 0; numRetries < MAX_RETRIES_PER_REQUEST; ++numRetries) {
      try {
        // Clear the wrapper of status messages of completed jobs
        wrapper.put(input);
        requestSubmitted = true;
        while(wrapper.peek()) {
          DecoderOutput status = wrapper.poll();
          sourceId = status.inputId;
          if (status.success) {
            logger.info(String.format("Input id %s: status %b", status.inputId, status.success));
          } else {
            logger.severe(String.format("Input id %s: status %b", status.inputId, status.success));
          }
        }
        break;
        
      } catch (RejectedExecutionException e) {
        logger.log(Level.SEVERE, "Threadpool corrupted by underlying exceptions. Restarting...", e);
        wrapper.join();
        wrapper = new MulticoreWrapper<DecoderInput,DecoderOutput>(decoder.getNumThreads(), 
            new DecoderService(0, decoder), false);
        logger.info("Restarted threadpool");
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Exception while processing request", e);
      }
    }
    
    if ( ! requestSubmitted) {
      logger.severe("Decoding of request failed: " + input.toString());
      TranslationRequestHandler.populateRequest(input.request, new LinkedList<Sequence<IString>>(), 
          new LinkedList<List<String>>(), new LinkedList<Double>());
      input.continuation.resume(); // Re-dispatch/ resume to generate response
    }
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
