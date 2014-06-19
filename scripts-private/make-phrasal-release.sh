#!/bin/sh

echo "Making Phrasal release tar ball"
echo "JAVANLP_HOME set to $JAVANLP_HOME"

basedir=$PWD
cd $JAVANLP_HOME

expectedBranch="master"
version=""
while getopts "b:v:" OPTION
do
  case $OPTION in 
  b)
    expectedBranch=$OPTARG
    ;;
  v)
    version=$OPTARG
    ;;
  esac
done

if [ "$version" == "" ]; then
  echo "FAIL: must specify a version name with -v"
  exit 2
fi


gitBranch=`git branch | grep "*" | cut -d " " -f 2`
echo "GIT branch: " $gitBranch
if [ "$gitBranch" = "$expectedBranch" ]; then
    echo "PASS: GIT branch " $gitBranch
else
    echo "FAIL: GIT should be on branch master, is on " $gitBranch
    exit -1
fi

gitFetch=`git fetch -v --dry-run 2>&1 | grep master`
gitExpectedFetch=" = [up to date]      master     -> origin/master"
echo "Result of 'git fetch -v --dry-run':"
echo "  " $gitFetch
if [ "$gitFetch" = "$gitExpectedFetch" ]; then
    echo "PASS: Repository checkout is current"
else
    echo "FAIL: Repository checkout is NOT current"
    echo "Please git pull before making a distribution"
    exit -1
fi

gitPush=`git push --dry-run 2>&1`
gitExpectedPush="Everything up-to-date"
echo "Result of 'git push --dry-run':"
echo "  " $gitPush
if [ "$gitPush" = "$gitExpectedPush" ]; then
    echo "PASS: no unpushed changes"
else
    echo "FAIL: there are committed but unpushed changes"
    exit -1
fi

gitUntracked=`git status 2>&1 | grep Untracked`
if [ "$gitUntracked" = "" ]; then
    echo "PASS: no untracked changes"
else
    echo "FAIL: untracked changes detected"
    echo $gitUntracked
    exit -1
fi

gitUncommitted=`git status 2>&1 | grep "Changes to be committed"`
if [ "$gitUncommitted" == "" ]; then
    echo "PASS: no changes ready to be committed"
else
    echo "FAIL: detected changes ready to be committed"
    echo $gitUncommitted
    exit -1
fi

gitCommitDryrun=`git commit --dry-run -am foo 2>&1 | grep -e modified -e deleted | grep -v make-phrasal-release`
if [ "$gitCommitDryrun" == "" ]; then
    echo "PASS: no uncommitted changes detected"
else
    echo "FAIL: uncommitted changes detected"
    echo $gitCommitDryrun
fi

echo "Current time: " `date`
echo "Building in $PWD"

ant all
if [ $? = 0 ]; then
  echo "PASS: repository builds succuessfully"
else
  echo "FAIL: repository has build errors"
  exit -1
fi 
cd $basedir

rm -rf phrasal.$version
mkdir phrasal.$version || exit

cp -r src scripts example README.txt LICENSE.txt phrasal.$version || exit
cp userbuild.xml  phrasal.$version/build.xml || exit

cd $JAVANLP_HOME
cp -r projects/more/src-cc $basedir/phrasal.$version/src-cc || exit
cd $basedir

perl ../../bin/gen-dependencies.pl -depdump depdump -srcjar src.jar -classdir ../core/classes -srcdir ../core/src \
    edu.stanford.nlp.classify.LogisticClassifier \
    edu.stanford.nlp.classify.LogisticClassifierFactory \
    edu.stanford.nlp.trees.DependencyScoring \
    edu.stanford.nlp.util.concurrent.ConcurrentHashIndex \
    
mkdir -p phrasal.$version/src || exit
cd phrasal.$version/src || exit
jar xf ../../src.jar edu || exit
cd $basedir

# TODO: if these dependencies start getting more complicated, find an
# automatic way to solve them (would need to make gen-dependencies
# work across multiple directories)

