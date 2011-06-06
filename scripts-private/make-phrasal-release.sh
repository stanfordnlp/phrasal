#!/bin/sh

echo "Making Phrasal release tar ball"

rm -rf phrasal.$1
mkdir phrasal.$1


mkdir src/edu/stanford/nlp/lm
cp ../more/src/edu/stanford/nlp/lm/* src/edu/stanford/nlp/lm

cp -r src scripts README.txt LICENSE.txt phrasal.$1
cp userbuild.xml  phrasal.$1/build.xml

mkdir -p phrasal.$1/lib
cp lib/berkeleyaligner.jar phrasal.$1/lib

mkdir `pwd`/phrasal.$1/classes
mkdir `pwd`/phrasal.$1/lib-nodistrib

export CLASSPATH=.
CORENLP=`ls -dt /u/nlp/distrib/stanford-corenlp-2011-0*[0-9] | head -1`

(cd  phrasal.$1/; ./scripts/first-build.sh all)
jar -cf phrasal.$1/phrasal.$1.jar -C phrasal.$1/classes edu
rm -rf phrasal.$1/classes/*
rm -rf phrasal.$1/lib-nodistrib/*

tar --exclude .svn -czf phrasal.$1.tar.gz phrasal.$1

rm -rf src/edu/stanford/nlp/lm
