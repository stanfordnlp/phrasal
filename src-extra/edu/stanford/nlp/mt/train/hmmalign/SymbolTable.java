package edu.stanford.nlp.mt.train.hmmalign;

import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * This class will make sure every word-tag-etc pair has a unique Id. Also the
 * objects will not be created too many times - just once. The Buffer in
 * SentenceHandler will hold references to these objects
 * <p>
 * There are two stages. First all Word-Tag pairs are put into a hashmap and get
 * their Ids then the Ids are rearranged so that the Id of s
 * &lt;wordId,tagId&gt; pair in which one of the fields is missing will be the
 * Id of the thing that is there.
 * 
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class SymbolTable {

  private HashMap<WordEx, WordEx> initialMap = new HashMap<WordEx, WordEx>();
  private int numBaseIds;
  private int numComplexIds;
  private int numAllIds;
  private int maxSimpleIds;
  private int numWords = 2; // the null word + the eos word
  private int numTags = 2; // the null tag + the eos tag
  private WordEx[] entries;
  WordEx empty;
  private WordEx eos;

  public SymbolTable() {
    empty = new WordEx(0, 0, 0, 0);
  }

  private String[] names; // this will be read from the dictionary , the names
                          // of complex entries will be word_tag

  public WordEx getEntry(WordEx key) {

    if (initialMap.containsKey(key)) {
      return (initialMap.get(key));
    } else {
      initialMap.put(key, key);
      if ((key.getWordId() == 0) || (key.getTagId() == 0)) {
        numBaseIds++;
        int max = key.getWordId();
        if (max > 0) {
          numWords++;
        } else {
          numTags++;
        }
        if (key.getTagId() > max) {
          max = key.getTagId();
        }
        if (maxSimpleIds < max) {
          maxSimpleIds = max;
        }
        return key;
      } else {
        numComplexIds++;
      }
      return key;
    }

  }

  public WordEx getEos() {
    return eos;
  }

  public WordEx getEntry(int index) {
    return entries[index];
  }

  public void print() {
    System.out.println("Base entries " + numBaseIds + " total " + numAllIds);

    for (int i = 0; i < entries.length; i++) {
      if (entries[i] != null) {
        System.out.println("Index is " + i);
        entries[i].print();
      }

    }
  }

  public int getNumBaseIds() {
    return this.numBaseIds;
  }

  public int getNumAllIds() {
    return this.numAllIds;
  }

  public int getNumWords() {
    return this.numWords;
  }

  public int getNumTags() {
    return this.numTags;
  }

  public int getMaxSimpleIds() {
    return this.maxSimpleIds;
  }

  public void reorganizeTable() {
    // create an array in entries to keep all the WordEx objects
    // the index in each WordEx should be set
    int baseSize = maxSimpleIds + 4; // these four are for the empty word and
                                     // its tag and the eos word and its tag
    numAllIds = maxSimpleIds + numComplexIds;
    entries = new WordEx[numAllIds + 5]; // five be/ in addition to those for
                                         // above i want to have one complex
                                         // entry for eos+eos_tag
    WordEx[] keys = initialMap.keySet().toArray(
        new WordEx[initialMap.keySet().size()]);
    // if(numAllIds!=keys.length){System.out.println("Something wrong here in reorganizeTable");}
    for (WordEx current : keys) {
      int index = 0;
      if (current.isSimple()) {
        if (current.getWordId() > index) {
          index = current.getWordId();
        } else {
          index = current.getTagId();
        }
        current.setIndex(index);
        entries[index] = current;
      } else {
        index = baseSize++;
        current.setIndex(index);
        entries[index] = current;
      }

    }

    entries[0] = empty;
    empty.set(0, maxSimpleIds + 1);
    entries[maxSimpleIds + 1] = new WordEx(0, maxSimpleIds + 1,
        maxSimpleIds + 1, empty.getCount());
    entries[maxSimpleIds + 2] = new WordEx(maxSimpleIds + 2, 0,
        maxSimpleIds + 2, empty.getCount()); // the EOS word
    entries[maxSimpleIds + 3] = new WordEx(0, maxSimpleIds + 3,
        maxSimpleIds + 3, empty.getCount());// the EOS tag
    numAllIds += 5;
    if (entries[numAllIds - 1] != null) {
      System.exit(-1);
    }
    entries[numAllIds - 1] = new WordEx(maxSimpleIds + 2, maxSimpleIds + 3,
        numAllIds - 1, empty.getCount());// the EOS_T!EOS pair
    eos = entries[numAllIds - 1];// keep the eos here
    System.out.println("numbase ids " + this.getNumBaseIds() + " num words "
        + numWords + " num tags " + numTags + " complex ids " + numComplexIds
        + " all " + numAllIds);

  }

  public String getName(int id) {
    return names[id];

  }

  public void readDictionary(String filename) {
    // first read in the dictionary in names and then
    names = new String[entries.length];

    try {
      InFile in = new InFile(filename);
      String name;
      for (String line; (line = in.readLine()) != null;) {
        StringTokenizer st = new StringTokenizer(line, "\t");
        int id = Integer.parseInt(st.nextToken());
        name = st.nextToken();

        if (id >= entries.length) {
          continue;
        }
        if (entries[id] == null) {
          continue;
        }
        if (entries[id].isSimple()) {
          names[id] = name;
          // System.out.println("put "+id+" "+names[id]);
        }

      }// while
      in.close();

      System.out.println("Dictionary read");
      // now create the names for the other entries as well;
      // the ones that have tags
      names[0] = "NULL";
      names[maxSimpleIds + 1] = "T!NULL";
      names[maxSimpleIds + 2] = "S_EOS";
      names[maxSimpleIds + 3] = "T!S_EOS";
      for (int i = 0; i < entries.length; i++) {
        WordEx w = entries[i];
        if (w == null) {
          continue;
        }
        if (!w.isSimple()) {
          name = names[w.getWordId()] + "_" + names[w.getTagId()];
          names[i] = name;

        }// f

      }// for

    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }

  }

}
