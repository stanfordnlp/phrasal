#!/usr/bin/perl
# BOLT P1 Eval: end-to-end translation script
# author: Daniel Cer

$id = $ARGV[0];

print "Doing id $id\n";
print "Normalizing source\n";

$snorm = "source.part$id.norm";
$model_dir = "model.$id";
$decoder_cfg = "phrasal.BOLTP1EVAL.part$id.ini";
$nbest_file = "part.$id.nbest";
$trans_file = "part.$id.trans";
$dlog_file  = "part.$id.dlog";

$cmd = "normalize2_test.pl < BOLT-P1-chinese-text-shadows-part$id.sgm.line.seg.class.trans > $snorm";
print "Running: $cmd\n";
system($cmd);

print "Extracting Phrase Table\n";
print "================================================================\n";
system("mkdir -p $model_dir");

$cmd = 
"java -Xmx70g edu.stanford.nlp.mt.train.PhraseExtract -filterCenterDot true  -usePmi true -hierarchicalOrientationModel true -extractors edu.stanford.nlp.mt.train.MosesPharoahFeatureExtractor:edu.stanford.nlp.mt.train.CountFeatureExtractor:edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor -fCorpus /juicy/scr42/scr/nlp/data/gale/BOLT/BOLTP1-chinese/Zh-En.081611.local/training/model_BOLT_FOUO-shared/aligned.zh -eCorpus /juicy/scr42/scr/nlp/data/gale/BOLT/BOLTP1-chinese/Zh-En.081611.local/training/model_BOLT_FOUO-shared/aligned.en -align /juicy/scr42/scr/nlp/data/gale/BOLT/BOLTP1-chinese/Zh-En.081611.local/training/model_BOLT_FOUO-shared/aligned.A -fFilterCorpus $snorm -outputFile $model_dir/phrases.gz > $model_dir/phrases.log 2>&1";

print "Running: $cmd\n";
system($cmd);

print "Spliting Phrase Table\n";
print "================================================================\n";
$cmd =
"zcat $model_dir/phrases.gz | split-table-n 9 $model_dir/phrases.tm.gz $model_dir/phrases.om.gz";
print "Running: $cmd\n";
system($cmd);

print "Creating model file: $decoder_cfg\n";
print "================================================================\n";
open ifh, "phrasal.BOLTP1EVAL.templ.ini" or die;
open ofh,  ">$decoder_cfg" or die;
while (<ifh>) {
  chomp;
  s/\$ID/$id/g;
  print ofh "$_\n";
}
close ofh;
close ifh;

print "Translating\n";
print "================================================================\n";
$nbest_file = "part.$id.nbest";
$trans_file = "part.$id.trans";
$dlog_file  = "part.$id.dlog";
$hostname = `hostname -s`;
chomp $hostname;
$cmd = "java -Xmx20g -DSRILM=true -Djava.library.path=/scr/nlp/data/gale3/SRILM-JNI/$hostname edu.stanford.nlp.mt.Phrasal $decoder_cfg < $snorm > $trans_file 2> $dlog_file";
print "Running: $cmd\n";
system($cmd);

print "Running MBR\n";
print "================================================================\n";
$cmd = "sort -n -s $nbest_file > $nbest_file.sort";
print "Running: $cmd\n";
system($cmd);

$cmd = "java edu.stanford.nlp.mt.tools.MinimumBayesRisk 0.1 utility ter $nbest_file.sort > $nbest_file.mbr";
print "Running: $cmd\n";
system($cmd);

print "Post Processing\n";
print "================================================================\n";
$cmd = "zhen-postprocess-wb.nbest.pl < $nbest_file.mbr > $nbest_file.final";
print "Running: $cmd\n";
system($cmd);
$cmd = "extract_from_nbest.pl < $nbest_file.final > $trans_file.final";
print "Running: $cmd\n";
system($cmd);

