#!/bin/sh
#PBS -l cput=12:00:00,ncpus=2,mem=10g
#PBS -N daily.mt.mert.test
#PBS -M cerd@stanford.edu
#PBS -j oe
#PBS -o /u/nlp/data/mt_test/mert/dailytest.log
#PBS -m a
#PBS -q verylong

TEST_DIR=/u/nlp/data/mt_test/mert/

cd /u/nlp/data/mt_test/mert/
rm -rf javanlp
svn checkout file:///u/nlp/svnroot/trunk/javanlp
cd javanlp
ant all
export JAVANLP_HOME=`pwd`
export CLASSPATH=.
for i in projects/*/classes projects/*/lib/*.jar; do
  export CLASSPATH=$CLASSPATH:`pwd`/$i
done

for i in  projects/*/scripts/; do
  export PATH=$PATH:`pwd`/$i
done

echo classpath $CLASSPATH
echo path $PATH
standard_mert_test.pl dailytest cerd@stanford.edu stanford-mt@lists.stanford.edu
