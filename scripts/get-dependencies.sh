#!/usr/bin/env bash 
#
# Download external dependencies for Phrasal.
#

# Download TERp, since BBN's license 
# won't let us redistribute it with Phrasal 
wget http://www.umiacs.umd.edu/~snover/terp/downloads/terp.v1.tgz
tar -xvf terp.v1.tgz --wildcards --no-anchored '*.jar'

mkdir -p lib-nodistrib
mv terp.v1/dist/lib/*.jar lib-nodistrib

JETTY_VERSION=9.2.1.v20140609
wget http://download.eclipse.org/jetty/$JETTY_VERSION/dist/jetty-distribution-$JETTY_VERSION.tar.gz

# Download jetty for the web service
tar -xzf jetty-distribution-$JETTY_VERSION.tar.gz
mv jetty-distribution-$JETTY_VERSION/lib/jetty-*jar lib-nodistrib

# Cleanup
rm -rf terp* jetty-*

