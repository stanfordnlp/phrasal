package mt.base;

import java.util.*;


public class IStrings {
	
	private IStrings() { }
	
	static public IString[] toIStringArray(String[] strings) {
		IString[] istrs = new IString[strings.length];
		for (int i = 0; i < istrs.length; i++) {
			istrs[i] = new IString(strings[i]);
		}
		return istrs;
	}
	
	static public IString[] toIStringArray(List<String> strings) {
		IString[] istrs = new IString[strings.size()];
		int i = 0;
		for (String str : strings) {
			istrs[i++] = new IString(str);
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
			strs[i] = new String(IString.getString(ids[i]));
		}
		return strs;
	}
}
