package edu.stanford.nlp.mt.service.handlers;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.process.ProcessorFactory.Language;
import edu.stanford.nlp.mt.service.PhrasalLogger;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.Messages.RuleQueryReply;
import edu.stanford.nlp.mt.service.Messages.RuleQueryRequest;
import edu.stanford.nlp.mt.service.PhrasalLogger.LogName;
import edu.stanford.nlp.util.Generics;

/**
 * Synchronous handler for phrase table query messages.
 * 
 * TODO: Tokenization and detokenization.
 * 
 * @author Spence Green
 *
 */
public class RuleQueryRequestHandler implements RequestHandler {

  private static final AtomicInteger qId = new AtomicInteger();

  private final PhraseGenerator<IString, String> phraseTable;
  private final Scorer<String> scorer;
  private final Logger logger;

  public RuleQueryRequestHandler(PhraseGenerator<IString,String> phraseGenerator, 
      Scorer<String> scorer) {
    this.phraseTable = phraseGenerator;
    this.scorer = scorer;
    this.logger = Logger.getLogger(RuleQueryRequestHandler.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);
  }

  @Override
  public ServiceResponse handle(Request request) {
    final long startTime = System.nanoTime();
    RuleQueryRequest ruleRequest = (RuleQueryRequest) request;

    Sequence<IString> sourceSegment = IStrings.tokenize(ruleRequest.text);

    List<ConcreteRule<IString,String>> ruleList = phraseTable
        .getRules(sourceSegment, null, qId.incrementAndGet(), scorer);

    List<RuleQuery> queriedRules = Generics.newLinkedList();
    RuleGrid<IString,String> ruleGrid = new RuleGrid<IString,String>(ruleList, sourceSegment, true);
    final int spanLimit = ruleRequest.spanLimit;
    final int maxPhraseLength = phraseTable.longestSourcePhrase();
    final int sourceLength = sourceSegment.size();
    for (int i = 0; i < sourceLength; ++i) {
      int rightEdge = i+maxPhraseLength > sourceLength ? sourceLength : i+maxPhraseLength; 
      for (int j = i; j < rightEdge; ++j) {
        List<ConcreteRule<IString,String>> rulesForSpan = ruleGrid.get(i, j);
        final int numRules = rulesForSpan.size() < spanLimit ? rulesForSpan.size() : spanLimit;
        for (int k = 0; k < numRules; ++k) {
          queriedRules.add(new RuleQuery(rulesForSpan.get(k)));
        }
      }
    }
    RuleQueryReply reply = new RuleQueryReply(queriedRules);
    Type t = new TypeToken<RuleQueryReply>() {}.getType();
    ServiceResponse response = new ServiceResponse(reply, t);

    double querySeconds = ((double) System.nanoTime() - startTime) / 1e9;
    logger.info(String.format("Elapsed time: %.3fs", querySeconds));

    return response;
  }

  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    throw new UnsupportedOperationException("Asynchronous call to synchronous handler.");
  }

  @Override
  public boolean validate(Request baseRequest) {
    RuleQueryRequest request = (RuleQueryRequest) baseRequest;
    if (request.src == Language.UNK || request.tgt == Language.UNK)
      return false;
    if (request.text == null || request.text.length() == 0)
      return false;
    return true;
  }

}
