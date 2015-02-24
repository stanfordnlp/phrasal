#!/usr/bin/env bash 
#
# Download external dependencies for Phrasal.
#

# Download TERp, since BBN's license 
# won't let us redistribute it with Phrasal 
# TODO(spenceg) 23 February 2015 -- This download is no longer available,
# so distribute the legacy jars with Phrasal.
#echo Downloading TERp...
#wget http://www.umiacs.umd.edu/~snover/terp/downloads/terp.v1.tgz
#tar -xzf terp.v1.tgz

#mkdir -p lib
#mv terp.v1/dist/lib/*.jar lib

# Download jetty for the web service
#
echo Downloading Jetty...
JETTY_VERSION=9.2.1.v20140609
wget http://archive.eclipse.org/jetty/$JETTY_VERSION/dist/jetty-distribution-$JETTY_VERSION.tar.gz
tar -xzf jetty-distribution-$JETTY_VERSION.tar.gz
mv jetty-distribution-$JETTY_VERSION/lib/jetty-*jar lib

