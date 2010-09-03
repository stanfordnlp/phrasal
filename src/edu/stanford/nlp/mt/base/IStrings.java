package edu.stanford.nlp.mt.base;

import java.util.*;


/**
 * Utility functions for working with {@link IString}s.
 * 
 * @author danielcer
 * @author mgalley
 * 
 */
public class IStrings {
	
	private IStrings() { super(); }

	static public IString[] toIStringArray(String[] strings) {
		IString[] istrs = new IString[strings.length];
		for (int i = 0; i < istrs.length; i++) {
			istrs[i] = new IString(strings[i]);
		}
		return istrs;
	}

  static public IString[] toSyncIStringArray(String[] strings) {
		IString[] istrs = new IString[strings.length];
		for (int i = 0; i < istrs.length; i++) {
      synchronized (IString.class) {
        istrs[i] = new IString(strings[i]);
      }
		}
		return istrs;
	}

	static public IString[] toIStringArray(Collection<String> strings) {
		IString[] istrs = new IString[strings.size()];
		int i = 0;
		for (String str : strings) {
			istrs[i++] = new IString(str);
		}
		return istrs;
	}

  static public IString[] toSyncIStringArray(Collection<String> strings) {
		IString[] istrs = new IString[strings.size()];
		int i = 0;
		for (String str : strings) {
      synchronized (IString.class) {
        istrs[i++] = new IString(str);
      }
		}
		return istrs;
	}

  static public int[] toIntArray(IString[] strings) {
		int[] intArray = new int[strings.length];
		for (int i = 0; i < strings.length; i++) {
			intArray[i] = strings[i].id;
		}
		return intArray;
	}

  static public String[] toStringArray(IString[] strings) {
    String[] stringArray = new String[strings.length];
		for (int i = 0; i < strings.length; i++) {
			stringArray[i] = strings[i].toString();
		}
		return stringArray;
	}
  
  static public IString[] toIStringArray(int[] ids) {
		IString[] istrs = new IString[ids.length];
		for (int i = 0; i < istrs.length; i++) {
			istrs[i] = new IString(ids[i]);
		}
		return istrs;
	}
  
  static public String[] toStringArray(int[] ids) {
		String[] strs = new String[ids.length];
		for (int i = 0; i < strs.length; i++) {
			strs[i] = IString.getString(ids[i]);
		}
		return strs;
	}
}
