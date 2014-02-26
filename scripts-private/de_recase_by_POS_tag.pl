#!/usr/bin/perl

# Uppercases nouns (words for which POS tag starts with N). Input should be lowercased.

system("java -mx300m edu.stanford.nlp.tagger.maxent.MaxentTagger  -model /u/nlp/data/pos-tagger/distrib-2014-02-20/german-fast-caseless.tagger -tokenize false -sentenceDelimiter newline -outputFormatOptions keepEmptySentences -textFile $ARGV[0] > $ARGV[0].pos");

open fh, "$ARGV[0].pos" or die;

while (<fh>) {
  chomp;
  if (/^\s*$/) {
    print "\n";
    next;
  }
  @toks = split /\s+/;
  $l = "";
  foreach $tok (@toks) {
     $word = $pos = $tok;
     $word =~ s/\_[^_]+$//;
     $pos =~ s/.*\_([^_]+)$/\1/;
     if ($pos =~ /^N/) {
        $word = ucfirst($word);
     }
     $l .= "$word "; 
  }
  $l =~ s/ $//;
  print "$l\n";
}
