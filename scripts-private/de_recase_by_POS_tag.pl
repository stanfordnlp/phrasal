#!/usr/bin/perl

system("java -mx300m edu.stanford.nlp.tagger.maxent.MaxentTagger  -model ~cerd/scr/stanford-postagger-full-2013-04-04/models/german-fast.tagger -tokenize false -textFile $ARGV[0] > $ARGV[0].pos");

open fh, "$ARGV[0].pos" or die;

while (<fh>) {
  chomp;
  next if (/^\s*$/);
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