mkdir -p phrasal.$version/src/edu/stanford/nlp/stats
cp ../more/src/edu/stanford/nlp/stats/OpenAddressCounter.java phrasal.$version/src/edu/stanford/nlp/stats/OpenAddressCounter.java || exit

mkdir -p phrasal.$version/src/edu/stanford/nlp/classify
cp ../more/src/edu/stanford/nlp/classify/LinearRegressionFactory.java phrasal.$version/src/edu/stanford/nlp/classify || exit
cp ../more/src/edu/stanford/nlp/classify/LinearRegressionObjectiveFunction.java phrasal.$version/src/edu/stanford/nlp/classify || exit
cp ../more/src/edu/stanford/nlp/classify/LinearRegressor.java phrasal.$version/src/edu/stanford/nlp/classify || exit
cp ../more/src/edu/stanford/nlp/classify/Regressor.java phrasal.$version/src/edu/stanford/nlp/classify || exit
cp ../more/src/edu/stanford/nlp/classify/RegressionFactory.java phrasal.$version/src/edu/stanford/nlp/classify || exit
cp ../more/src/edu/stanford/nlp/classify/CorrelationLinearRegressionObjectiveFunction.java phrasal.$version/src/edu/stanford/nlp/classify || exit

mkdir -p phrasal.$version/src/edu/stanford/nlp/lm
cp ../more/src/edu/stanford/nlp/lm/KenLM.java phrasal.$version/src/edu/stanford/nlp/lm || exit

mkdir -p phrasal.$version/lib || exit
cp ../core/lib/javax.servlet.jar phrasal.$version/lib || exit
cp ../core/lib/junit.jar phrasal.$version/lib || exit
cp ../core/lib/commons-lang3-3.1.jar phrasal.$version/lib || exit
cp ../more/lib/fastutil.jar phrasal.$version/lib || exit
cp ../more/lib/je.jar phrasal.$version/lib || exit
cp ../more/lib/google-guava.jar phrasal.$version/lib || exit

mkdir `pwd`/phrasal.$version/classes
mkdir `pwd`/phrasal.$version/lib-nodistrib

export CLASSPATH=.
export CORENLP=`ls -dt /u/nlp/distrib/stanford-corenlp-full-201*-0*[0-9] | head -1`

(cd  phrasal.$version/; ./scripts/get-dependencies.sh all) || exit
(cd phrasal.$version/; ant)
if [ $? = 0 ]; then
   echo "PASS: User distribution builds successfully"
else
   echo "FAIL: User distribution has build errors"
   exit -1
fi

jar -cf phrasal.$version/phrasal.$version.jar -C phrasal.$version/classes edu || exit

rm -rf phrasal.$1/classes/
rm -rf phrasal.$1/lib-nodistrib/

if [ "$expectedBranch" = "master" ]; then 
  # This time, look without excluding make-phrasal-release so that we can stash it if needed
  gitCommitDryrun=`git commit --dry-run -am foo 2>&1 | grep -e modified -e deleted`
  if [ "$gitCommitDryrun" == "" ]; then
    stash="false"
  else
    stash="true"
    echo "Stashing your changes to make-phrasal-release.sh.  If something goes wrong, you will need to run"
    echo "  git stash pop"
    git stash
  fi

  gitBranch=phrasal-release-$version
  echo "Pushing new git branch $gitBranch"

  existingBranch=`git branch -r 2>&1 | grep $gitBranch`
  if [ "existingBranch" == "" ]; then
    echo "PASS: no existing $gitBranch found"
  else
    echo "Apparently found existing $gitBranch, attempting to delete"
    git push origin :$gitBranch
  fi

  git branch $gitBranch
  git push origin $gitBranch

  git checkout master || exit
  if [ "$stash" == "true" ]; then
    git stash pop
  fi
fi

tar -czf phrasal.$version.tar.gz phrasal.$version

if [ $? = 0 ]; then
  echo "SUCCESS: Stanford Phrasal distribution phrasal.$version.tar.gz successfully built"
else
  echo "FAIL: Tar went wrong somehow"
  exit -1
fi
