package edu.berkeley.nlp.lm.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Stack;

/**
 * Basic logging singleton class. The underlying LogInterface instance can be
 * changed to customize logging behaviour by calling setGlobalLogger.
 * 
 * @author adampauls
 * 
 */
@SuppressWarnings("ucd")
public class Logger
{

	/**
	 * Logging interface.
	 * 
	 * @author adampauls
	 * 
	 */
	public static interface LogInterface
	{
		/**
		 * Log a string, but only once in a while. This is useful when we are
		 * logging frequently and only wish to see lines every second or so. How
		 * often logging is done is up to the implementation.
		 * 
		 * @param s
		 *            printf style string
		 * @param args
		 *            printf args
		 */
		public void logs(String s, Object... args);

		/**
		 * Always log this string.
		 * 
		 * @param s
		 */
		public void logss(String s);

		public void logss(String string, Object... args);

		/**
		 * Start a track (a function, or some other logical unit of computation)
		 * with a name given by <code>s</code>.
		 * 
		 * @param s
		 */
		public void startTrack(String s);

		/**
		 * Ends a track, printing out how long the track took.
		 */
		public void endTrack();

		public void dbg(String s);

		public void err(String s);

		public void err(String s, Object... args);

		public void warn(String s);

		public void warn(String string, Object... args);

	}

	/**
	 * Logs to System.out and System.err
	 * 
	 * @author Aria Haghighi
	 * 
	 */
	@SuppressWarnings("ucd")
	public static class SystemLogger implements LogInterface
	{

		private final PrintStream out;

		private final PrintStream err;

		private int trackLevel = 0;

		private final boolean debug = true;

		public SystemLogger(final PrintStream out, final PrintStream err) {
			this.out = out;
			this.err = err;
		}

		public void close() {
			if (out != null) {
				out.close();
			}
			if (err != null) {
				err.close();
			}
		}

		public SystemLogger(final String outFile, final String errFile) throws FileNotFoundException {
			this(outFile != null ? new PrintStream(new FileOutputStream(outFile)) : null, errFile != null ? new PrintStream(new FileOutputStream(errFile))
				: null);
		}

		public SystemLogger() {
			this(System.out, System.err);
		}

		private final Stack<Long> trackStartTimes = new Stack<Long>();

		private String getIndentPrefix() {
			final StringBuilder builder = new StringBuilder();
			for (int i = 0; i < trackLevel; ++i) {
				builder.append("\t");
			}
			return builder.toString();
		}

		private void output(final PrintStream o, final String txt) {
			if (o == null) return;
			final String[] lines = txt.split("\n");
			final String prefix = getIndentPrefix();
			for (final String line : lines) {
				o.println(prefix + line);
			}
		}

		@Override
		public void dbg(final String s) {
			if (debug) output(out, "[dbg] " + s);
		}

		private String timeString(final double milliSecs_) {
			double milliSecs = milliSecs_;
			String timeStr = "";
			final int hours = (int) (milliSecs / (1000 * 60 * 60));
			if (hours > 0) {
				milliSecs -= hours * 1000 * 60 * 60;
				timeStr += hours + "h";
			}
			final int mins = (int) (milliSecs / (1000 * 60));
			if (mins > 0) {
				milliSecs -= mins * 1000.0 * 60.0;
				timeStr += mins + "m";
			}
			final int secs = (int) (milliSecs / 1000.0);
			//if (secs > 0) {
			//milliSecs -= secs * 1000.0;
			timeStr += secs + "s";
			//}

			return timeStr;
		}

		@Override
		public void endTrack() {
			String timeStr = null;
			synchronized (this) {
				trackLevel--;
				final double milliSecs = System.currentTimeMillis() - trackStartTimes.pop();
				timeStr = timeString(milliSecs);
			}
			output(out, "} " + (timeStr != null ? "[" + timeStr + "]" : ""));
		}

		@Override
		public void err(final String s) {
			err.println(s);
		}

		public void logs(final String s) {
			output(out, s);
		}

		@Override
		public void logss(final String s) {
			output(out, s);
		}

		@Override
		public void startTrack(final String s) {
			output(out, s + " {");
			synchronized (this) {
				trackLevel++;
				trackStartTimes.push(System.currentTimeMillis());
			}
		}

