package edu.stanford.nlp.mt.service.handlers;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.SystemLogger;
import edu.stanford.nlp.mt.base.SystemLogger.LogName;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.process.Postprocessor;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory.Language;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.Messages.RuleQueryReply;
import edu.stanford.nlp.mt.service.Messages.RuleQueryRequest;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.util.Generics;

/**
 * Synchronous handler for phrase table query messages.
 * 
 * @author Spence Green
 *
 */
public class RuleQueryRequestHandler implements RequestHandler {

  private static final AtomicInteger qId = new AtomicInteger();

  private final PhraseGenerator<IString, String> phraseTable;
  private final Scorer<String> scorer;
  private final Preprocessor preprocessor;
  private final Postprocessor postprocessor;
  private final Logger logger;

  /**
   * Constructor.
   * 
   * @param phraseGenerator
   * @param scorer
   * @param preprocessor
   * @param postprocessor
   */
  public RuleQueryRequestHandler(PhraseGenerator<IString,String> phraseGenerator, 
      Scorer<String> scorer, Preprocessor preprocessor, Postprocessor postprocessor) {
    this.phraseTable = phraseGenerator;
    this.scorer = scorer;
    this.preprocessor = preprocessor;
    this.postprocessor = postprocessor;
    this.logger = Logger.getLogger(RuleQueryRequestHandler.class.getName());
    SystemLogger.attach(logger, LogName.SERVICE);
  }

  @Override
  public ServiceResponse handle(Request request) {
    final long startTime = System.nanoTime();
    ServiceResponse response;
    try {
      RuleQueryRequest ruleRequest = (RuleQueryRequest) request;

      // Source pre-processing
      Sequence<IString> source;
      Sequence<IString> sourceContext;
      SymmetricalWordAlignment s2sPrime = null;
      if (preprocessor == null) {
        source = IStrings.tokenize(ruleRequest.text);
        sourceContext = ruleRequest.leftContext != null && ruleRequest.leftContext.length() > 0 ? 
            IStrings.tokenize(ruleRequest.leftContext) : null;
            s2sPrime = identityAlignment(source);

      } else {
        s2sPrime = preprocessor.processAndAlign(ruleRequest.text);
        source = s2sPrime.e();
        sourceContext = ruleRequest.leftContext != null && ruleRequest.leftContext.length() > 0 ?
            preprocessor.process(ruleRequest.leftContext) : null;
      }

      // Query the phrase table
      List<ConcreteRule<IString,String>> rulesForSpan;
      ConcreteRule<IString,String> bestLeftContext = null;
      if (sourceContext == null) {
        List<ConcreteRule<IString,String>> ruleList = phraseTable
            .getRules(source, null, null, qId.incrementAndGet(), scorer);
        RuleGrid<IString,String> ruleGrid = new RuleGrid<IString,String>(ruleList, source, true);
        rulesForSpan = ruleGrid.get(0, source.size()-1);

      } else {
        Sequence<IString> queryString = Sequences.concatenate(sourceContext, source);
        List<ConcreteRule<IString,String>> ruleList = phraseTable
            .getRules(queryString, null, null, qId.incrementAndGet(), scorer);
        RuleGrid<IString,String> ruleGrid = new RuleGrid<IString,String>(ruleList, queryString, true);
        rulesForSpan = ruleGrid.get(sourceContext.size(), queryString.size()-1);
        List<ConcreteRule<IString,String>> rulesForContext = ruleGrid.get(0, sourceContext.size()-1);
        bestLeftContext = rulesForContext.size() > 0 ? rulesForContext.get(0) : null;
      }

      // Process the query
      double normalizer = 0.0;
      List<RuleQuery> queriedRules = Generics.newArrayList(ruleRequest.spanLimit);
      for(ConcreteRule<IString,String> rule : rulesForSpan) {
        if (queriedRules.size() >= ruleRequest.spanLimit) {
          break;
        } else if (rule.abstractRule.target == null || rule.abstractRule.target.size() == 0) {
          // Ignore deletion rules from the unknown word model
          continue;
        }

        // Extract word-word alignment from the rule.
        SymmetricalWordAlignment sPrime2tPrime = getAlignment(rule.abstractRule);

        // Post-process the target side, possibly adding left context.
        Sequence<IString> target = rule.abstractRule.target;
        int offset = 0;
        if (bestLeftContext != null) {
          target = Sequences.concatenate(bestLeftContext.abstractRule.target, target);
          offset = bestLeftContext.abstractRule.target.size();
        }
        SymmetricalWordAlignment tPrime2t = postprocessor == null ?
            identityAlignment(target) : postprocessor.process(target);

        double score = Math.exp(rule.isolationScore);
        normalizer += score;
        RuleQuery query = createQueryResult(ruleRequest.text, score, s2sPrime, sPrime2tPrime, tPrime2t, offset);
        queriedRules.add(query);
      }
      // Normalize the model scores
      for (RuleQuery query : queriedRules) {
        query.setScore(query.score / normalizer);
      }

      // Successful query
      RuleQueryReply reply = new RuleQueryReply(queriedRules);
      Type t = new TypeToken<RuleQueryReply>() {}.getType();
      response = new ServiceResponse(reply, t);

    } catch (Exception e) {
      logger.log(Level.SEVERE, "Rule query request failed", e);
      RuleQueryReply reply = new RuleQueryReply(new LinkedList<RuleQuery>());
      Type t = new TypeToken<RuleQueryReply>() {}.getType();
      response = new ServiceResponse(reply, t);
    }

    double querySeconds = (System.nanoTime() - startTime) / 1e9;
    logger.info(String.format("Rule query elapsed time: %.3fs", querySeconds));

    return response;
  }

