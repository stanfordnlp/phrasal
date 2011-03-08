#!/bin/sh

echo "Making Phrasal release tar ball"

rm -rf phrasal.$1
mkdir phrasal.$1

jar -cf phrasal.$1/phrasal.$1.jar -C classes edu
cp -r src scripts README.txt LICENSE.txt phrasal.$1
tar --exclude .svn -czf phrasal.$1.tar.gz phrasal.$1
