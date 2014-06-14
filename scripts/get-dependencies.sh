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

# Cleanup
rm -rf terp*

