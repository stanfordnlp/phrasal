package mt.train;

import java.io.*;
import java.util.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import mt.base.Sequence;
import mt.base.SimpleSequence;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

/**
 *  @author Michel Galley
 */

public class IBMWordAlignmentHandler extends DefaultHandler {

  enum TagType { SSTR, TSTR, AL, OTHER };

  private static final double MINP = 0.5;

  private List<SymmetricalWordAlignment> align = new ArrayList<SymmetricalWordAlignment>();

  private Sequence<IString> f=null;
  private Sequence<IString> e=null;
  private Set<Integer>[] f2e;
  private Set<Integer>[] e2f;

  private TagType tagType;
  private int fLen, eLen;
  private StringBuffer buf = new StringBuffer();

  /**
	 * Constructor initializing state. 
	 */
	public IBMWordAlignmentHandler() {}

	/** 
	 * Read XML from input stream and parse, generating SAX events.
	 */
	public void readXML(InputStream inStream) {
		try {
				System.setProperty("org.xml.sax.driver", "org.apache.crimson.parser.XMLReaderImpl");
				XMLReader reader = XMLReaderFactory.createXMLReader();
				reader.setContentHandler(this);
				reader.parse(new InputSource(new InputStreamReader(inStream)));
		} catch (Exception e) {
				e.printStackTrace();
		}
	}
	
	@Override
	public void startDocument() throws SAXException {
			//System.err.println("Starting Document.");
	}

	@Override
	public void endDocument() throws SAXException {
			//System.err.println("Ending Document.");
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public void startElement (String namespaceURI,
														String localName,
														String qName, 
														Attributes atts)
			throws SAXException {
    tagType = TagType.OTHER;
    if("bead".equals(localName)) {
      fLen = Integer.parseInt(atts.getValue("src_leng"));
      eLen = Integer.parseInt(atts.getValue("tgt_leng"));
    } else if("sstr".equals(localName)) {
      tagType = TagType.SSTR;
    } else if("tstr".equals(localName)) {
      tagType = TagType.TSTR;
    } else if("alignment".equals(localName)) {
      tagType = TagType.AL;
      f2e = new Set[fLen];
      for(int i=0; i<fLen; ++i) f2e[i] = new TreeSet();
      e2f = new Set[eLen];
      for(int i=0; i<eLen; ++i) e2f[i] = new TreeSet();
    }
    //System.err.printf("start: %s\n",localName);
	}

	@Override
	public void endElement(String namespaceURI, 
												 String localName,
												 String qName)
			throws SAXException {
		//System.err.printf("end: %s\n",localName);
    if(tagType != TagType.OTHER) {
      String str = buf.toString().trim();
      if(str.isEmpty())
        return;
      //System.err.printf("  chars: type=%s str={{{%s}}}\n", tagType.name(), str);
      if(tagType == TagType.SSTR) {
        f = new SimpleSequence<IString>(true, IStrings.toIStringArray(str.split("\\s+")));
        if(f.size() != fLen)
          throw new RuntimeException("length mismatch: "+f.size()+" != "+fLen);
      } else if(tagType == TagType.TSTR) {
        e = new SimpleSequence<IString>(true, IStrings.toIStringArray(str.split("\\s+")));
        if(e.size() != eLen)
          throw new RuntimeException("length mismatch: "+e.size()+" != "+eLen);
      } else if(tagType == TagType.AL) {
        for(String line : str.split("\\n+")) {
          String[] els = line.split("\\s+");
          if(els.length != 3)
            throw new RuntimeException("incorrect number of cols: "+els.length+" in "+str);
          if(Double.parseDouble(els[2]) >= MINP) {
            int fi = Integer.parseInt(els[0]);
            for(String eiStr : els[1].split(",")) {
              int ei = Integer.parseInt(eiStr);
              if(fi > 0 && ei > 0) {
                f2e[fi-1].add(ei-1);
                e2f[ei-1].add(fi-1);
              }
            }
          }
        }
        align.add(new SymmetricalWordAlignment(f,e,f2e,e2f));
      }
    }
    buf = new StringBuffer();
  }

	@Override
	public void characters (char[] ch, int start, int length)
			throws SAXException {
    String str = new String(ch,start,length);
    buf.append(str);
  }

  public SymmetricalWordAlignment[] getIBMWordAlignment() {
    return align.toArray(new SymmetricalWordAlignment[align.size()]);
  }

  public static void main(String[] args) {
    for(String arg : args) {
      SymmetricalWordAlignment[] aligns = SymmetricalWordAlignment.readFromIBMWordAlignment(arg);
      for(SymmetricalWordAlignment a: aligns)
        System.out.println(a.toReverseString1());
    }
  }
}
