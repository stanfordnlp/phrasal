#!/usr/bin/perl


$LIBLINEAR_DIR = $ENV{"LIBLINEAR_DIR"};
$train_cmd = "$LIBLINEAR_DIR/train";

if ($#ARGV < 4) {
  print stderr "Usage:\n\t$0 (work dir) (iter) (opt_type) (comma ref list) (random seed - ignored)\n";
  exit -1;
}

$work_dir = $ARGV[0];
$iter = $ARGV[1];
$opt_type = $ARGV[2];
$commaRefList = $ARGV[3];
$refList = $commaRefList;
$refList =~ s/,/ /g;
$seed = $ARGV[4];

$next_iter = $iter+1;

$cmd = "java edu.stanford.nlp.mt.tools.NbestEvaluatationAnnotation $work_dir/phrasal.$iter.combined.nbest.gz $opt_type $work_dir/phrasal.$iter.combined.nbest.with_bleu_scores $refList";
print "$cmd\n"; system($cmd);

$cmd = "prepare-liblinear.py $work_dir/phrasal.$iter.combined.nbest.with_bleu_scores  $work_dir/phrasal.$iter.combined.nbest.feature_index $work_dir/phrasal.$iter.combined.nbest.liblinear";
print "$cmd\n"; system($cmd);

$cmd = "$train_cmd -c 1.0 -s 13 -p 1.0 $work_dir/phrasal.$iter.combined.nbest.liblinear $work_dir/phrasal.$iter.combined.nbest.liblinear.mod";
print "$cmd\n"; system($cmd);


$cmd = "extract-liblinear-weights.py $work_dir/phrasal.$iter.combined.nbest.feature_index $work_dir/phrasal.$iter.combined.nbest.liblinear.mod  $work_dir/phrasal.$next_iter.binwts";
print "$cmd\n"; system($cmd);

$cmd = "java edu.stanford.nlp.mt.tools.NBestListDecoder $work_dir/phrasal.$iter.combined.nbest.gz  $work_dir/phrasal.$next_iter.binwts $work_dir/phrasal.$iter.opt_nbest.trans";
print "$cmd\n"; system($cmd);

$cmd ="cat $work_dir/phrasal.$iter.opt_nbest.trans | java edu.stanford.nlp.mt.tools.Evaluate $opt_type $refList | head -1 | sed -e 's/.*= //g'";
print "$cmd\n";
$SCORE = `$cmd`;

print "Final Eval Score: $SCORE";
