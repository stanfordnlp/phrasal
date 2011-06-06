#!/bin/sh 

# Download TER and TERp, since BBN's license 
# won't let us redistribute it with Phrasal 

wget http://www.cs.umd.edu/~snover/tercom/tercom-0.7.25.tgz
wget http://www.umiacs.umd.edu/~snover/terp/downloads/terp.v1.tgz

tar -xvf tercom-0.7.25.tgz --wildcards --no-anchored '*.jar'
tar -xvf terp.v1.tgz --wildcards --no-anchored '*.jar'
mv terp.v1/dist/lib/*.jar lib-nodistrib
mv tercom-0.7.25/tercom.7.25.jar lib-nodistrib

# build phrasal
ant all
