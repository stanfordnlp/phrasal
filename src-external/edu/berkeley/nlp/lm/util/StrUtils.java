package edu.berkeley.nlp.lm.util;

import java.util.List;

public class StrUtils
{

	public static <T> String join(final List<T> objs) {
		return join(objs, " ");
	}

	public static <T> String join(final List<T> objs, final String delim) {
		if (objs == null) return "";
		return join(objs, delim, 0, objs.size());
	}

	public static <T> String join(final List<T> objs, final String delim, final int start, final int end) {
		if (objs == null) return "";
		final StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (int i = start; i < end; i++) {
			if (!first) sb.append(delim);
			sb.append(objs.get(i));
			first = false;
		}
		return sb.toString();
	}

	public static boolean isEmpty(final String s) {
		return s == null || s.equals("");
	}

}
