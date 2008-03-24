package mt;

import java.util.*;
/**
 * 
 * @author danielcer
 *
 */
class FactoryUtil {
	
	/**
	 * 
	 * @param specs
	 * @return
	 */
	public static Map<String,String> getParamPairs(String[] specs) {
		Map<String,String> paramPairs = new HashMap<String, String>();
	
		for (int i = 1; i < specs.length; i++) {
			String[] fields = specs[i].split(":");
			String key, value;
			if (fields.length == 1) {
				key = fields[0];
				value = "";
			} else if (fields.length == 2) {				
				key = fields[0];
				value = fields[1];
			} else {
				throw new RuntimeException(String.format("Invalid parameter pair '%s'", specs[i]));
			}
			paramPairs.put(key, value);
		}
		
		return paramPairs;
	}
}
