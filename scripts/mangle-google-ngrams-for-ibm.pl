#!/usr/bin/perl

## Collapse together some n-grams into (n-1)-grams to match the bad
## tokenization of IBM MT GALE data in 2007-08
## Basically runs as a filter.
## Optionally takes two arguments which are the min and the max 
## n-gram size to print
##
## Input format is space separate n-gram tokens, then a tab, then count.
##
## Christopher Manning .. Jan 2008

use utf8;

$minN = -1;
$maxN = -1;
if ($#ARGV == 1) {
  $minN = shift;
  $maxN = shift;
  # print "min is $minN; max is $maxN\n";
  ## Make ARGV empty so it runs as a filter
  shift;
}

while ($line = <>) {
  ($line,$cnt) = split(/\t/, $line);
  # space pad for ease
  $line = " " . $line . " ";

  # Do triple ones like . ' ' or , ' "
  $line =~ s/ ([,.:;?!"]) ([,.:;?!"]) " / $1$2" /;
  $line =~ s/ " ([,.:;?!"]) ([,.:;?!"]) / "$1$2 /;

  # Do ones like ," or ".
  $line =~ s/ ([,.:;?!]) " / $1" /;
  $line =~ s/ " ([,.:;?!]) / "$1 /;
  
  # Do triple ones like U.S.," or U.N.,"
  $line =~ s/([a-zA-Z]\.) ([,"-]) ([,"]) /$1$2$3 /;

  # Do ones like U.S., or U.N."
  $line =~ s/([a-zA-Z]\.) ([,"-]) /$1$2 /;

  @ngram = split(/ /, $line);

  # print if appropriate
  if ($minN < 0 || ($#ngram >= $minN && $#ngram <= $maxN)) {
    # remove padding
    $line =~ s/^ //;
    $line =~ s/ $//;
    print $line . "\t" . $cnt;
  }
}
