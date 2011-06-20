#!/usr/bin/perl

use File::Compare;

$start_time = time;

$date_tag=`date +%Y-%m-%d`; chomp $date_tag;
$proc_tag=$ARGV[0];
shift @ARGV;

#`rm -rf /u/nlp/data/mt_test/mert/phrasal-mert.$proc_tag`;
#`(cd  /u/nlp/data/mt_test/mert/; $ENV{"JAVANLP_HOME"}/projects/mt/scripts/phrasal-mert.pl --nbest=500 --working-dir=phrasal-mert.$proc_tag dev2006.fr.lowercase.h10 dev2006.en.lowercase.h10 bleu base.ini > phrasal-mert.$proc_tag.$date_tag.log 2>&1)`;
#`touch phrasal-mert.$proc_tag.$date_tag.log`;
$total_time = time - $start_time;
if (compare("/u/nlp/data/mt_test/mert/phrasal-mert.$proc_tag/phrasal.10.trans", "/u/nlp/data/mt_test/mert/expected-pmert-dir/phrasal.10.trans") == 0) {
	print "Test Success (Time: $total_time s).\n";
  $exitStatus = 0;
} else {
	print "Test Failure (Time: $total_time s).\n";
  $exitStatus = -1;
}


$emails = join ', ', @ARGV;
if ($emails ne "") {
  print "Emailing: $emails\n";
} else {
  print "No alert e-mail addresses specified\n";
}
$log = `cat /u/nlp/data/mt_test/mert/phrasal-mert.$proc_tag.$date_tag.log`;
$from_addr = "javanlp-mt-no-reply\@mailman.stanford.edu";
foreach $emailAddr (@ARGV) {
  if ($exitStatus == 0) {
     $subject = "MT daily integration test ($date_tag) was successful!";
     $body    = "Hello $emailAddr,\n\n".
                "The $data_tag MT daily integration test was sucessful!\n\n";
  } else {
     $subject = "MT daily integration test ($date_tag) FAILED!";
     $body = "Hello $emailAddr,\n\n".
            "The $data_tag MT daily integration test FAILED!\n\n";
  }
  $body .= "Log File:\n\n$log\n";
  open(fh, ">/u/nlp/data/mt_test/mert/email.$date_tag.body");
  print fh $body;
  close(fh);

  print "ssh jacob 'mail -s \"$subject\" $emailAddr -- -f $from_addr < body'";
  `ssh jacob 'mail -s \"$subject\" $emailAddr -- -f $from_addr < /u/nlp/data/mt_test/mert/email.$date_tag.body > /u/nlp/data/mt_test/mert/e-mail.$proc_tag.$date_tag.log 2>&1'`;
}

exit $exitStatus;
