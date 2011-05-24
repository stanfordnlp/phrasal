package edu.berkeley.nlp.lm.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A thread manager for executing many tasks safely using a fixed number of
 * threads.
 * 
 * @author John DeNero
 * @author Adam Pauls
 */
public class WorkQueue
{

	private static final long WAIT_TIME = 10;

	private final ExecutorService executor;

	private final Semaphore sem;

	private final boolean serialExecution;

	private final boolean dieOnException;

	public WorkQueue(final int numThreads, final boolean dieOnException) {
		this.dieOnException = dieOnException;
		if (numThreads == 0) {
			serialExecution = true;
			sem = null;
			executor = null;
		} else {
			executor = Executors.newFixedThreadPool(numThreads);
			sem = new Semaphore(numThreads);
			serialExecution = false;
		}
	}

	public void execute(final Runnable work) {
		if (serialExecution) {
			work.run();
		} else {
			sem.acquireUninterruptibly();
			executor.execute(new Runnable()
			{

				@Override
				public void run() {
					if (!dieOnException) {
						try {
							work.run();
						} catch (final AssertionError e) {

							final StringWriter stringWriter = new StringWriter();
							e.printStackTrace(new PrintWriter(stringWriter));
							Logger.err(e.toString());
							Logger.err(stringWriter.toString());
						} catch (final RuntimeException e) {

							final StringWriter stringWriter = new StringWriter();
							e.printStackTrace(new PrintWriter(stringWriter));
							Logger.err(e.toString());
							Logger.err(stringWriter.toString());
						}
					} else {
						try {
							work.run();
						} catch (final Throwable e) {
							sem.release();
							final StringWriter stringWriter = new StringWriter();

							e.printStackTrace(new PrintWriter(stringWriter));
							Logger.err(e.toString());
							Logger.err(stringWriter.toString());
							throw new RuntimeException(e);
						}

					}
					sem.release();
				}
			});
		}
	}

	public void finishWork() {
		if (serialExecution) return;
		executor.shutdown();
		try {
			int secs = 0;
			while (!executor.awaitTermination(WAIT_TIME, TimeUnit.SECONDS)) {
				secs += WAIT_TIME;
			}
		} catch (final InterruptedException e) {
			throw new RuntimeException("Work queue interrupted");
		}
	}

}