  /**
   * Convert a PhraseAlignment to a SymmetricalWordAlignment. This is necessary because
   * PhraseAlignment only stores t2s alignments, but we need the other direction for
   * creating the query.
   * 
   * @param rule
   * @param srcPos 
   * @return
   */
  private SymmetricalWordAlignment getAlignment(Rule<IString> rule) {
    SymmetricalWordAlignment alignment = 
        new SymmetricalWordAlignment(rule.source, rule.target);
    int tgtLength = rule.target.size();
    for (int i = 0; i < tgtLength; ++i) {
      int[] sIndices = rule.alignment.t2s(i);
      if (sIndices != null) {
        for (int sIndex : sIndices) {
          alignment.addAlign(sIndex, i);
        }
      }
    }
    return alignment;
  }

  /**
   * Create the RuleQuery.
   * 
   * @param rule
   * @param right 
   * @param left 
   * @param s2sPrime
   * @param sPrime2tPrime
   * @param tPrime2t
   * @param tContextOffset 
   * @return
   */
  private RuleQuery createQueryResult(String sourceText, double isolationScore, 
      SymmetricalWordAlignment s2sPrime, SymmetricalWordAlignment sPrime2tPrime, 
      SymmetricalWordAlignment tPrime2t, int tContextOffset) {

    // Alignments
    List<String> alignmentList = Generics.newLinkedList();
    Set<Integer> alignments = s2sPrime.f2e(0);
    for (int i : alignments) {
      Set<Integer> alignments2 = sPrime2tPrime.f2e(i);
      for (int j : alignments2) {
        j += tContextOffset;
        Set<Integer> alignments3 = tPrime2t.f2e(j);
        for (int k : alignments3) {
          alignmentList.add(String.format("%d-%d",0,k));
        }
      }
    }
    Sequence<IString> tgt = tPrime2t.e();
    if (tContextOffset > 0) {
      tgt = tgt.subsequence(tContextOffset, tgt.size());
    }
    List<String> tgtStrings = Sequences.toStringList(tgt);
    return new RuleQuery(tgtStrings, alignmentList, isolationScore);
  }

  /**
   * An identity alignment.
   * 
   * @param sequence
   * @return
   */
  private static SymmetricalWordAlignment identityAlignment(Sequence<IString> sequence) {
    SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sequence,sequence);
    int sequenceLength = sequence.size();
    for (int i = 0; i < sequenceLength; ++i) {
      alignment.addAlign(i, i);
    }
    return alignment;
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