		@Override
		public void warn(final String s) {
			output(err, "[warn] " + s);
		}

		@Override
		public void logs(final String s, final Object... args) {
			logs(String.format(s, args));
		}

		@Override
		public void err(final String s, final Object... args) {
			output(err, "[err] " + String.format(s, args));
		}

		@Override
		public void warn(final String string, final Object... args) {
			warn(String.format(string, args));
		}

		@Override
		public void logss(final String string, final Object... args) {
			logss(String.format(string, args));
		}
	}

	/**
	 * Default logging goes nowhere.
	 * 
	 * @author adampauls
	 * 
	 */
	@SuppressWarnings("ucd")
	public static class NullLogger implements LogInterface
	{

		@Override
		public void logs(final String s, final Object... args) {
		}

		@Override
		public void logss(final String s) {
		}

		@Override
		public void startTrack(final String s) {
		}

		@Override
		public void endTrack() {
		}

		@Override
		public void dbg(final String s) {
		}

		@Override
		public void err(final String s) {
		}

		@Override
		public void err(final String s, final Object... args) {
		}

		@Override
		public void warn(final String s) {
		}

		@Override
		public void warn(final String string, final Object... args) {
		}

		@Override
		public void logss(final String string, final Object... args) {
		}

	}

	/**
	 * Convenience class for stringing together loggers.
	 * 
	 * @author adampauls
	 * 
	 * 
	 */
	@SuppressWarnings("ucd")
	public static class CompoundLogger implements LogInterface
	{
		private final LogInterface[] loggers;

		public CompoundLogger(final LogInterface... loggers) {
			this.loggers = loggers;
		}

		@Override
		public void logs(final String s, final Object... args) {
			for (final LogInterface logger : loggers) {
				logger.logs(s, args);
			}
		}

		@Override
		public void logss(final String s) {
			for (final LogInterface logger : loggers) {
				logger.logss(s);
			}
		}

		@Override
		public void startTrack(final String s) {
			for (final LogInterface logger : loggers) {
				logger.startTrack(s);
			}
		}

		@Override
		public void endTrack() {
			for (final LogInterface logger : loggers) {
				logger.endTrack();
			}
		}

		@Override
		public void dbg(final String s) {
			for (final LogInterface logger : loggers) {
				logger.dbg(s);
			}
		}

		@Override
		public void err(final String s) {
			for (final LogInterface logger : loggers) {
				logger.err(s);
			}
		}

		@Override
		public void err(final String s, final Object... args) {
			for (final LogInterface logger : loggers) {
				logger.err(s, args);
			}
		}

		@Override
		public void warn(final String s) {
			for (final LogInterface logger : loggers) {
				logger.warn(s);
			}
		}

		@Override
		public void warn(final String string, final Object... args) {
			for (final LogInterface logger : loggers) {
				logger.warn(string, args);
			}
		}

		@Override
		public void logss(final String string, final Object... args) {
			for (final LogInterface logger : loggers) {
				logger.logss(string, args);
			}
		}
	}

	public synchronized static void setGlobalLogger(final LogInterface logger) {
		instance = logger;
	}

	public synchronized static LogInterface getGlobalLogger() {
		return instance;
	}

	private static LogInterface instance = new NullLogger();

	public static LogInterface i() {
		return instance;
	}

	public static void setLogger(final LogInterface i) {
		instance = i;
	}

	public static void logs(final String s) {
		i().logs(s);
	}

	// Static Logger Methods
	public static void logs(final String s, final Object... args) {
		i().logs(s, args);
	}

	public static void logss(final String s) {
		i().logss(s);
	}

	public static void startTrack(final String s, final Object... args) {
		i().startTrack(String.format(s, args));
	}

	public static void endTrack() {
		i().endTrack();
	}

	public static void dbg(final String s) {
		i().dbg(s);
	}

	public static void err(final String s) {
		i().err(s);
	}

	public static void err(final String s, final Object... args) {
		i().err(s, args);
	}

	public static void warn(final String s) {
		i().warn(s);
	}

	public static void warn(final String string, final Object... args) {
		i().warn(string, args);
	}

	public static void logss(final String string, final Object... args) {
		i().logss(string, args);
	}

}
