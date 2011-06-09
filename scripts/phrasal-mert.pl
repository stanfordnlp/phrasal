#!/usr/bin/perl

use List::Util qw[min max];

#############################################################################
# Train Phrasal model parameters using minimum error rate training.
#
# If you run into trouble:
# 
# * Check that cmert is present and compiled. Be sure that 'cmert-dir'
#   points at the right directory.
#
# * Make sure you are using Java 6.
#
# * Make sure you classpath is set such that the JavaNLP and MT project
#   classlibaries are visible.
#
# * Make sure your locale is set correctly for the data set that you are
#   training on.
#
# Usage:
#
#   mert-phrasal.pl input-text references cmert-dir decoder.ini
#
# Author: Daniel Cer (Daniel.Cer@gmail.com)
#############################################################################

use List::Util qw[max];

$TMP = $ENV{TMPDIR} || '/tmp';
$WEIGHTS_SUFF = ".binwts";
$WEIGHT_MIN = -1;
$WEIGHT_MAX = 1;
$SLEEP = 30;
$ORACLE = 0;
$SORT = 'sort --buffer-size=3g';
$DEFAULT_MAX_ITERS = 25;
$MIN_OBJ_DIFF = 1e-7;
$DEFAULT_WORK_DIR = "phrasal-mert";
$DEFAULT_NBEST_SIZE = 100;
$DEFAULT_JAVA_FLAGS = "-Xmx7g";
$DEFAULT_OPT_FLAGS = "-o cer -t 4 -p 4"; # 4 starting points, 4 thread, Cer algorithm
$MIN_WEIGHT_DELTA = 1e-5;
$DELETE_COMBINED_NBEST = 0;
$NBEST_HISTORY_WINDOW = 1000000;
$SCRIPTS_DIR = $0;
$SCRIPTS_DIR =~ s/\/[^\/]*$//;
if ($SCRIPTS_DIR eq $0) {
  $SCRIPTS_DIR = `which $0`;
  $SCRIPTS_DIR =~ s/\/[^\/]*$//;
}

$OBJ_DIFF_STOP = 0.001;

$SCRIPTS_DIR =~ s/ /\\ /g;

$EXTERNAL_SCRIPTS_DIR="$SCRIPTS_DIR/../external_scripts";

print "Scripts dir: $SCRIPTS_DIR\n";
print "SVN info:\n";
print `(cd $SCRIPTS_DIR; svn info)`;
print "Host: ".`hostname`;

$work_dir=$DEFAULT_WORK_DIR;
$nbest_size=$DEFAULT_NBEST_SIZE;
$java_flags=$DEFAULT_JAVA_FLAGS;
$mert_java_flags=$DEFAULT_JAVA_FLAGS;
$opt_flags=$DEFAULT_OPT_FLAGS;
$phrasal_flags="";

