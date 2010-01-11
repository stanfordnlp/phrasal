#!/usr/bin/perl

use File::Compare;

$start_time = time;
$date_tag=`date +%Y-%m-%d`; chomp $date_tag;
`(cd  /u/nlp/data/mt_test/mert/; $ENV{"JAVANLP_HOME"}/projects/mt/scripts/phrasal-mert.pl dev2006.fr.lowercase.h10  dev2006.en.lowercase.h10 bleu base.ini > phrasal-mert.$date_tag.log)`;
$total_time = time - $start_time;
if (compare("/u/nlp/data/mt_test/mert/pmert-dir/phrasal.17.trans", "/u/nlp/data/mt_test/mert/expected-pmert-dir/phrasal.17.trans") == 0) {
	print "Test Success (Time: $total_time s).\n";
	exit 0;
} else {
	print "Test Failure (Time: $total_time s).\n";
	exit -1;
}
