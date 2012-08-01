#!/usr/bin/perl

if ($#ARGV != 5) {
  $prog_name = $0;
  $prog_name =~ s/.*\///g;
  print stderr "Usage:\n\t$prog_name source refs decoder.ini mert_dir outprefix mod\n";
  exit -1;
}

$source_file = $ARGV[0];
$ref_prefix  = $ARGV[1];
$decoder_ini = $ARGV[2];
$mert_dir    = $ARGV[3];
$output_prefix = $ARGV[4];
$mod = $ARGV[5];
$JAVA_ARGS = $ENV{"JAVA_ARGS"};

while (<$mert_dir/phrasal.*.*wts>) { chomp;
  $weights_file = $_; $iter = $_;
  next if ($weights_file =~ /phrasal.final.binwts/);
  $iter =~ s/^.*phrasal\.([0-9]+)\.(bin)?wts$/\1/;
  $m = $iter % $mod;
  next if (($iter % $mod) != 0); 
  push @good_iters, $iter; 
  $wIter{$iter} = $weights_file;
}

@good_iters = sort {$a <=> $b} @good_iters;
for $iter  (@good_iters) {
  $weights_file = $wIter{$iter};
  print "$iter\n";
  if (-e "$output_prefix.$iter.nbest") {
     print "$output_prefix.$iter.nbest exists - skipping decoding\n";
  } else {
     $cmd = "java $JAVA_ARGS edu.stanford.nlp.mt.Phrasal -config-file $decoder_ini -weights-file $weights_file -n-best-list \"$output_prefix.$iter.nbest 100\" < $source_file > $output_prefix.$iter.trans 2> $output_prefix.$iter.dlog";
     print "Decoding:\n$cmd\n";
     `$cmd`;
  }
  $cmd = "sort -n -s $output_prefix.$iter.nbest | extract_from_nbest.pl | java edu.stanford.nlp.mt.metrics.BLEUMetric $ref_prefix*";
  print "Scoring:\n$cmd\n";
  $score = `$cmd`;
  print $score;
} 
