#!/usr/bin/env bash 
#
# Download external dependencies for Phrasal.
#

# Download TERp, since BBN's license 
# won't let us redistribute it with Phrasal 
echo Downloading TERp...
wget http://www.umiacs.umd.edu/~snover/terp/downloads/terp.v1.tgz
tar -xzf terp.v1.tgz

mkdir -p lib-nodistrib
mv terp.v1/dist/lib/*.jar lib-nodistrib

# Download jetty for the web service
#
echo Downloading Jetty...
JETTY_VERSION=9.2.1.v20140609
wget http://download.eclipse.org/jetty/$JETTY_VERSION/dist/jetty-distribution-$JETTY_VERSION.tar.gz
tar -xzf jetty-distribution-$JETTY_VERSION.tar.gz
mv jetty-distribution-$JETTY_VERSION/lib/jetty-*jar lib-nodistrib

# Download gson for the web service
#
echo Downloading Gson...
wget http://google-gson.googlecode.com/files/google-gson-2.2.4-release.zip
unzip google-gson-2.2.4-release.zip
mv google-gson-2.2.4/gson-2.2.4.jar lib-nodistrib

# Cleanup
rm -rf terp* jetty-* google-*

