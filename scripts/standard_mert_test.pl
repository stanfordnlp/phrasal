#!/usr/bin/perl

use File::Compare;

$start_time = time;

$date_tag=`date +%Y-%m-%d`; chomp $date_tag;
$proc_tag=$ARGV[0];

`rm -rf /u/nlp/data/mt_test/mert/phrasal-mert.$proc_tag`;
`(cd  /u/nlp/data/mt_test/mert/; $ENV{"JAVANLP_HOME"}/projects/mt/scripts/phrasal-mert.pl --nbest=500 --working-dir=phrasal-mert.$proc_tag dev2006.fr.lowercase.h10 dev2006.en.lowercase.h10 bleu base.ini > phrasal-mert.$proc_tag.$date_tag.log 2>&1)`;
$total_time = time - $start_time;
if (compare("/u/nlp/data/mt_test/mert/phrasal-mert.$proc_tag/phrasal.10.trans", "/u/nlp/data/mt_test/mert/expected-pmert-dir/phrasal.10.trans") == 0) {
	print "Test Success (Time: $total_time s).\n";
	exit 0;
} else {
	print "Test Failure (Time: $total_time s).\n";
	exit -1;
}
