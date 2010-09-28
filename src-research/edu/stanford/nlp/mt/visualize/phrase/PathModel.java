package edu.stanford.nlp.mt.visualize.phrase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.util.XMLUtils;

/**
 * 
 * @author Spence Green
 */
public class PathModel {

  // TODO Make this arbitrary - fixing for now so that arbitrary color schemes
  // need not be defined
  public static final int MAX_PATHS = 5;

  // Element names in the input file
  private static final String ROOT = "tr";
  private static final String SYSTEM = "engine";
  private static final String SENTENCE = "sentence";
  private static final String SENT_ID = "id";
  private static final String SOURCE = "source";
  private static final String PATH = "path";
  private static final String PATH_NAME = "name";
  private static final String WORD = "w";
  private static final String PHRASE = "p";
  private static final String ALGN_START = "start";
  private static final String ALGN_END = "end";
  private static final String SCORE = "sco";

  // Members
  private final Map<Integer, List<Path>> translationPaths;
  private Path currentPath = null;
  private boolean isLoaded = false;
  private final PhraseController controller;
  private final boolean VERBOSE;

  public PathModel() {
    controller = PhraseController.getInstance();
    VERBOSE = controller.getVerbose();

    translationPaths = new HashMap<Integer, List<Path>>();

    controller.addClickEventListener(clickStreamListener);
  }

  public void freeResources() {
    controller.removeClickEventListener(clickStreamListener);
  }

  private ClickEventListener clickStreamListener = new ClickEventListener() {
    @Override
    public void handleClickEvent(ClickEvent e) {
      VisualPhrase vp = (VisualPhrase) e.getSource();
      processClick(vp);
    }
  };

  public void processClick(VisualPhrase vp) {
    if (currentPath == null)
      return;
    else if (currentPath.phrases.size() == 0)
      currentPath.phrases.add(vp);
    else if (currentPath.phrases.get(currentPath.phrases.size() - 1).getId() == vp
        .getId())
      currentPath.phrases.remove(currentPath.phrases.size() - 1);
    else if (currentPath.phrases.contains(vp))
      return;
    else
      currentPath.phrases.add(vp);
  }

  private class Path {
    public String name = null;
    public boolean enabled = false;
    public List<VisualPhrase> phrases = null;
    public int formatId = -1;
    public int transId = -1;
    public String trans = null;
  }

  public boolean load(File file, File schema) {
    DocumentBuilder parser = XMLUtils.getValidatingXmlParser(schema);
    if (parser == null)
      return false;

    final int minTranslationId = controller.getMinTranslationId();
    final int maxTranslationId = minTranslationId
        + controller.getNumTranslationLayouts() - 1;

    try {
      Document xmlDocument = parser.parse(file);

      Element root = xmlDocument.getDocumentElement();
      NodeList sentences = root.getElementsByTagName(SENTENCE);
      for (int i = 0; i < sentences.getLength(); i++) {
        Element sentence = (Element) sentences.item(i);
        final int translationId = Integer.parseInt(sentence
            .getAttribute(SENT_ID));

        if (translationId < minTranslationId)
          continue;
        else if (translationId > maxTranslationId)
          break;

        if (translationPaths.get(translationId) == null)
          translationPaths.put(translationId, new ArrayList<Path>());

        NodeList xmlPaths = sentence.getElementsByTagName(PATH);
        final int formatOffset = translationPaths.get(translationId).size();
        for (int pathIdx = 0; pathIdx < xmlPaths.getLength(); pathIdx++) {

          // Only allow up MAX_PATHS paths
          if (translationPaths.get(translationId).size() >= MAX_PATHS)
            break;

          Element path = (Element) xmlPaths.item(pathIdx);
          String pathName = path.getAttribute(PATH_NAME);

          // Disallow duplicate path names
          Path p = getPath(translationId, pathName);
          if (p != null)
            continue;

          Path newPath = new Path();
          newPath.name = pathName;
          newPath.formatId = pathIdx + formatOffset;
          newPath.transId = translationId;
          newPath.phrases = new ArrayList<VisualPhrase>();
          StringBuilder newFullTrans = new StringBuilder();

          NodeList phrases = path.getElementsByTagName(PHRASE);
          for (int phraseIdx = 0; phraseIdx < phrases.getLength(); phraseIdx++) {
            Element xmlPhrase = (Element) phrases.item(phraseIdx);

            Phrase phrase = getPhraseFromXml(xmlPhrase);
            newFullTrans.append(phrase.getPhrase() + ' ');

            VisualPhrase vp = controller.lookupVisualPhrase(translationId,
                phrase);
            if (vp != null)
              newPath.phrases.add(vp);
            else if (VERBOSE)
              System.err.printf("%s: While loading [%d / %s], discarded %s\n",
                  this.getClass().getName(), translationId, pathName, phrase);
          }
          newPath.trans = newFullTrans.toString();
          translationPaths.get(translationId).add(newPath);
        }
      }
    } catch (SAXException e) {
      System.err.printf("%s: XML file %s does not conform to schema\n", this
          .getClass().getName(), file.getPath());
      return false;

    } catch (IOException e) {
      System.err.printf("%s: Error reading from %s\n", this.getClass()
          .getName(), file.getPath());
      e.printStackTrace();
      return false;
    }

    isLoaded = true;
    return true;
  }

