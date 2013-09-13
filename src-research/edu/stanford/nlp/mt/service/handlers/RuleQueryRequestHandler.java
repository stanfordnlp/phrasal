package edu.stanford.nlp.mt.service.handlers;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.process.Postprocessor;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory.Language;
import edu.stanford.nlp.mt.service.PhrasalLogger;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.Messages.RuleQueryReply;
import edu.stanford.nlp.mt.service.Messages.RuleQueryRequest;
import edu.stanford.nlp.mt.service.PhrasalLogger.LogName;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
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
  private final Preprocessor preprocessor;
  private final Postprocessor postprocessor;
  private final Logger logger;

  public RuleQueryRequestHandler(PhraseGenerator<IString,String> phraseGenerator, 
      Scorer<String> scorer, Preprocessor preprocessor, Postprocessor postprocessor) {
    this.phraseTable = phraseGenerator;
    this.scorer = scorer;
    this.preprocessor = preprocessor;
    this.postprocessor = postprocessor;
    this.logger = Logger.getLogger(RuleQueryRequestHandler.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);
  }

  @Override
  public ServiceResponse handle(Request request) {
    final long startTime = System.nanoTime();
    RuleQueryRequest ruleRequest = (RuleQueryRequest) request;

    // Source pre-processing
    Sequence<IString> source;
    SymmetricalWordAlignment s2sPrime = null;
    if (preprocessor == null) {
      source = IStrings.tokenize(ruleRequest.text);
      s2sPrime = identityAlignment(source);
    
    } else {
      s2sPrime = preprocessor.processAndAlign(ruleRequest.text);
      source = s2sPrime.e();
    }
    
    List<ConcreteRule<IString,String>> ruleList = phraseTable
        .getRules(source, null, qId.incrementAndGet(), scorer);

    List<RuleQuery> queriedRules = Generics.newLinkedList();
    RuleGrid<IString,String> ruleGrid = new RuleGrid<IString,String>(ruleList, source, true);
    final int spanLimit = ruleRequest.spanLimit;
    final int maxPhraseLength = phraseTable.longestSourcePhrase();
    final int sourceLength = source.size();
    for (int i = 0; i < sourceLength; ++i) {
      int rightEdge = i+maxPhraseLength > sourceLength ? sourceLength : i+maxPhraseLength; 
      for (int j = i; j < rightEdge; ++j) {
        List<ConcreteRule<IString,String>> rulesForSpan = ruleGrid.get(i, j);
        int addedRules = 0;
        for(ConcreteRule<IString,String> rule : rulesForSpan) {
          if (addedRules >= spanLimit) {
            break;
          } else if (rule.abstractRule.target == null || rule.abstractRule.target.size() == 0) {
            continue;
          }
          SymmetricalWordAlignment tPrime2t = postprocessor == null ?
              identityAlignment(rule.abstractRule.target) :
                postprocessor.process(rule.abstractRule.target);    
          SymmetricalWordAlignment sPrime2tPrime = getAlignment(rule.abstractRule);
              RuleQuery ruleQuery = createQuery(rule, i, j, s2sPrime, sPrime2tPrime, tPrime2t);
          queriedRules.add(ruleQuery);
          ++addedRules;
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

  /**
   * Convert a PhraseAlignment to a SymmetricalWordAlignment. This is necessary because
   * PhraseAlignment only stores t2s alignments, but we need the other direction for
   * creating the query.
   * 
   * @param abstractRule
   * @return
   */
  private SymmetricalWordAlignment getAlignment(Rule<IString> abstractRule) {
    SymmetricalWordAlignment alignment = 
        new SymmetricalWordAlignment(abstractRule.source, abstractRule.target);
    for (int i = 0; i < abstractRule.alignment.size(); ++i) {
      int[] sIndices = abstractRule.alignment.t2s(i);
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
   * @return
   */
  private RuleQuery createQuery(ConcreteRule<IString, String> rule, int left, int right, SymmetricalWordAlignment s2sPrime,
      SymmetricalWordAlignment sPrime2tPrime, SymmetricalWordAlignment tPrime2t) {
    String src = s2sPrime.f().subsequence(left, right+1).toString();
    String tgt = tPrime2t.e().toString();
    int srcPos = s2sPrime.e2f(rule.sourcePosition).first();
    double score = rule.isolationScore;
    
    StringBuilder sb = new StringBuilder();
    for (int i = left; i <= right; ++i) {
      Set<Integer> alignments = s2sPrime.f2e(i);
      for (int j : alignments) {
        Set<Integer> alignments2 = sPrime2tPrime.f2e(j-left);
        for (int k : alignments2) {
          Set<Integer> alignments3 = tPrime2t.f2e(k);
          for (int q : alignments3) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(String.format("%d-%d",i,q));
          }
        }
      }
    }
    return new RuleQuery(src, tgt, srcPos, score, sb.toString());
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
