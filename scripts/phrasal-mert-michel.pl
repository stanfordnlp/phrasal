#!/usr/bin/perl

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

$WEIGHT_MIN = -1;
$WEIGHT_MAX = 1;
$DEFAULT_MAX_ITERS = 25;
$DEFAULT_WORK_DIR = "pmert-dir";
$DEFAULT_NBEST_SIZE = 100;
$DEFAULT_JAVA_FLAGS = "-Xmx6g";
$MIN_WEIGHT_DELTA = 0.001;

$SCRIPTS_DIR = $0;
$SCRIPTS_DIR =~ s/\/[^\/]*$//;
if ($SCRIPTS_DIR eq $0) {
  $SCRIPTS_DIR = `which $0`;
  $SCRIPTS_DIR =~ s/\/[^\/]*$//;
}

$EXTERNAL_SCRIPTS_DIR="$SCRIPTS_DIR/../external_scripts";

$work_dir=$DEFAULT_WORK_DIR;
$nbest_size=$DEFAULT_NBEST_SIZE;
$java_flags=$DEFAULT_JAVA_FLAGS;

if (not ($work_dir =~ /^\//)) {
  $pwd = `pwd`; chomp $pwd;
  $work_dir = $pwd."/".$work_dir;
} 

%WT_STORE= (
  "weight-d"=>["LinearDistortion", 
               "LexR::monotoneWithPrevious", 
               "LexR::swapWithPrevious",
               "LexR::discontinousWithPrevious",
               "LexR::monotoneWithNext", 
               "LexR::swapWithNext", 
               "LexR::discontinousWithNext"],
  "weight-l"=>["LM"],
  "weight-t"=>["TM:phi(t|f)", "TM:lex(t|f)", "TM:phi(f|t)", "TM:lex(f|t)", 
               "TM:phrasePenalty"],
  "weight-w"=>["WordPenalty"]
);

foreach $arg (@ARGV) {
  if (not ($arg =~ /^--.*/)) {
     push @POSITIONAL_ARGS, $arg;
     next;
  }
  if ($arg =~ /^--working-dir=.*/) {
     $work_dir = $arg;
     $work_dir =~ s/^--working-dir=//;
  } elsif ($arg =~ /^--nbest=.*/) {
     $nbest_size = $arg;
     $nbest_size =~ s/^--nbest=//g;
  } elsif ($arg =~ /^--java-flags=.*/) {
     $java_flags = $arg;
     $java_flags =~ s/^--java-flags=//g;
  } else {
     print stderr "Unrecognized flag $arg\n";
     exit -1;
   }
}

$work_dir =~ s/\/$//g;

if (@POSITIONAL_ARGS != 4) {
   $nm = $0; $nm =~ s/.*\///g;
   print stderr "Usage:\n\t$nm input-text references cmert-dir decoder.ini\n";
   exit -1;
}

$input_text   = $POSITIONAL_ARGS[0];
$references   = $POSITIONAL_ARGS[1];
$cmert_dir    = $POSITIONAL_ARGS[2];
$decoder_ini  = $POSITIONAL_ARGS[3];

if (-e $references."0") {
  $referenceList = "";
  for ($i = 0; -e "$references$i"; $i++) {
     $referenceList .= " " if ($referenceList);
     $referenceList .= "$references$i";
  }
}

%POSITIVE_WT_ONLY_FEATURES = (
  "LM"=>1,
  "LinearDistortion"=>1,
  "TM:lex(f|t)"=>1,
  "TM:lex(t|f)"=>1,
  "TM:phi(f|t)"=>1,
  "TM:phi(t|f)"=>1,
  "LexR::discontinousWithNext"=>1, 
  "LexR::discontinousWithPrevious"=>1,
  "LexR::monotoneWithNext"=>1,
  "LexR::monotoneWithPrevious"=>1, 
  "LexR::swapWithNext"=>1,
  "LexR::swapWithPrevious"=>1,
  "IBM1TGS:full"=>1,
  "IBM1TGS:tmo"=>1,
  "SegmentationLogProb"=>0.1,
);

%DEFAULT_WEIGHTS = (
  "LM"=>1.0, "LinearDistortion"=>"1.0",
  "TM:lex(f|t)"=>0.3, 
  "TM:lex(t|f)"=>0.2,
  "TM:phi(f|t)"=>0.3,
  "TM:phi(t|f)"=>0.2,
  "TM:phrasePenalty"=>0.0,
  "UnknownWord"=>1.0,
  "WordPenalty"=>0.0,
  "SegmentationLogProb"=>0.1,
	);

foreach $arg (@ARGV) {
  if (not ($arg =~ /^--.*/)) {
     push @POSITIONAL_ARGS, $arg;
     next;
  }
  if ($arg =~ /^--working-dir=.*/) {
     $work_dir = $arg;
     $work_dir =~ s/^--working-dir=//;
  } elsif ($arg =~ /^--nbest=.*/) {
     $nbest_size = $arg;
     $nbest_size =~ s/^--nbest=//g;
  } elsif ($arg =~ /^--java-flags=.*/) {
     $java_flags = $arg;
     $java_flags =~ s/^--java-flags=//g;
   } else {
     print stderr "Unrecognized flag $arg\n";
     exit -1;
   }
}

$work_dir =~ s/\/$//g;

print stderr "MERT - Phrasal\n";
print stderr "\tinput text: $input_text\n";
print stderr "\treferences: $references\n";
print stderr "\tcmert_dir: $cmert_dir\n";
print stderr "\tdecoder_ini: $decoder_ini\n";
print stderr "\twork dir: $work_dir\n";
print stderr "\tnbest size: $nbest_size\n";
print stderr "\tjava flags: $java_flags\n";
print stderr "\n";

if (not (-e $work_dir)) {
   mkdir($work_dir);
} 

open difh, $decoder_ini or die "Can't open $decoder_ini";

%strip_fields = ("n-best-list"=>1, "weights-file"=>1);
$strip_line = 0;
%init_wts = %DEFAULT_WEIGHTS;
while (!eof(difh)) {
  $line = <difh>; chomp $line;
  if ($line =~ /^\s*$/ or $line =~ /^\s*#.*$/ or $line =~ /^\[.*/) { 
     $strip_line = 0;
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

$ini_weight_file = "$work_dir/phrasal.0.wts\n";

print stderr "Writing initial weights file:\n$ini_weight_file\n";
open wtfh, ">$ini_weight_file" or die;
foreach $key (keys %init_wts) {
   print "$key => $init_wts{$key}\n";
   print wtfh "$key $init_wts{$key}\n"
}
close wtfh;


for ($iter = 0; $iter < $DEFAULT_MAX_ITERS; $iter++) {
   print stderr "Iter: $iter\n"; 
   print stderr 
   "========================================================================".
   "\n\n";
   $iter_nbest_list = "$work_dir/phrasal.$iter.nbest";
   print stderr "Preparing to produce nbest list:\n$iter_nbest_list\n";
   print stderr 
   "------------------------------------------------------------------------\n\n";
   $iter_weights = "$work_dir/phrasal.$iter.wts";
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
   $out_nbest_bleu = ($nbest_bleu ? sprintf "%.3f", $nbest_bleu : "n/a");
   print difh "# Prior n-best BLEU: $out_nbest_bleu\n";
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
   print "Cmd:\njava $java_flags mt.PseudoMoses $iter_decoder_ini < $input_text > $iter_trans 2>$iter_dlog\n\n";
  if (!$ENV{"SDI$iter"}) { 
   `java $java_flags mt.PseudoMoses $iter_decoder_ini < $input_text > $iter_trans 2>$iter_dlog`;
   
   if ($? != 0) {
      print stderr "Decoder Failure!\n";
      exit -1;
   } 
   print stderr "Success.\n";
   } else {
     print "skipping iter $iter\n";
   }

   $trans_bleu = `$EXTERNAL_SCRIPTS_DIR/multi-bleu.perl $references < $iter_trans 2>&1`;
   chomp $trans_bleu;
   
   print stderr "BLEU eval cmd: $EXTERNAL_SCRIPTS_DIR/multi-bleu.perl $references < $iter_trans\n";
   print stderr "$trans_bleu\n";
  
   # Update decoder.ini file with actual BLEU score that it acheived 
   open difh, "$iter_decoder_ini" or die;
   undef @alt_decoder_ini;
   while (<difh>) { chomp;
      push @alt_decoder_ini, $_;
      push @alt_decoder_ini, "# Actual Translation $trans_bleu" 
         if (/^# Prior n-best BLEU:/);
   }
   close difh;
   open difh, ">$iter_decoder_ini" or die;
   foreach $line (@alt_decoder_ini) {
     print difh "$line\n";
   }
   close difh;
   

   print stderr "\nPreparing to run CMERT\n";
   print stderr 
   "------------------------------------------------------------------------\n";
   $iter_cmert_nbest = "$work_dir/cmert.$iter.nbest";
   print stderr "\n";

   $iter_pcommulative_nbest = "$work_dir/phrasal.$iter.combined.nbest";
   print stderr "Building cummulative nbest list:\n$iter_pcommulative_nbest\n";
   if ($iter == 0) {
      `cp $iter_nbest_list $iter_pcommulative_nbest`;
   } else {
      $prior_pcommulative_nbest = "$work_dir/phrasal.".($iter-1).".combined.nbest";
      `cp $prior_pcommulative_nbest $iter_pcommulative_nbest`;
      `cat $iter_nbest_list >> $iter_pcommulative_nbest`; 
   }
   $temp_unsorted = "$work_dir/temp_unsorted";
   `cp $iter_pcommulative_nbest $temp_unsorted`;
   print "cmd: sort +0n -s $temp_unsorted > $iter_pcommulative_nbest\n";
   `sort -n -k 1 -s $temp_unsorted > $iter_pcommulative_nbest`;
    #unlink($temp_unsorted);

   $iter_commulative_nbest = "$work_dir/cmert.$iter.combined.nbest";
   print stderr 
    "Converting nbest list to cmert nbest list:\n$iter_commulative_nbest\n\n";
   my $convert_cmd = "$SCRIPTS_DIR/phrasal_nbest_to_cmert_nbest.pl < $iter_pcommulative_nbest 2>&1 > $iter_commulative_nbest";
	 print "n-best conversion command: $convert_cmd\n";
	 `$convert_cmd`;

   my $nbest_cmd = "sort -mn -t\\| -k 1,1 $iter_commulative_nbest | tr 'A-Z' 'a-z' | $cmert_dir/score-nbest.py $referenceList $work_dir/ 2>&1";
	 print "score-nbest.py command: $nbest_cmd\n";
   $log = `$nbest_cmd`;
   
   if ($? != 0) {
     print stderr "Failure during the production of: feats.opt & cands.opts\n";
     print stderr "Log:\n$log\n";
     exit -1; 
   }

   $init_opt_file = "$work_dir/init.$iter.opt";
   $cmert_weights = "$work_dir/cmert.$iter.wts";
   print stderr "\nProducing cmert init.opt:\n$init_opt_file\n\n";
   $cmert_feature_names = `$SCRIPTS_DIR/phrasal_weights_to_cmert_weights.pl $iter_weights $iter_commulative_nbest 2>&1 > $cmert_weights`;
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
        $nbest_bleu = $_; 
        $nbest_bleu =~ s/.*=>\s*//;
      }
   }
   close cmlfh; 
   
   $out_nbest_bleu = sprintf "%.3f", $nbest_bleu;
   print stderr "BLEU score on n-best list: $out_nbest_bleu\n\n";
  
   $next_iter_weights = "$work_dir/phrasal.".($iter+1).".wts";
   $cmert_produced_wts = "$work_dir/weights.txt";
   print stderr "Converting cmert produced weights:\n$cmert_produced_wts\n".
         "to phrasal weights for next iteration.\n\n";
   `$SCRIPTS_DIR/cmert_weights_phrasal_weights.pl $cmert_produced_wts  $iter_commulative_nbest 2>&1 > $next_iter_weights`;

   # Okay, cmert sucks in that it seems to sometimes ignore boundry 
   # constraints.
   #
   # So...here we manually enforce them 
   open fh, $next_iter_weights or die;
   undef @next_weights;
   while (<fh>) { chomp;
     @fields = split /\s+/;
     $feature_name = $fields[0];
     $feature_value = $fields[1];
     $feature_value = 0.0 if ($POSITIVE_WT_ONLY_FEATURES{$feature_name} &&
         $feature_value < 0);
     $feature_value = $WEIGHT_MIN if ($feature_value < $WEIGHT_MIN);
     $feature_value = $WEIGHT_MAX if ($feature_value < $WEIGHT_MIN);
     push @next_weights, "$feature_name $feature_value"; 
   }
   close fh;
   open fh, ">$next_iter_weights" or die;
   foreach $pair (@next_weights) { 
     print fh "$pair\n";
   }
   close fh;

   $weight_delta = `$SCRIPTS_DIR/phrasal_weight_delta.pl $iter_weights $next_iter_weights`;
   chomp $weight_delta; 
   print stderr "Weight delta: $weight_delta\n\n";
   if ($weight_delta < $MIN_WEIGHT_DELTA) {
      print stderr "Done as $weight_delta < $MIN_WEIGHT_DELTA\n\n";
      last; 
   }
}

$phrasal_final_ini = "$work_dir/phrasal.final.ini\n";
$phrasal_final_wts = "$work_dir/phrasal.final.wts\n";

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
