package edu.berkeley.nlp.lm.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class Annotations
{

	/**
	 * Just a fancy-pants comment.
	 * 
	 * @author adampauls
	 * 
	 */
	public @interface OutputParameter
	{
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	/**
	 * Fields annotated with this annotation will <b>not</b> have their memory usage counted towards total
	 * memory usage.
	 * @author adampauls
	 *
	 */
	public @interface SkipMemoryCount
	{
	}

	/**
	 * Fields annotated with this annotation will have their memory usage added
	 * to the memory usage map returned by countApproximateMemoryUsage. Fields
	 * without this annotation will be counted towards total memory usage but
	 * not printed specifically.
	 * 
	 * @author adampauls
	 * 
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface PrintMemoryCount
	{

	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	/**
	 * Annotation which documents command line options. 
	 */
	public @interface Option
	{
		String name() default "";

		String gloss() default "";

		boolean required() default false;

		String[] tags() default {};

		// Conditionally required option, e.g.
		//   - "main.operation": required only when main.operation specified
		//   - "main.operation=op1": required only when main.operation takes on value op1
		//   - "operation=op1": the group of the option is used
		String condReq() default "";
	}

}
