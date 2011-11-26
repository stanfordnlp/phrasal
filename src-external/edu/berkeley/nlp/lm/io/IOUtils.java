package edu.berkeley.nlp.lm.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.berkeley.nlp.lm.util.StrUtils;

/**
 * Some IO utility functions. Naming convention: "Hard" means that the function
 * throws a RuntimeException upon failure, "Easy" means it returns null.
 * 
 * @author adampauls
 * @author Percy Liang
 * 
 */
@SuppressWarnings("ucd")
public class IOUtils
{

	public static BufferedReader openIn(final String path) throws IOException {
		return openIn(new File(path));
	}

	public static BufferedReader openIn(final File path) throws IOException {
		InputStream is = new FileInputStream(path);
		if (path.getName().endsWith(".gz")) is = new GZIPInputStream(is);
		return new BufferedReader(getReader(is));
	}

	public static BufferedReader openInHard(final String path) {
		return openInHard(new File(path));
	}

	public static BufferedReader openInHard(final File path) {
		try {
			return openIn(path);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static PrintWriter openOut(final String path) throws IOException {
		return openOut(new File(path));
	}

	public static PrintWriter openOut(final File path) throws IOException {
		OutputStream os = new FileOutputStream(path);
		if (path.getName().endsWith(".gz")) os = new GZIPOutputStream(os);
		return new PrintWriter(getWriter(os));
	}

	public static PrintWriter openOutEasy(final String path) {
		if (StrUtils.isEmpty(path)) return null;
		return openOutEasy(new File(path));
	}

	public static PrintWriter openOutEasy(final File path) {
		if (path == null) return null;
		try {
			return openOut(path);
		} catch (final Exception e) {
			return null;
		}
	}

	public static PrintWriter openOutHard(final String path) {
		return openOutHard(new File(path));
	}

	public static PrintWriter openOutHard(final File path) {
		try {
			return openOut(path);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	// }

	// Java binary serialization {
	// openObjIn
	public static ObjectInputStream openObjIn(final String path) throws IOException {
		return openObjIn(new File(path));
	}

	public static ObjectInputStream openObjIn(final File path) throws IOException {
		final InputStream fis = new BufferedInputStream(new FileInputStream(path));
		return path.getName().endsWith(".gz") ? new ObjectInputStream(new GZIPInputStream(fis)) : new ObjectInputStream(fis);
	}

	// openObjOut
	public static ObjectOutputStream openObjOut(final String path) throws IOException {
		return openObjOut(new File(path));
	}

	public static ObjectOutputStream openObjOut(final File path) throws IOException {
		final OutputStream fos = new BufferedOutputStream(new FileOutputStream(path));
		return path.getName().endsWith(".gz") ? new ObjectOutputStream(new GZIPOutputStream(fos)) : new ObjectOutputStream(fos);
	}

	// readObjFile
	public static Object readObjFile(final String path) throws IOException, ClassNotFoundException {
		return readObjFile(new File(path));
	}

	public static Object readObjFile(final File path) throws IOException, ClassNotFoundException {
		final ObjectInputStream in = openObjIn(path);
		final Object obj = in.readObject();
		in.close();
		return obj;
	}

	public static Object readObjFileEasy(final String path) {
		if (StrUtils.isEmpty(path)) return null;
		return readObjFileEasy(new File(path));
	}

	public static Object readObjFileEasy(final File path) {
		if (path == null) return null;
		try {
			return readObjFile(path);
		} catch (final Exception e) {
			return null;
		}
	}

	public static Object readObjFileHard(final String path) {
		return readObjFileHard(new File(path));
	}

	public static Object readObjFileHard(final File path) {
		try {
			return readObjFile(path);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	// writeObjFile
	public static void writeObjFile(final File path, final Object obj) throws IOException {
		final ObjectOutputStream out = openObjOut(path);
		out.writeObject(obj);
		out.close();
	}

	public static boolean writeObjFileEasy(final File path, final Object obj) {
		if (path == null) return false;
		try {
			writeObjFile(path, obj);
			return true;
		} catch (final Exception e) {
			return false;
		}
	}

	public static void writeObjFileHard(final String path, final Object obj) {
		writeObjFileHard(new File(path), obj);
	}

	public static void writeObjFileHard(final File path, final Object obj) {
		try {
			writeObjFile(path, obj);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	// }

	public static boolean closeEasy(final BufferedReader in) {
		try {
			in.close();
			return true;
		} catch (final IOException e) {
			return false;
		}
	}

	// Copying files {
	// Return number of bytes copied
	public static int copy(final InputStream in, final OutputStream out) throws IOException {
		final byte[] buf = new byte[16384];
		int total = 0, n;
		while ((n = in.read(buf)) != -1) {
			total += n;
			out.write(buf, 0, n);
		}
		out.flush();
		return total;
	}

	// Return number of characters copied
	public static int copy(final Reader in, final Writer out) throws IOException {
		final char[] buf = new char[16384];
		int total = 0, n;
		while ((n = in.read(buf)) != -1) {
			total += n;
			out.write(buf, 0, n);
		}
		out.flush();
		return total;
	}

	// }

	public static Iterator<String> lineIterator(final String path) throws IOException {
		final BufferedReader reader = IOUtils.openIn(path);
		return lineIterator(reader);
	}

	/**
	 * @param reader
	 */
	public static Iterator<String> lineIterator(final BufferedReader reader) {
		return new Iterator<String>()
		{

			private String line;

			@Override
			public boolean hasNext() {
				// TODO Auto-generated method stub
				try {
					return nextLine();
				} catch (final Exception e) {
					e.printStackTrace();
				}
				return false;
			}

			private boolean nextLine() throws IOException {
				if (line != null) { return true; }
				line = reader.readLine();
				return line != null;
			}

			@Override
			public String next() {
				// TODO Auto-generated method stub
				try {
					nextLine();
					final String retLine = line;
					line = null;
					return retLine;
				} catch (final IOException e) {
					throw new RuntimeException();
				}
			}

			@Override
			public void remove() {
				// TODO Auto-generated method stub
				throw new RuntimeException("remove() not supported");
			}

		};
	}

	public static List<String> readLines(final BufferedReader in) throws IOException {
		String line;
		final List<String> lines = new ArrayList<String>();
		while ((line = in.readLine()) != null)
			lines.add(line);
		return lines;
	}

	public static List<String> readLinesEasy(final String path) {
		try {
			return readLines(path);
		} catch (final IOException e) {
			return Collections.emptyList();
		}
	}

	public static List<String> readLinesHard(final String path) {
		try {
			return readLines(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Return the first line, null if it doesn't exist
	public static String readLine(final String path) throws IOException {
		final BufferedReader in = IOUtils.openIn(path);
		final String line = in.readLine();
		in.close();
		return line;
	}

	public static String readLineEasy(final String path) {
		try {
			return readLine(path);
		} catch (final IOException e) {
			return null;
		}
	}

	public static List<String> readLines(final String path) throws IOException {

		final BufferedReader in = IOUtils.openIn(path);
		final List<String> list = readLines(in);
		in.close();
		return list;
	}

	//private static String charEncoding = "ISO-8859-1";
	private static String charEncoding = "UTF-8";

	public static String getCharEncoding() {
		return charEncoding;
	}

	public static void setCharEncoding(final String charEncoding) {
		if (StrUtils.isEmpty(charEncoding)) return;
		IOUtils.charEncoding = charEncoding;
	}

	public static BufferedReader getReader(final InputStream in) throws IOException {
		return new BufferedReader(new InputStreamReader(in, getCharEncoding()));
	}

	public static PrintWriter getWriter(final OutputStream out) throws IOException {
		return new PrintWriter(new OutputStreamWriter(out, getCharEncoding()), true);
	}

}