  private Phrase getPhraseFromXml(Element xmlPhrase) {
    String english = xmlPhrase.getTextContent();
    int start = Integer.parseInt(xmlPhrase.getAttribute(ALGN_START));
    int end = Integer.parseInt(xmlPhrase.getAttribute(ALGN_END));
    double score = Double.parseDouble(xmlPhrase.getAttribute(SCORE));

    Phrase phrase = new Phrase(english, start, end, score);

    return phrase;
  }

  public boolean isLoaded() {
    return isLoaded;
  }

  public boolean save(File file, File schema) {
    DocumentBuilder parser = XMLUtils.getValidatingXmlParser(schema);
    if (parser == null)
      return false;

    try {
      Document xmlDoc = parser.newDocument();

      Element root = xmlDoc.createElement(ROOT);
      root.setAttribute(SYSTEM, "phrase-viewer");
      xmlDoc.appendChild(root);
      for (int translationId : translationPaths.keySet()) {
        // Create the sentence child
        Element xmlSent = xmlDoc.createElement(SENTENCE);
        xmlSent.setAttribute(SENT_ID, Integer.toString(translationId));
        root.appendChild(xmlSent);

        // Write the source
        Element xmlSource = xmlDoc.createElement(SOURCE);
        xmlSent.appendChild(xmlSource);
        String source = controller.getTranslationSource(translationId);
        StringTokenizer st = new StringTokenizer(source);
        while (st.hasMoreTokens()) {
          Element xmlWord = xmlDoc.createElement(WORD);
          xmlWord.setTextContent(st.nextToken());
          xmlSource.appendChild(xmlWord);
        }

        // Write out each path
        for (Path path : translationPaths.get(translationId)) {
          Element xmlPath = xmlDoc.createElement(PATH);
          xmlSent.appendChild(xmlPath);
          xmlPath.setAttribute(PATH_NAME, path.name);
          for (VisualPhrase vp : path.phrases) {
            Phrase p = vp.getPhrase();
            Element alignedPhrase = xmlDoc.createElement(PHRASE);
            alignedPhrase.setAttribute(ALGN_START,
                Integer.toString(p.getStart()));
            alignedPhrase.setAttribute(ALGN_END, Integer.toString(p.getEnd()));
            alignedPhrase.setAttribute(SCORE, Double.toString(p.getScore()));
            alignedPhrase.setTextContent(p.getPhrase());
            xmlPath.appendChild(alignedPhrase);
          }
        }
      }

      // Write the xml document to file
      Transformer transformer = TransformerFactory.newInstance()
          .newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      StreamResult result = new StreamResult(file);
      DOMSource source = new DOMSource(xmlDoc);
      transformer.transform(source, result);

    } catch (DOMException e) {
      System.err.printf("%s: XML DOM while writing path model to %s\n", this
          .getClass().getName(), file.getPath());
      e.printStackTrace();
      return false;

    } catch (TransformerConfigurationException e) {
      System.err.printf("%s: Error writing XML document to %s\n", this
          .getClass().getName(), file.getPath());
      e.printStackTrace();
      return false;

    } catch (IllegalArgumentException e) {
      System.err.printf(
          "%s: Unknown exception while writing path model to %s\n", this
              .getClass().getName(), file.getPath());
      e.printStackTrace();
      return false;

    } catch (TransformerFactoryConfigurationError e) {
      System.err.printf(
          "%s: Unable to create a file writer for XML path model\n", this
              .getClass().getName());
      e.printStackTrace();
      return false;

    } catch (TransformerException e) {
      System.err.printf("%s: Error while writing XML to %s\n", this.getClass()
          .getName(), file.getPath());
      e.printStackTrace();
      return false;
    }

    return true;
  }