if (not ($work_dir =~ /^\//)) {
  $pwd = `pwd`; chomp $pwd;
  $work_dir = $pwd."/".$work_dir;
} 

%WT_STORE= (
  "weight-d"=>["LinearDistortion", 
               "LexR:monotoneWithPrevious", 
               "LexR:swapWithPrevious",
               "LexR:discontinuousWithPrevious",
               "LexR:monotoneWithNext", 
               "LexR:swapWithNext", 
               "LexR:discontinuousWithNext"],
  "weight-l"=>["LM"],
  "weight-t"=>["TM:phi(t|f)", "TM:lex(t|f)", "TM:phi(f|t)", "TM:lex(f|t)", 
               "TM:phrasePenalty"],
  "weight-w"=>["WordPenalty"]
);

sub handle_arg {
  my ($arg) = @_;
  

  if ($arg =~ /^--textwts/) {
     $WEIGHTS_SUFF = ".wts";
  } elsif ($arg =~ /^--clean/) {
     $DELETE_COMBINED_NBEST = 1;
  } elsif ($arg =~ /^--oracle=.*/) {
     $ORACLE = $arg;
     $ORACLE =~ s/^--oracle=//;
		 chomp $SLEEP;
  } elsif ($arg =~ /^--sleep=.*/) {
     $SLEEP = $arg;
     $SLEEP =~ s/^--sleep=//;
		 chomp $SLEEP;
  } elsif ($arg =~ /^--history-size=.*/) {
		 $NBEST_HISTORY_WINDOW = $arg;
     $NBEST_HISTORY_WINDOW =~ s/^--history-size=//;
		 print STDERR "HISTORY SIZE: $NBEST_HISTORY_WINDOW\n";
		 chomp $SLEEP;
  } elsif ($arg =~ /^--working-dir=.*/) {
     $work_dir = $arg;
     $work_dir =~ s/^--working-dir=//;
  } elsif ($arg =~ /^--nbest=.*/) {
     $nbest_size = $arg;
     $nbest_size =~ s/^--nbest=//g;
  } elsif ($arg =~ /^--java-flags=.*/) {
     $java_flags = $arg;
     $java_flags =~ s/^--java-flags=//g;
     $mert_java_flags = $java_flags;
		 print STDERR "JAVA FLAGS: $java_flags\n";
  } elsif ($arg =~ /^--mert-java-flags=.*/) {
     $mert_java_flags = $arg;
     $mert_java_flags =~ s/^--mert-java-flags=//g;
		 print STDERR "MERT JAVA FLAGS: $mert_java_flags\n";
  } elsif ($arg =~ /^--phrasal-flags=.*/) {
     $phrasal_flags = $arg;
     $phrasal_flags =~ s/^--phrasal-flags=//g;
		 print STDERR "PHRASAL FLAGS: $phrasal_flags\n";
  } elsif ($arg =~ /^--opt-flags=.*/) {
     $opt_flags = $arg;
     $opt_flags =~ s/^--opt-flags=//g;
		 print STDERR "OPT FLAGS: $opt_flags\n";
  } else {
     print stderr "Unrecognized flag $arg\n";
     exit -1;
  }
}

foreach $arg (@ARGV) {
	print STDERR "arg: $arg\n";
  if (not ($arg =~ /^--.*/)) {
     push @POSITIONAL_ARGS, $arg;
     next;
  }  
  handle_arg($arg);
}

$work_dir =~ s/\/$//g;

if (@POSITIONAL_ARGS != 4 && @POSITIONAL_ARGS != 5) {
   $nm = $0; $nm =~ s/.*\///g;
   print stderr "Usage:\n\t$nm [mem-size] input-text references (bleu/ter or :cmert-dir) decoder.ini\n";
   exit -1;
}

if (@POSITIONAL_ARGS == 5) {
	my $dirname = `dirname $0`;
	chomp $dirname;
	my $memsize = shift(@POSITIONAL_ARGS);
	$java_flags =~ s/(^| )-Xm[xs]\S+($| )/ /g;
	$mert_java_flags =~ s/(^| )-Xm[xs]\S+($| )/ /g;
	$java_flags .= " -Xmx$memsize ";
	$mert_java_flags .= "-Xmx$memsize ";
}

$input_text   = $POSITIONAL_ARGS[0];
$references   = $POSITIONAL_ARGS[1];
$opt_type     = $POSITIONAL_ARGS[2];

if ($opt_type =~ /cmert/) {
  $cmert_dir  = $opt_type;
}

$decoder_ini  = $POSITIONAL_ARGS[3];

open fhdi, "$decoder_ini" or die "Can't open $decoder_ini\n";
while (<fhdi>) { chomp;
	next unless /^#PMERT/;
	s/^#PMERT\s+//;
	handle_arg($_);
}
close fhdi;

if (-e $references."0") {
  $referenceList = "";
  for ($i = 0; -e "$references$i"; $i++) {
     $referenceList .= " " if ($referenceList);
     $referenceList .= "$references$i";
  }
}

if (not $referenceList) {
  $referenceList = $references;
}

$commaRefList =  $referenceList;
$commaRefList =~ s/ /,/g; 

   

%POSITIVE_WT_ONLY_FEATURES = (
  "LM"=>1,
  "LinearDistortion"=>1,
  "TM:lex(f|t)"=>1,
  "TM:lex(t|f)"=>1,
  "TM:phi(f|t)"=>1,
  "TM:phi(t|f)"=>1,
  "LexR:discontinuousWithNext"=>1, 
  "LexR:discontinuousWithPrevious"=>1,
  "LexR:monotoneWithNext"=>1,
  "LexR:monotoneWithPrevious"=>1, 
  "LexR:swapWithNext"=>1,
  "LexR:swapWithPrevious"=>1,
  "IBM1TGS:full"=>1,
  "IBM1TGS:tmo"=>1,
);

%DEFAULT_WEIGHTS = (
  "LM"=>1.0, "LinearDistortion"=>"1.0",
  "TM:lex(f|t)"=>0.3, 
  "TM:lex(t|f)"=>0.2,
  "TM:phi(f|t)"=>0.3,
  "TM:phi(t|f)"=>0.2,
  "TM:phrasePenalty"=>0.0,
  "UnknownWord"=>1.0,
  "WordPenalty"=>0.0);


$work_dir =~ s/\/$//g;

print stderr "MERT - Phrasal\n";
print stderr "\tdecoder_ini: $decoder_ini\n";
print stderr "\tinput text: $input_text\n";
print stderr "\treferences: $references\n";
print stderr "\topt_type: $opt_type\n";
print stderr "\tcmert_dir: $cmert_dir\n";
print stderr "\twork dir: $work_dir\n";
print stderr "\tnbest size: $nbest_size\n";
print stderr "\tjava flags: $java_flags\n";
print stderr "\tMERT java flags: $mert_java_flags\n";
print stderr "\n";

if (!$ENV{"RECOVER"}) {
  print "removing old work dir\n";
  `rm -rf $work_dir`;
}

if (not (-e $work_dir)) {
   mkdir($work_dir);
} else {
   if (!$ENV{"RECOVER"}) { 
      print "aborting $work_dir already exists!\n";
      exit -1;
    }
}

open difh, $decoder_ini or die "Can't open $decoder_ini";

%strip_fields = ("n-best-list"=>1);
$strip_line = 0;
%init_wts = %DEFAULT_WEIGHTS;
my $init_weight_file = '';
while (!eof(difh)) {
  $line = <difh>; chomp $line;
  if ($line =~ /^\[weights-file\].*/) {
		$init_weight_file = <difh>; chomp $init_weight_file;
		next;
	}
  if ($line =~ /^\s*$/ or $line =~ /^\s*#.*$/ or $line =~ /^\[.*/) { 
     $strip_line = 0;
		 $wts_line = 0;
  }
  foreach $strip_field (keys %strip_fields) {
     if ($line =~ /\[$strip_field\].*/) {
       $strip_line = 1;
     }
  } 
  if (not $strip_line) {
     push @decoder_ini, $line;
  } 
  foreach $weight_field (keys %WT_STORE) {
     if ($line =~ /\[$weight_field\].*/) {
        $line = <difh>; chomp $line;
        push @decoder_ini, $line;
        undef @wt_arr;
        while (!($line =~ /^\s*$/)) {
           @fields = split /\s+/, $line;
           foreach $field (@fields) {
              push @wt_arr, $field;
           }
           $line = <difh>; chomp $line;
           push @decoder_ini, $line;
        }
        $wt_names = $WT_STORE{$weight_field};
        @wt_names = @$wt_names;
        for ($i = 0; $i <= $#wt_arr; $i++) {
            $init_wts{$wt_names[$i]} = $wt_arr[$i];
        }
     }
  }
}
close difh; 

if($init_weight_file) {
	open(W,$init_weight_file);
	while(<W>) {
		chomp;
		/(\S+)\s+(\S+)/;
		$init_wts{$1} = $2;
	}
}

if($ORACLE) {
	$init_wts{BLEU} = $ORACLE;
	 $opt_flags .= " -D BLEU";
}

$ini_weight_file = "$work_dir/phrasal.0.wts\n";
print stderr "Writing initial weights file:\n$ini_weight_file\n";
open wtfh, ">$ini_weight_file" or die;
foreach $key (keys %init_wts) {
	 print "$key => $init_wts{$key}\n";
	 print wtfh "$key $init_wts{$key}\n"
}
close wtfh;

$first_active_iter = 0;

if ($ENV{"RECOVER"}) {
	system stderr "Searching for work in progress....\n";
	@trans_files = `ls $work_dir/phrasal.*$WEIGHTS_SUFF`; # we use a cue that is safe but, risks redoing some work
	$max_weight_iter = 0;
	foreach $trans_file (@trans_files) { chomp $trans_file;
		$iter = $trans_file;
		$iter =~ s/^.*phrasal\.//;
		$iter =~ s/\.wts//;
		$max_weight_iter = $iter if ($iter > $max_weight_iter);
	}
	$first_active_iter = $max_weight_iter;
	print stderr "Restarting on iter: $first_active_iter\n";
} 


$lastTotalNbestListSize = "N/A";
$LastObj = "null";

for ($iter = 0; $iter < $DEFAULT_MAX_ITERS; $iter++) {
   print stderr "Iter: $iter\n"; 
	 print stderr "Date: ".`date`;
   print stderr 
   "========================================================================".
   "\n\n";
   $iter_nbest_list = "$work_dir/phrasal.$iter.nbest";
   print stderr "Preparing to produce nbest list:\n$iter_nbest_list\n";
   print stderr 
   "------------------------------------------------------------------------\n\n";
   if ($iter == 0) {
     $iter_weights = "$work_dir/phrasal.$iter.wts";
   } else {
     $iter_weights = "$work_dir/phrasal.$iter$WEIGHTS_SUFF";
   }
   print stderr "Using weights file:\n$iter_weights\n\n";
   $iter_decoder_ini = "$work_dir/phrasal.$iter.ini";
   print stderr "Writing decoder.ini:\n$iter_decoder_ini\n\n";
   open difh, ">$iter_decoder_ini" or die;
   print difh "# Automatically generated Phrasal decoder configuration file\n";
   print difh "# Configuration template: $decoder_ini\n";
   print difh "# Source Text: $input_text\n";
   print difh "# References: $references\n";
   print difh "# Created: ".`date`."";
   print difh "# Training Iteration: $iter\n";
   $out_nbest_eval = ($nbest_eval ? sprintf "%.3f", $nbest_eval : "n/a");
   print difh "# Prior n-best Eval: $out_nbest_eval\n";
   print difh 
   "###########################################################################"   ."\n";
   print difh "\n\n";
   foreach $line (@decoder_ini) { print difh "$line\n"; }
   print difh "\n[n-best-list]\n$iter_nbest_list\n$nbest_size\n\n";
   print difh "[weights-file]\n$iter_weights\n"; close difh;
   close difh;
   $iter_trans = "$work_dir/phrasal.$iter.trans";
   $iter_dlog   = "$work_dir/phrasal.$iter.dlog";

   print stderr "\nRunning phrasal\n";
   print stderr
   "------------------------------------------------------------------------\n\n";
   if (!$ENV{"SDI$iter"} && $iter >= $first_active_iter) { 
     my $cmd = "java $java_flags edu.stanford.nlp.mt.Phrasal $phrasal_flags -config-file $iter_decoder_ini < $input_text 2>$iter_dlog > $iter_trans";
		 print "CMD:\n$cmd\n\n";
     my $now = localtime time;
     print "Start time: ",$now,"\n";
    `$cmd`;
		 # Sort output:
     open fh, $iter_trans or die;
     while (<fh>) {
       chomp;
       last if (not /^[0-9]+:/);
       $id = $_; 
       $sent = $_;
       $id =~ s/:.*//; 
       $sent =~ s/^[^:]*://; 
       $lines[$id] = $sent;
     }
     close fh;
     if (@lines) {
       open fh, ">$iter_trans" or die;
       for $line (@lines) {
         print fh "$line\n";
       }
       close fh;
     }
     $now = localtime time;
     print "End time: ",$now,"\n";
     if ($? != 0) {
        print stderr "Decoder Failure!\n";
        exit -1;
     } 
     print stderr "Success.\n";
     sleep $SLEEP; # nfs weirdness with slow writes?!?!
     print "gziping $iter_nbest_list\n";
		 `$SORT -t '|' -n -s $iter_nbest_list | gzip > $iter_nbest_list.gz`;
     unlink("$iter_nbest_list");
   } else {
     print "skipping decoding for iter $iter ($first_active_iter)\n";
   }

   if ($opt_type eq 'bleu-ter') {
     my $terStr = `java $java_flags edu.stanford.nlp.mt.metrics.TERMetric $referenceList < $iter_trans 2>&1`; chomp $terStr;
		 $terStr =~ /TER = -(\S+)/; 
		 my $terScore = $1;
		 my $bleuStr = `java $java_flags edu.stanford.nlp.mt.metrics.BLEUMetric $referenceList < $iter_trans 2>&1`; chomp $bleuStr;
		 $bleuStr =~ /BLEU = (\S+),/; 
		 my $bleuScore = $1;
     $trans_eval = "(TER-BLEU)/2 = ".(($terScore-$bleuScore)/2).", TER = $terScore, $bleuStr\n";
   } elsif ($opt_type eq 'ter') {
     $trans_eval = `java $java_flags edu.stanford.nlp.mt.metrics.TERMetric $referenceList < $iter_trans 2>&1`; 
   } elsif($opt_type eq 'terp') {
     $trans_eval = `java $java_flags edu.stanford.nlp.mt.metrics.TERpMetric $referenceList < $iter_trans 2>&1`; 
   } elsif($opt_type eq 'terpa') {
     $trans_eval = `java $java_flags -Dterpa edu.stanford.nlp.mt.metrics.TERpMetric $referenceList < $iter_trans 2>&1`; 
   } elsif($opt_type eq 'meteor') {
     $trans_eval = `java $java_flags edu.stanford.nlp.mt.metrics.METEORMetric $referenceList < $iter_trans 2>&1`; 
   } elsif($opt_type eq 'per') {
     $trans_eval = `java $java_flags edu.stanford.nlp.mt.metrics.PERMetric $referenceList < $iter_trans 2>&1`; 
   } elsif($opt_type =~ /meteor:/) {
     @abg = split /:/, $opt_type;
     $trans_eval = `java -Dabg=$abg[1]:$abg[2]:$abg[3] $java_flags edu.stanford.nlp.mt.metrics.METEORMetric $referenceList < $iter_trans 2>&1`; 
   } elsif ($opt_type =~ /^bleu:/) {
     @fields = split /:/, $opt_type;
     $trans_eval = `java $java_flags edu.stanford.nlp.mt.metrics.BLEUMetric -order $fields[1] $referenceList < $iter_trans 2>&1`; 
   } else { # bleu or cmert path, the latter implies bleu 
     $trans_eval = `java $java_flags edu.stanford.nlp.mt.metrics.BLEUMetric $referenceList < $iter_trans 2>&1`; 
   }

   chomp $trans_eval;
	 $trans_eval =~ s/\n/\n# /g; # comment $trans_eval, since printed inside ini file
   
   print stderr "$trans_eval\n";
  
   # Update decoder.ini file with actual Eval score that it acheived 
   open difh, "$iter_decoder_ini" or die;
   undef @alt_decoder_ini;
   while (<difh>) { chomp;
      push @alt_decoder_ini, $_;
      push @alt_decoder_ini, "# Actual Translation $trans_eval" 
         if (/^# Prior n-best Eval:/);
   }
   close difh;
   open difh, ">$iter_decoder_ini" or die;
   foreach $line (@alt_decoder_ini) {
     print difh "$line\n";
   }
   close difh;
   
   print stderr "\nPreparing to run MERT\n";
   print stderr 
   "------------------------------------------------------------------------\n";
   $iter_cmert_nbest = "$work_dir/cmert.$iter.nbest";
   print stderr "\n";

   $local_iter_pcumulative_nbest = "$TMP/phrasal.$$.$iter.combined.nbest";
   $iter_pcumulative_nbest = "$work_dir/phrasal.$iter.combined.nbest.gz";
   
   if ($iter >= $first_active_iter) {
	   print stderr "Building cummulative nbest list:\n$iter_pcumulative_nbest\n";
	   
	   if ($iter == 0) {
        print "cp $iter_nbest_list.gz $iter_pcumulative_nbest\n";
	      `cp $iter_nbest_list.gz $iter_pcumulative_nbest`;
	   } else {
	     # $prior_pcumulative_nbest = "$work_dir/phrasal.".($iter-1).".combined.nbest";
	     unlink($local_iter_pcumulative_nbest);
	     unlink($iter_pcumulative_nbest);
	     for ($prior_iter = max(0, $iter-$NBEST_HISTORY_WINDOW);
	          $prior_iter < $iter; $prior_iter++) {
	          $prioriter_nbest_list = "$work_dir/phrasal.$prior_iter.nbest";
	          `zcat $prioriter_nbest_list.gz | sed 's/|||[^|]*|||[^|]*\$//' >> $local_iter_pcumulative_nbest`;    
	      }
	      `zcat $iter_nbest_list.gz  | sed 's/|||[^|]*|||[^|]*\$//'   >> $local_iter_pcumulative_nbest`; 
   
   
	     $temp_unsorted_uniq = "$TMP/temp_unsorted.uniq.gz";
	     `$SORT $local_iter_pcumulative_nbest -u | gzip > $temp_unsorted_uniq`; 
			 unlink("$local_iter_pcumulative_nbest");
	     $totalNbestListSize = `zcat $temp_unsorted_uniq | wc -l`;
	     chomp $totalNbestListSize;
	     print stderr "Total unique entries on cumulative nbest list $totalNbestListSize\n";
	     print stderr "Total unique entries on last cumulative nbest list $lastTotalNbestListSize\n";
	     
	     
	     if ($totalNbestListSize == $lastTotalNbestListSize) {
	  		print stderr "Done as n-best list has not grown, $totalNbestListSize == $lastTotalNbestListSize\n";
	  		last;   	
	     }
	     
	     $lastTotalNbestListSize = $totalNbestListSize;  
	     `zcat $temp_unsorted_uniq | $SORT -n -k 1 -s | gzip > $iter_pcumulative_nbest`;
			 unlink("$temp_unsorted_uniq");
	   }
   } else {
   	 print stderr "Skipping building cummulative nbest list for iter $iter ($first_active_iter)\n";
   }
   
   $next_iter_weights = "$work_dir/phrasal.".($iter+1)."$WEIGHTS_SUFF";
   if (!$cmert_dir) {
	    $jmert_log = "$work_dir/jmert.$iter.log";
   	  if ($iter >= $first_active_iter) {
	      unlink($next_iter_weights);
				my $all_iter_weights = $iter_weights;
				for(my $i = $iter-1; $i>=0; --$i) {
                                        if ($i != 0) {
					$all_iter_weights .= ",$work_dir/phrasal.$i$WEIGHTS_SUFF";
					} else {
					$all_iter_weights .= ",$work_dir/phrasal.$i.wts";
                                        }
				}
				my $optOut = "$work_dir/jmert.$iter.opt";
        $RANDOM_SEED = ($iter<<16) + 33*$iter;
        print "Random Seed: $RANDOM_SEED\n";
				my $mertCMD = "java $mert_java_flags edu.stanford.nlp.mt.tune.MERT -a $optOut.feats -N $opt_flags -s $iter $opt_type $iter_pcumulative_nbest $iter_nbest_list.gz $all_iter_weights $commaRefList $next_iter_weights > $jmert_log 2>&1";
	      print stderr "MERT command: $mertCMD\n";
	      `$mertCMD`;
				`cat $optOut.feats | sed 's/ |||.*//' > $optOut.trans`;
	      if (not -e $next_iter_weights) {
	        print stderr "Exiting, error running $opt_type MERT\n";
	        exit -1;
	      }
				if($ORACLE) {
					print STDERR `cat $next_iter_weights | grep -v BLEU > $next_iter_weights.tmp`;
					print STDERR `echo "BLEU $ORACLE" | cat - $next_iter_weights.tmp > $next_iter_weights`;
					unlink "$next_iter_weights.tmp"
				}
				unlink $iter_pcumulative_nbest if $DELETE_COMBINED_NBEST;
   	  } else {
   	  	print stderr "Skipping running JMERT for iter $iter ($first_active_iter)\n";
   	  }
   	  
      open jmlfh, $jmert_log or die "can't open jmert log file $jmert_log";

			my $obj_diff = 1;
      $converge_info = "";
      while (<jmlfh>) { chomp;
         if (/>>>\[Converge Info\]/) {
           $converge_info = $_;
         }
         if (/^Final Eval Score:/) {
           $nbest_eval = $_;
           $nbest_init_eval = $nbest_eval;
           $nbest_eval =~ s/.*->\s*//;
           $nbest_init_eval =~ s/->.*//g;
           $nbest_init_eval =~ s/.*Score: //;
         }
         if (/^Obj diff:/) {
         	$obj_diff = $_;
         	$obj_diff =~ s/^Obj diff: *//g;
         }
      }
      close jmlfh;
      $out_nbest_eval = sprintf "%.3f", $nbest_eval;
      $out_nbest_init_eval = sprintf "%.3f", $nbest_init_eval;
      print stderr "Eval($opt_type) score on n-best list: $out_nbest_eval (up from $out_nbest_init_eval)\n\n";
      
      print "Objective diff: $obj_diff\n";
      if ($obj_diff < $MIN_OBJ_DIFF) {
   	    print stderr "Done as obj diff $obj_diff < $MIN_OBJ_DIFF\n";
   	    last;
      }   
   } else {
   
      $iter_cumulative_nbest = "$work_dir/cmert.$iter.combined.nbest";
      print stderr 
       "Converting nbest list to cmert nbest list:\n$iter_cumulative_nbest\n\n";
      print "$SCRIPTS_DIR/phrasal_nbest_to_cmert_nbest.pl < $iter_pcumulative_nbest 2>&1 > $iter_cumulative_nbest";
      `zcat $iter_pcumulative_nbest.gz | $SCRIPTS_DIR/phrasal_nbest_to_cmert_nbest.pl 2>&1 > $iter_cumulative_nbest`;
   
      
      print "cmd: $SORT -mn -t\\| -k 1,1 $iter_cumulative_nbest | $cmert_dir/score-nbest.py $referenceList $work_dir/ 2>&1\n";
      $log = `$SORT -mn -t\\| -k 1,1 $iter_cumulative_nbest | $cmert_dir/score-nbest.py $referenceList $work_dir/ 2>&1`;
      
      if ($? != 0) {
        print stderr "Failure during the production of: feats.opt & cands.opts\n";
        print stderr "Log:\n$log\n";
        exit -1; 
      }
   
      $init_opt_file = "$work_dir/init.$iter.opt";
      $cmert_weights = "$work_dir/cmert.$iter$WEIGHTS_SUFF";
      print stderr "\nProducing cmert init.opt:\n$init_opt_file\n\n";
      $cmert_feature_names = `$SCRIPTS_DIR/phrasal_weights_to_cmert_weights.pl $iter_weights $iter_cumulative_nbest 2>&1 > $cmert_weights`;
      chomp $cmert_feature_names;
      $cmert_feature_names =~ s/: / /g;
      @cmert_feature_names = split /\s+/, $cmert_feature_names;
   
      open cwts, "$cmert_weights" or die;
      $line = <cwts>; close cwts; chomp $line;
      @fields = split /\s+/, $line;
   
      open init_opt_fh, ">$init_opt_file" or die;
      $fields = @fields;
      for ($i = 0; $i < $fields; $i++) {
        if ($POSITIVE_WT_ONLY_FEATURES{$cmert_feature_names[$i]} &&
            $WEIGHT_MIN < 0) {
           print init_opt_fh "0.0 ";
        } else {
           print init_opt_fh "$WEIGHT_MIN ";
        }
      } print init_opt_fh "\n"; 
      for ($i = 0; $i < @fields; $i++) {
        print init_opt_fh "$WEIGHT_MAX ";
      } print init_opt_fh "\n"; 
      for ($i = 0; $i < @fields; $i++) {
        print init_opt_fh "$fields[$i] ";
      } print init_opt_fh "\n"; 
      close init_opt_fh;
   
      `cp $init_opt_file $work_dir/init.opt`;
       
      $cmert_log = "$work_dir/cmert.$iter.log";
      print "cmd: (cd $work_dir; $cmert_dir/mert -d $fields )>$cmert_log 2>&1\n";
      `(cd $work_dir; $cmert_dir/mert -d $fields )>$cmert_log 2>&1`;
      if ($? != 0) {
        print stderr "Failure running mert!\n";
        exit -1;
      }
   
      open cmlfh, "$cmert_log" or dir;
      while (<cmlfh>) { chomp;
         if (/^Best point:/) {
           $nbest_eval = $_; 
           $nbest_eval =~ s/.*=>\s*//;
         }
      }
      close cmlfh; 
      
      $out_nbest_eval = sprintf "%.3f", $nbest_eval;
      print stderr "BLEU score on n-best list: $out_nbest_eval\n\n";
     
      $cmert_produced_wts = "$work_dir/weights.txt";
      print stderr "Converting cmert produced weights:\n$cmert_produced_wts\n".
            "to phrasal weights for next iteration.\n\n";
      `$SCRIPTS_DIR/cmert_weights_phrasal_weights.pl $cmert_produced_wts  $iter_cumulative_nbest 2>&1 > $next_iter_weights`;
      unlink($iter_cumulative_nbest);
   }


   #$max_weight_delta = `$SCRIPTS_DIR/phrasal_weight_delta.pl -max $iter_weights $next_iter_weights 2>&1`;
   if ($converge_info) {
      print stderr "Using converge info\n+$converge_info\n";
      $ObjDiff=$converge_info;$ObjDiff=~s/.*ObjDiff\(//;$ObjDiff=~s/\).*//g;
      $ObjFinal=$converge_info;$ObjFinal=~s/.*ObjFinal\(//;$ObjFinal=~s/\).*//g;
      print stderr "ObjDiff: $ObjDiff\n";
      print stderr "ObjFinal: $ObjFinal\n";
      $L2DInit=$converge_info;$L2DInit=~s/.*L2DInit\(//;$L2DInit=~s/\).*//g;
      print stderr "L2DInit:  $L2DInit\n";
      $L2XInit=$converge_info;$L2XInit=~s/.*L2XInit\(//;$L2XInit=~s/\).*//g;
      print stderr "L2XInit:  $L2XInit\n";
      $L2XFinal=$converge_info;$L2XFinal=~s/.*L2XFinal\(//;$L2XFinal=~s/\).*//g;
      print stderr "L2XFinal:  $L2XFinal\n";
      $L2XEffective = min($L2XInit, $L2XFinal);
      print stderr "L2XEffective: $L2XEffective\n";
      $convTest1 = $ObjDiff/$ObjFinal;
      $convTest2 = $L2DInit/max(1,$L2XEffective);
      print stderr "Test1 ObjDiff:$ObjDiff/ObjFinal:$ObjFinal = $convTest1\n";
      print stderr "Test2 L2DInit:$L2DInit/max(1,L2XEffective:$L2XEffective) = $convTest2\n";
      if ($LastObj ne "null") {
        $v = abs($ObjFinal - $LastObj);
        $v_frac = $v/$ObjFinal;
        print stderr "Objective difference from last iteration: $v frac: $v_frac\n";
        if ($v_frac < $OBJ_DIFF_STOP) {
          print stderr "Done as objective fractional diff:$v_frac < $OBJ_DIFF_STOP \n";
          last;
        } 
      } 
      $LastObj = $ObjFinal;
   } else {
   print stderr "Can't find converge info - falling back to weight delta test\n";
	 my $cmd = "java edu.stanford.nlp.mt.tools.CompareWeights $iter_weights $next_iter_weights  2>&1";
   print stderr "cmd: $cmd\n";
   $max_weight_delta = `$cmd`;
   chomp $max_weight_delta; 
   print stderr "Max weight delta: '$max_weight_delta' stopping @ ($MIN_WEIGHT_DELTA)\n\n";
   if ($max_weight_delta < $MIN_WEIGHT_DELTA) {
      print stderr "Done as max weight delta $weight_delta < $MIN_WEIGHT_DELTA\n\n";
      last; 
   }
   }
}

$phrasal_final_ini = "$work_dir/phrasal.final.ini\n";
$phrasal_final_wts = "$work_dir/phrasal.final$WEIGHTS_SUFF\n";

print stderr "\nOptimization Complete\n";
print stderr 
   "------------------------------------------------------------------------\n\n";

print stderr "Creating final phrasal ini: $phrasal_final_ini\n";
open difh, ">$phrasal_final_ini" or die;
foreach $line (@decoder_ini) { print difh "$line\n"; }
print difh "[weights-file]\n$phrasal_final_wts\n"; close difh;

print stderr "Creating final weights file: $phrasal_final_wts\n"; 
`cp $next_iter_weights $phrasal_final_wts`;

print stderr "Done.\n";
