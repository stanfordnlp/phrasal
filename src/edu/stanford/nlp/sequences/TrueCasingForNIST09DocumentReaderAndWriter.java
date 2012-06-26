package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PositionAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PriorAnnotation;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.process.TrueCaser;
import edu.stanford.nlp.objectbank.LineIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.ie.crf.CRFBiasedClassifier;

import java.io.File;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapted from Pichuan's TrueCasingForNISTDocumentReaderAndWriter.java
 *
 * @author Michel Galley
 */
public class TrueCasingForNIST09DocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  public static final String THREE_CLASSES_PROPERTY = "3class";
  public static final boolean THREE_CLASSES = Boolean.parseBoolean(System.getProperty(THREE_CLASSES_PROPERTY, "false"));

  private static final long serialVersionUID = -3000389291781534479L;

  private IteratorFromReaderFactory<List<CoreLabel>> factory;
  private Map<String,String> mixedCaseMap = new HashMap<String,String>();

  private static int id = 0;

  public static void main(String[] args) throws IOException{
    Reader reader = new BufferedReader(new FileReader(args[0]));
    TrueCasingForNIST09DocumentReaderAndWriter raw = new TrueCasingForNIST09DocumentReaderAndWriter();
    raw.init(null);
    Iterator<List<CoreLabel>> it = raw.getIterator(reader);
    while(it.hasNext()) {
      List<CoreLabel> l = it.next();
      for (CoreLabel cl : l) {
        System.out.println(cl);
      }
      System.out.println("========================================");
    }
  }

  public void init(SeqClassifierFlags flags) {

    List<TrueCaser> auxModels = new LinkedList<TrueCaser>();
    List<String> auxModelNames = new LinkedList<String>();

    // Load map containing mixed-case words:
    mixedCaseMap = loadMixedCaseMap(flags.mixedCaseMapFile);

    // Load auxiliary models whose predictions are used as binary features:
    if(flags.auxTrueCaseModels != null && flags.auxTrueCaseModels.length() > 0) {
      String[] auxModelStr = flags.auxTrueCaseModels.split(",");
      for(int i=0; i<auxModelStr.length; ++i) {
        String[] els = auxModelStr[i].split("=");
        assert(els.length == 2);
        if(els[0].equals("lm")) {
          System.err.printf("Loading LM file %s...\n",els[1]);
          auxModelNames.add("lm"+i);
          try {
            TrueCaser lmTC = (TrueCaser) Class.forName("mt.tools.LanguageModelTrueCaser").newInstance();
            lmTC.init(els[1]);
            auxModels.add(lmTC);
          } catch(ClassNotFoundException e) {
            throw new UnsupportedOperationException();
          } catch(InstantiationException e) {
            throw new UnsupportedOperationException();
          } catch(IllegalAccessException e) {
            throw new UnsupportedOperationException();
          }
        } else if(els[0].equals("crf")) {
          System.err.printf("Loading CRF classifier %s ...\n",els[1]);
          auxModelNames.add("crf"+i);
          TrueCaser crfTC = new TrueCaser() {

            CRFBiasedClassifier<CoreLabel> crf;

            public void init(String crfModelFile) {
              Properties props = new Properties();
              props.put("classBias","INIT_UPPER:-0.7,UPPER:-0.7,O:0");
              crf = new CRFBiasedClassifier<CoreLabel>(props);
              crf.loadClassifierNoExceptions(crfModelFile, props);
            }

            public String[] trueCase(String[] words, int id) {
              List<List<CoreLabel>> l = crf.classifyRaw(StringUtils.join(words," "), TrueCasingForNIST09DocumentReaderAndWriter.this);
              assert(l.size() == 1);
              StringWriter sw = new StringWriter();
              PrintWriter pw = new PrintWriter(sw);
              for(List<CoreLabel> el : l)
                printAnswers(el, pw);
              pw.close();
              return sw.toString().split("\\s+");
            }
          };
          auxModels.add(crfTC);
          crfTC.init(els[1]);
        } else {
          throw new RuntimeException("Unknown model type: "+els[0]);
        }
      }
    }

    // Factory stuff:
    System.err.println("init factory");
    factory = LineIterator.getFactory(new LineToTrueCasesParser(auxModels, auxModelNames));
    System.err.println("factory="+factory);
  }

  public static Map<String,String> loadMixedCaseMap(String mapFile) {
    Map<String,String> map = new HashMap<String,String>();
    for(String line : ObjectBank.getLineIterator(new File(mapFile))) {
      line = line.trim();
      String[] els = line.split("\\s+");
      if(els.length != 2)
        throw new RuntimeException("Wrong format: "+mapFile);
      map.put(els[0],els[1]);
    }
    return map;
  }

  public static Set knownWords = null;

  public static boolean known(String s) {
    return knownWords.contains(s.toLowerCase());
  }

  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    List<String> sentence = new ArrayList<String>();

    int wrong = 0;

    for (CoreLabel wi : doc) {
      StringBuilder sb = new StringBuilder();
      if (! wi.get(AnswerAnnotation.class).equals(wi.get(GoldAnswerAnnotation.class))) {
        wrong++;
      }
      if (!THREE_CLASSES && wi.get(AnswerAnnotation.class).equals("UPPER")) {
        sb.append(wi.word().toUpperCase());
      } else if (wi.get(AnswerAnnotation.class).equals("LOWER")) {
        sb.append(wi.word().toLowerCase());
      } else if (wi.get(AnswerAnnotation.class).equals("INIT_UPPER")) {
        sb.append(wi.word().substring(0,1).toUpperCase())
          .append(wi.word().substring(1));
      } else if (wi.get(AnswerAnnotation.class).equals("O")) {
        // The model predicted mixed case, so lookup the map:
        String w = wi.word();
        if(mixedCaseMap.containsKey(w))
          w = mixedCaseMap.get(w);
        sb.append(w);
      }

      /*if (verboseForTrueCasing) {
        sb.append("/GOLD-")
          .append(wi.get(GoldAnswerAnnotation.class))
          .append("/GUESS-")
          .append(wi.get(AnswerAnnotation.class));
      }*/
      sentence.add(sb.toString());
    }
    out.print(StringUtils.join(sentence, " "));
    System.err.printf("> wrong = %d ; total = %d\n", wrong, doc.size());
    out.println();
  }

  public static class LineToTrueCasesParser implements Function<String,List<CoreLabel>> {
    private static Pattern allLower = Pattern.compile("[^A-Z]*?[a-z]+[^A-Z]*?");
    private static Pattern allUpper = Pattern.compile("[^a-z]*?[A-Z]+[^a-z]*?");
    private static Pattern startUpper = Pattern.compile("[A-Z].*");

    private final List<TrueCaser> auxModels;
    private final List<String> auxModelNames;

    public LineToTrueCasesParser(List<TrueCaser> auxModels, List<String> auxModelNames) {
      this.auxModels = auxModels;
      this.auxModelNames = auxModelNames;
    }

    public List<CoreLabel> apply(String line) {
      List<CoreLabel> doc = new ArrayList<CoreLabel>();
      int pos = 0;

      String[] toks = line.split(" ");
      System.err.printf("input(%d): <%s>\n", toks.length, line);

      for (String word : toks) {
        CoreLabel wi = new CoreLabel();

        String caseType = caseType(word);

        wi.set(AnswerAnnotation.class, caseType);
        wi.set(GoldAnswerAnnotation.class, caseType);

        wi.setWord(word.toLowerCase());
        wi.set(PositionAnnotation.class, String.valueOf(pos));
        doc.add(wi);
        pos++;
      }

      // i=model id, j=word id
      if(auxModels.size() > 0) {
        String[][] cased = new String[auxModels.size()][];
        for(int i=0; i<auxModels.size(); ++i) {
          cased[i] = auxModels.get(i).trueCase(toks, id);
          System.err.printf("output(%d,%s): <%s>\n", cased[i].length, auxModelNames.get(i), StringUtils.join(cased[i]," "));
          ++id;
        }
        for(int j=0; j<cased[0].length; ++j) {
          Map<String,Double> prior = new HashMap<String,Double>();
          CoreLabel wi = doc.get(j);
          for (int i=0; i<cased.length; ++i) {
            String type = caseType(cased[i][j]);
            prior.put(auxModelNames.get(i)+"_"+type, -0.001);
          }
          wi.set(PriorAnnotation.class, prior);
        }
      }

      return doc;
    }

    private static String caseType(String word) {

      Matcher lowerMatcher = allLower.matcher(word);

      if (lowerMatcher.matches()) {
        return "LOWER";
      } else {
        Matcher upperMatcher = allUpper.matcher(word);
        if (!THREE_CLASSES && upperMatcher.matches()) {
          return "UPPER";
        } else {
          Matcher startUpperMatcher = startUpper.matcher(word);

          boolean isINIT_UPPER;
          if (word.length() > 1) {
            String w2 = word.substring(1);
            String lcw2 = w2.toLowerCase();
            isINIT_UPPER = w2.equals(lcw2);
          } else {
            isINIT_UPPER = false;
          }

          if (startUpperMatcher.matches() && isINIT_UPPER) {
            return "INIT_UPPER";
          } else {
            return "O";
          }
        }
      }
    }

  }

}