  public boolean addPath(int translationId, String name) {
    if (translationPaths.get(translationId) == null)
      translationPaths.put(translationId, new ArrayList<Path>());

    int numPaths = translationPaths.get(translationId).size();
    Path p = getPath(translationId, name);

    if (p == null && numPaths < MAX_PATHS) {
      currentPath = new Path();
      currentPath.transId = translationId;
      currentPath.enabled = true;
      currentPath.name = name.intern();
      currentPath.formatId = numPaths;
      currentPath.phrases = new ArrayList<VisualPhrase>();
      return true;
    }
    return false;
  }

  public int getFormatId(int translationId, String name) {
    if (currentPath != null && currentPath.transId == translationId
        && currentPath.name.equals(name))
      return currentPath.formatId;

    Path p = getPath(translationId, name);
    if (p != null)
      return p.formatId;

    return -1;
  }

  public String getTranslationFromPath(int translationId, String name) {
    Path p = getPath(translationId, name);
    return (p == null) ? null : p.trans;
  }

  public void setTranslation(int translationId, String name, String translation) {
    Path p = getPath(translationId, name);
    if (p != null)
      p.trans = translation;
  }

  public boolean finishPath(int translationId, String name) {
    if (currentPath == null || currentPath.transId != translationId
        || !currentPath.name.equals(name)
        || translationPaths.get(translationId).size() >= MAX_PATHS)
      return false;

    translationPaths.get(translationId).add(currentPath);
    currentPath = null;

    return true;
  }

  public Map<String, List<VisualPhrase>> getPaths(int translationId) {
    if (translationPaths.get(translationId) == null)
      return null;

    Map<String, List<VisualPhrase>> ret = new HashMap<String, List<VisualPhrase>>();
    for (Path p : translationPaths.get(translationId))
      ret.put(p.name.intern(), Collections.unmodifiableList(p.phrases));
    return ret;
  }

  public List<String> getPathNames(int translationId) {
    if (translationPaths.get(translationId) != null) {
      List<String> names = new ArrayList<String>();
      for (Path p : translationPaths.get(translationId))
        names.add(p.name.intern());
      return names;
    }
    return null;
  }

  public void setPathState(boolean isOn, int translationId, String name) {
    Path p = getPath(translationId, name);
    if (p != null)
      p.enabled = isOn;
  }

  public boolean isEnabled(int translationId, String name) {
    Path p = getPath(translationId, name);
    return (p == null) ? false : p.enabled;
  }

  public void deletePath(int translationId, String name) {
    Path p = getPath(translationId, name);
    if (p != null)
      translationPaths.get(translationId).remove(p);
  }

  private Path getPath(int translationId, String name) {
    if (translationPaths.get(translationId) != null)
      for (Path p : translationPaths.get(translationId))
        if (p.name.equals(name))
          return p;
    return null;
  }
}
