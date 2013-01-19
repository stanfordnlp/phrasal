Classmexer beta version 0.03
============================

Written by Neil Coffey
Copyright (c) Javamex UK 2009.
All rights reserved.
http://www.javamex.com/classmexer/

1. Introduction
---------------

Classmexer is a simple Java profiling/instrumentation agent. This publicly released
beta version currently provides a couple of simple calls to query the JVM for
the memory use of an object or group of objects.

To use Classmexer, you need a Java Virtual Machine supporting Java specification 1.5 or
above, and which supports the Java Instrumentation framework.

2. Methods provided
-------------------

Once properly installed in your application (see below), Classmexer currently provides
the following public static methods via the com.javamex.classmexer.MemoryUtil class:

  public static long memoryUsageOf(Object obj)
  public static long deepMemoryUsageOf(Object obj)
  public static long deepMemoryUsageOf(Object obj, VisibilityFilter referenceFilter)
  public static long deepMemoryUsageOfAll(Collection<? extends Object> objs)
  public static long deepMemoryUsageOfAll(Collection<? extends Object> objs,
                                          VisibilityFilter referenceFilter)

These calls provide an estimate, based on information supplied by the running JVM, of
the number of bytes taken up on the heap by the given Java object. The "deep" count
includes the memory taken up by all objects referenced by the given object(s), and is
calculated recursively. The calls ensure that, where an object is referred to multiple
times, its memory usage is only included once.

For more information, see the Javadoc at:

  http://www.javamex.com/classmexer/api/

3. How to use Classmexer in your project
----------------------------------------

There are two steps to using the MemoryUtil methods provided by Classmexer in your
project:
(a) you need to include classmexer.jar in your project, so that you can compile
    against it (or include it in your classpath if compiling from the command line);
(b) when you run your application, include classmexer.jar as an agent.

In Hotspot, step (b) is accomplished by adding the following parameter to the java
startup command:

      -javaagent:classmexer.jar

For example:

   java -javaagent:classmexer.jar mypackage/MyClass

4. Comments/bug reports
-----------------------

Please post comments and bug reports at Classmexer's blog entry at the following
URL:

  http://javamex.blogspot.com/2008/12/beta-classmexer-agent.html

Licence and Copyright Information
---------------------------------

The contents of classmexer.jar and the accompanying documentation ("the Software") is
written by Neil Coffey ("the Author") and is provided "as is", in good faith,
without any guarantee whatsoever.

You use the Software at your own risk. In using the Software, you agree that you are
responsible for all consequences of your use of the Software. You agree not to hold the
Author liable for any loss or damage caused, directly or indirectly, by the use of
the Software.

The Software is copyright (c) Javamex UK 2009. You may use Classmexer version 0.03
(the version supplied alongside this README file) free of charge. However, you may
NOT redistribute the Software to third parties without express permission. Instead,
please direct interested third parties to download the software from the following URL:

  http://www.javamex.com/classmexer/
