#!/bin/sh

echo "Making Phrasal release tar ball"

cd $JAVANLP_HOME
revHead=`svn info -r HEAD | grep -i "Last Changed Rev"`
revCheckout=`svn info | grep -i "Last Changed Rev"`
svnStatus=`svn status | grep -v "scripts-private/make-phrasal-release.sh"`
#svnStatus=`svn status | grep -v "^\?" | grep -v "scripts-private/make-phrasal-release.sh"`
cd -

echo "SVN status: " $svnStatus

if [ "$revHead" = "$revCheckout" ]; then
   echo "PASS: Repository checkout is current"
   echo "$revCheckout"
   echo "Current time: " `date`
else
   echo "FAIL: Repository checkout is NOT current"
   echo "$revCheckout != $revHead"
   echo "Please svn update before making a distribution"
   exit -1
fi

if [ "$svnStatus" = "" ]; then
   echo "PASS: no uncommitted changes detected"
else
   echo "FAIL: uncommitted changes detected"
   echo $svnStatus
   exit -1
fi

cd $JAVANLP_HOME
ant all
if [ $? = 0 ]; then
  echo "PASS: repository builds succuessfully"
else
  echo "FAIL: repository has build errors"
  exit -1
fi 
cd -

svn info  file:///u/nlp/svnroot/branches/phrasal-releases/$1 >/dev/null 2>&1
if [ $? = 0 ]; then
echo "Removing old $1 distribution branch from svn/branches/phrasal-releases"
svn delete file:///u/nlp/svnroot/branches/phrasal-releases/$1 -m "remaking Stanford Phrasal distribution $1 (this happens when something went wrong the first time around)"
fi

echo "Archiving distribution under svnroot/branches/phrasal-releases/$1"
svn copy file:///u/nlp/svnroot/trunk/javanlp file:///u/nlp/svnroot/branches/phrasal-releases/$1 -m "release branch for Stanford Phrasal distribution $1"

rm -rf phrasal.$1
mkdir phrasal.$1

cp -r src scripts README.txt LICENSE.txt phrasal.$1
cp userbuild.xml  phrasal.$1/build.xml

# TODO: if these dependencies start getting more complicated, find an
# automatic way to solve them
mkdir -p phrasal.$1/src/edu/stanford/nlp/lm
cp ../more/src/edu/stanford/nlp/lm/* phrasal.$1/src/edu/stanford/nlp/lm

mkdir -p phrasal.$1/src/edu/stanford/nlp/stats
cp ../core/src/edu/stanford/nlp/stats/OpenAddressCounter.java phrasal.$1/src/edu/stanford/nlp/stats/OpenAddressCounter.java

mkdir -p phrasal.$1/src/edu/stanford/nlp/trees
cp ../core/src/edu/stanford/nlp/trees/DependencyScoring.java phrasal.$1/src/edu/stanford/nlp/trees/DependencyScoring.java

mkdir -p phrasal.$1/src/edu/stanford/nlp/util/logging
cp ../core/src/edu/stanford/nlp/util/logging/* phrasal.$1/src/edu/stanford/nlp/util/logging


mkdir -p phrasal.$1/lib
cp lib/berkeleyaligner.jar phrasal.$1/lib
cp ../core/lib/fastutil.jar phrasal.$1/lib
cp lib/je-4.1.10.jar phrasal.$1/lib

mkdir `pwd`/phrasal.$1/classes
mkdir `pwd`/phrasal.$1/lib-nodistrib

export CLASSPATH=.
export CORENLP=`ls -dt /u/nlp/distrib/stanford-corenlp-201*-0*[0-9] | head -1`

(cd  phrasal.$1/; ./scripts/first-build.sh all)
if [ $? = 0 ]; then
   echo "PASS: User distribution builds successfully"
else
   echo "FAIL: User distribution has build errors"
   exit -1
fi

jar -cf phrasal.$1/phrasal.$1.jar -C phrasal.$1/classes edu

echo "Running phrasal integration test" 
export CLASSPATH=$CLASSPATH:`pwd`/phrasal.$1/classes
for jarFile in $CORENLP/*.jar; do
  export CLASSPATH=$CLASSPATH:$jarFile
done
export CLASSPATH=$CLASSPATH:`pwd`/phrasal.$1/lib/fastutil.jar
echo $CLASSPATH

scripts/standard_mert_test.pl distro.$1

if [ $? = 0 ]; then
  echo "PASS: Phrasal integration test"
else
  echo "FAIL: Phrasal integration test"
  echo "Log file in /u/nlp/data/mt_test/mert:"
  echo `ls -t  /u/nlp/data/mt_test/mert/*.log | head -1`
  echo "End of log dump for FAIL: Phrasal integration test"
  cat `ls -t  /u/nlp/data/mt_test/mert/*.log | head -1`
  echo "FAIL: Phrasal integration test"
  exit -1
fi

#rm -rf phrasal.$1/classes/*
rm -rf phrasal.$1/lib-nodistrib/*

tar --exclude .svn -czf phrasal.$1.tar.gz phrasal.$1

if [ $? = 0]; then
  echo "SUCCESS: Stanford Phrasal distribution phrasal.$1.tar.gz successfully built"
else
  echo "FAIL: Tar went wrong somehow"
  exit -1
fi
