#!/usr/bin/perl -U

##################################################################
#                                                                
# 
# Copyright© 2005 by BBN Technologies and University of Maryland (UMD)
#
# BBN and UMD grant a nonexclusive, source code, royalty-free right to
# use this Software known as Translation Error Rate COMpute (the
# "Software") solely for research purposes. Provided, you must agree
# to abide by the license and terms stated herein. Title to the
# Software and its documentation and all applicable copyrights, trade
# secrets, patents and other intellectual rights in it are and remain
# with BBN and UMD and shall not be used, revealed, disclosed in
# marketing or advertisement or any other activity not explicitly
# permitted in writing.
#
# BBN and UMD make no representation about suitability of this
# Software for any purposes.  It is provided "AS IS" without express
# or implied warranties including (but not limited to) all implied
# warranties of merchantability or fitness for a particular purpose.
# In no event shall BBN or UMD be liable for any special, indirect or
# consequential damages whatsoever resulting from loss of use, data or
# profits, whether in an action of contract, negligence or other
# tortuous action, arising out of or in connection with the use or
# performance of this Software.
#
# Without limitation of the foregoing, user agrees to commit no act
# which, directly or indirectly, would violate any U.S. law,
# regulation, or treaty, or any other international treaty or
# agreement to which the United States adheres or with which the
# United States complies, relating to the export or re-export of any
# commodities, software, or technical data.  This Software is licensed
# to you on the condition that upon completion you will cease to use
# the Software and, on request of BBN and UMD, will destroy copies of
# the Software in your possession.
#
#                                                                
#   Version 6 - 3/01/2006                                       
#   Matthew Snover (snover@cs.umd.edu)                           
#                                                                
##################################################################

##################################################################
#                                                                #
# tercom.pl -h <hypothesis-file> -r <reference-file> [options]   #
#     -r   Specify the reference file                            #
#     -h   Specify the hypothesis file                           #
#     -a   Specify other reference file to be used for purposes  #
#          of calculating the ave reference length               #
#     -s   Turn on case sensitivity                              #
####     -c   Load cost matrix                                   #
#     -d   Turn on some diagnostics                              #
#     -S   Turn off Shifting                                     #
#     -q   Don't show detailed shift path                        #
#     -n   Specify output name                                   #
#     -o   Specify output types (sum/pra)                        #
#     -p   Leave Punctuation Untokenized                         #
#     -P   Remove Punctuation                                    #
#     -f   Use more speed optimizations (default 2)              #
#     -b   Set Beam Width (default 10)                           #
#     -i   Input File Type: text or sgm (default text)           #
#     -N   Apply MT eval-style normalization to both hyp and ref #
#                                                                #
##################################################################

##################################################################
#                                                                #
#   CHANGE LOG                                                   #
#                                                                #
#   Version 6b - 03/28/2006                                      #
#       Added document level scoring output (sum_doc file)       #
#                                                                #
#   Version 6 - 03/01/2006                                       #
#       Added option -N to normalize hyp and ref as in           #
#         MT eval scoring                                        #
#       Added support for n-best lists                           #
#                                                                #
#   Version 5 - 11/10/2005                                       #
#       changed program name from calc_ter to tercom             #
#       Change defult options                                    #
#       Added support for sgm files                              #
#       Added option for beam width                              #
#                                                                #
#   Version 4 - 9/13/2005                                        #
#       Added speed optimizations                                #
#                                                                #
#   Version 3 - 7/29/2005                                        #
#       Added support for cost matrix input file                 #
#                                                                #
#   Version 2 - 6/26/2005                                        #
#       Initial Public Release                                   #
#                                                                #
##################################################################

use FileHandle;
STDOUT->autoflush(1);

##################### DEFAULT PARAMETERS ##########################

# standard costs
my $MATCH_COST = 0;
my $INSERT_COST = 1;
my $DELETE_COST = 1;
my $SUB_COST = 1;
my $SHIFT_COST = 1;

# Super high value used to mark an impossible path
my $INF = 99999999999;

# Maximum Length Sequence to Shift
# Set to 0 to turn on shifting
$MAX_SHIFT_SIZE = 10;

# Maximum Distance To Shift
$MAX_SHIFT_DIST = 50;

############################# ACTUAL CODE ##########################

#Default options values
my $OUTPUTNAME = $options{h};
my %OUTPUT_TYPES;
my $CASE_ON = 0;
my $LEAVE_PUNC = 0;
my $REMOVE_PUNC = 0;
my $DIAGNOSTICS = 0;
my $DETAILED_SHIFT = 1;
my $COSMETIC_SH = 1;
my %options = ();
my $FAST_LEVEL = 2;
my $BEAMWIDTH = 10;
my $INPUTTYPE = 0;  # 0=text, 1=sgm
my $MTEVAL_NORMALIZE = 0;
get_options();

####################### START DOING REAL STUFF #################

# Load the data
my %REFS;
my %HYPS;
my %LENS;
my %DOCS;
my %DICT;
my @REVDICT;

my %SUB_MATRIX;
my %POSSIBLE_SUB;

my %INS_MATRIX;
my %POSSIBLE_INS;

my %DEL_MATRIX;
my %POSSIBLE_DEL;

load_data($options{h},\%HYPS, 1);
load_data($options{r},\%REFS, 0);
if (exists($options{a})) {
    load_data($options{a}, \%LENS, 0, 1);
}
if (exists($options{c})) {
    load_cost_file($options{c}, \%SUB_MATRIX, \%POSSIBLE_SUB, 
		   \%INS_MATRIX, \%POSSIBLE_INS, 
		   \%DEL_MATRIX, \%POSSIBLE_DEL);
}

if ($OUTPUT_TYPES{"pra"}) {
    open (PRA_OUT, ">${OUTPUTNAME}.pra") or (print "Cannot open ${OUTPUTNAME}.pra for writing\n" and exit(-1));
    print STDERR "Pra Output: ${OUTPUTNAME}.pra\n";
}
if ($OUTPUT_TYPES{"sum_doc"}) {
    open (DOCSUM_OUT, ">${OUTPUTNAME}.sum_doc") or (print "Cannot open ${OUTPUTNAME}.sum_doc for writing\n" and exit(-1));
    print STDERR "Sum (Document) Output: ${OUTPUTNAME}.sum_doc\n";
    print DOCSUM_OUT "Hypothesis File: $options{h}\n";
    print DOCSUM_OUT "Reference File: $options{r}\n";
    if (exists($options{a})) {
	print DOCSUM_OUT "Ave-Reference File: $options{a}\n";
    } else {
	print DOCSUM_OUT "Ave-Reference File: $options{r}\n";
    }
}


if ($OUTPUT_TYPES{"sum"}) {
    open (SUM_OUT, ">${OUTPUTNAME}.sum") or (print "Cannot open ${OUTPUTNAME}.sum for writing\n" and exit(-1));
    print STDERR "Sum Output: ${OUTPUTNAME}.sum\n";
    print SUM_OUT "Hypothesis File: $options{h}\n";
    print SUM_OUT "Reference File: $options{r}\n";
    if (exists($options{a})) {
	print SUM_OUT "Ave-Reference File: $options{a}\n";
    } else {
	print SUM_OUT "Ave-Reference File: $options{r}\n";
    }
}

if ($OUTPUT_TYPES{"sum_nbest"}) {
    open (SUM_NB_OUT, ">${OUTPUTNAME}.sum_nbest") or (print "Cannot open ${OUTPUTNAME}.sum_nbest for writing\n" and exit(-1));
    print STDERR "Sum (nbest) Output: ${OUTPUTNAME}.sum_nbest\n";
}

my $TOTAL_EDIT_NUM = 0;
my $TOTAL_REF_NUM = 0;

if ($OUTPUT_TYPES{"sum"}) {
    printf(SUM_OUT "%-19s | %-4s | %-4s | %-4s | %-4s | %-4s | %-6s | %-8s | %-8s\n", 
	   "Sent Id", "Ins", "Del", "Sub", "Shft", "WdSh", 
	   "NumEr", "NumWd", "TER"); 
    print(SUM_OUT "-------------------------------------------------------------------------------------\n");
}


my @all_breakdown = (0 x 5);
# Score each string
foreach my $prefixed_id (sort keys %HYPS) {
    my $id = $prefixed_id;
    my $rank = -1;
    if ($prefixed_id =~ /^\d+_([^:]+)(:(\d+))?$/) {
	$id = $1;
	if ($2) {
	    $rank = $3;
	}
    }
    else {
	die "Expected sentence-counted prefix on hypothesis id, got $prefixed_id instead\n";
    }

    my $rep_id = $id;
    if ($rank >= 0) {
	$rep_id = "$id:$rank";
    }

    print STDERR "Processing sentence $rep_id\n";
    (print "Error: No matching reference for $rep_id.\n" and next) 
	if (! defined($REFS{$id}));
    printf(PRA_OUT "Sentence ID: %s\n", $rep_id) if ($OUTPUT_TYPES{"pra"});
    my ($score, $path, $ref, $hyp, $allshift) = score_sent($prefixed_id, $id);
    ($hyp, $ref, my $ohyp) = (rev_dictstr($hyp), rev_dictstr($ref), 
			      rev_dictstr($HYPS{$prefixed_id}[0]));
    my @score_breakdown = get_score_breakdown($path, $allshift);
    printf(PRA_OUT "Best Ref: %s\n", $ref) if ($OUTPUT_TYPES{"pra"});
    printf(PRA_OUT "Orig Hyp: %s\n", $ohyp) if ($OUTPUT_TYPES{"pra"});
    print PRA_OUT "\n" if ($OUTPUT_TYPES{"pra"});
    my ($hstr, $rstr, $estr, $sstr) = 
	gen_str_out($hyp, $ref, $path, $allshift);
#    my $rlen = scalar(split(/\s+/,$ref));
    my $rlen = 0;
    if (! exists($options{a})) {
	$rlen = average_rlen(@{$REFS{$id}});
    } else {
	$rlen = 0;
	foreach my $r (@{$LENS{$id}}) {
	    $rlen += $r;
	}
	$rlen /= ($#{$LENS{$id}} + 1);
    }

    print(PRA_OUT "$rstr\n$hstr\n$estr\n$sstr\n") if ($OUTPUT_TYPES{"pra"});
    printf(PRA_OUT "TER Score: %6.2f (%5.1f/%5.1f)\n", $rlen ? (100.0 * $score)/$rlen : 0.0, $score, $rlen) if ($OUTPUT_TYPES{"pra"});
    $TOTAL_EDIT_NUM += $score;
    $TOTAL_REF_NUM += $rlen;

    #document counting stuff
    my $docID = $rep_id;
    if ($rep_id =~ /^(.*)([-_].*)/) {
	$docID = $1;
    } else {
	print(STDERR "Warning: Cannot identify document and segment number for $rep_id.\n");
    }
    
    if (! (exists $DOCS{$docID})) {
	$DOCS{$docID} = [0, 0, 0, 0, 0, 0, 0.0];
        # the five from $score_breakdown, and +=$score and +=$rlen
    }

    for (my $sb = 0; $sb <= $#score_breakdown; $sb++) {
	$DOCS{$docID}[$sb] += $score_breakdown[$sb];
    }
    
    $DOCS{$docID}[5] += $score;
    $DOCS{$docID}[6] += $rlen;   
    # end document counting stuff

    if ($DETAILED_SHIFT && $OUTPUT_TYPES{"pra"}) {
	print PRA_OUT "\n";
	foreach my $shift (@{$allshift}) {
	    print_detail_shift(*PRA_OUT, @{$shift}, $ref);
	    print PRA_OUT "\n";
	}
    }
    print PRA_OUT "\n" if ($OUTPUT_TYPES{"pra"});

    for (my $sb = 0; $sb <= $#score_breakdown; $sb++) {
	$all_breakdown[$sb] += $score_breakdown[$sb];
    }

    if ($OUTPUT_TYPES{"sum"}) {
	printf(SUM_OUT "%-19s | %4i | %4i | %4i | %4i | %4i | %6.1f | %8.3f | %8.3f\n",  
	       $rep_id, @score_breakdown, $score, $rlen, $rlen ? (100.0 * $score)/$rlen : 0);
    }

   if ($OUTPUT_TYPES{"sum_nbest"}) {
	printf(SUM_NB_OUT "%-19s %4i %4i %4i %4i %4i %6.1f %6.1f %6.2f\n",
	       $rep_id, @score_breakdown, $score, $rlen, $rlen ? (100.0 * $score)/$rlen : 0);
    }
}

#Document Scoring Output
if ($OUTPUT_TYPES{"sum_doc"}) {
    printf(DOCSUM_OUT "%-19s | %-4s | %-4s | %-4s | %-4s | %-4s | %-6s | %-8s | %-8s\n", 
	   "Sent Id", "Ins", "Del", "Sub", "Shft", "WdSh", 
	   "NumEr", "NumWd", "TER"); 
    print(DOCSUM_OUT "-------------------------------------------------------------------------------------\n");
    foreach my $docID (sort keys %DOCS) {
        printf(DOCSUM_OUT "%-19s | %4i | %4i | %4i | %4i | %4i | %6.1f | %8.3f | %8.3f\n",
	       $docID,  @{$DOCS{$docID}},                           
	       (100.0 * $DOCS{$docID}[5])/$DOCS{$docID}[6]);
    }
    print(DOCSUM_OUT "-------------------------------------------------------------------------------------\n");
    printf(DOCSUM_OUT "%-19s | %4i | %4i | %4i | %4i | %4i | %6.1f | %8.3f | %8.3f\n",  "TOTAL", @all_breakdown, $TOTAL_EDIT_NUM, $TOTAL_REF_NUM, (100.0 * $TOTAL_EDIT_NUM)/$TOTAL_REF_NUM);
}
#End Document Couting Output


if ($OUTPUT_TYPES{"sum"}) {
    print(SUM_OUT "-------------------------------------------------------------------------------------\n");
    printf(SUM_OUT "%-19s | %4i | %4i | %4i | %4i | %4i | %6.1f | %8.3f | %8.3f\n",  "TOTAL", @all_breakdown, $TOTAL_EDIT_NUM, $TOTAL_REF_NUM, (100.0 * $TOTAL_EDIT_NUM)/$TOTAL_REF_NUM);
}


if ($OUTPUT_TYPES{"pra"}) {
    close(PRA_OUT);
}
if ($OUTPUT_TYPES{"sum"}) {
    close(SUM_OUT);
}
if ($OUTPUT_TYPES{"sum_doc"}) {
    close(DOCSUM_OUT);
}
if ($OUTPUT_TYPES{"sum_nbest"}) {
    close(SUM_NB_OUT);
}

####################################################################
#                       Various Functions                          #
####################################################################

sub get_options {
# Process the command line options
    use Getopt::Std;

# -h = hypothesis file
# -r = reference file
    getopts("a:h:r:n:o:sdSqpPf:b:i:N",\%options);

    if ((! defined $options{h}) || (! defined $options{r})) {
	print "usage: calc_ter.pl -h <hypothesis-file> -r <reference-file> [options]
 Options:
   -r <file>  Reference-file
   -h <file>  Hypothesis-file
   -a <file>  Other reference-file for ref length calculation
   -n <name>  Output Name
   -o <types> Output Types
" .
#   -c <file>  Cost Matrix File
"   -s         Turn on case sensitivity
   -S         Turn off shifting
   -d         Turn on some diagnositics
   -q         Don't show detailed shift path
   -p         Don't Separate Punctuation
   -P         Remove all punctuation
   -i <type>  Set input type: 'text' or 'sgm' (Default: text)
   -b <num>   Set Beam Search Width (Default: 10)
   -f <level> Use more speed optimizations (Default: 2)
              0: No speed optimizations
              1: Slightly faster DP
              2: Beam Search (Default)
              3: Disable Cosmetic Shifts (Slight Loss of Performance)
              4: Ultra Greedy Shifts (Serious Performance Loss)
              For faster speed, disable shifts altogether
   -N         Perform MT eval-like hyp and ref normalization
\n";
	exit(-1);
    }

    if (defined $options{n}) {
	$OUTPUTNAME = $options{n};
    } else {
	$OUTPUTNAME = $options{h} . ".sys";
    }

    my @L_OUT_TYPES = ("pra", "sum", "sum_nbest", "sum_doc");    
    if (defined $options{o}) {
	@L_OUT_TYPES = split(/,/, $options{o});    
    }
    foreach my $o (@L_OUT_TYPES) {
	$OUTPUT_TYPES{$o} = 1;
    }	

# Turn on fast level
    if (defined $options{f}) {
	$FAST_LEVEL = $options{f};
	$COSMETIC_SH = 0 if ($FAST_LEVEL >= 3);
    }

# Turn on case sensitivity
    if (defined $options{s}) {
	$CASE_ON = 1;
    }

#Don't separate punctuation
    if (defined $options{p}) {
	$LEAVE_PUNC = 1;
    }

#Remove all punctuation
    if (defined $options{P}) {
	$REMOVE_PUNC = 1;
    }

# Turn on diagnostics to check things out

    if (defined $options{d}) {
	$DIAGNOSTICS = 1;
    }

    $DETAILED_SHIFT = 1;
    if (defined $options{q}) {
	$DETAILED_SHIFT = 0;
    }

# Turn off Shifting
    if (defined $options{S}) {
	$MAX_SHIFT_SIZE = 0;
    }

# Set Beam Width
    if (defined $options{b}) {
	$BEAMWIDTH = $options{b};
    }

# Set Beam Width
    if (defined $options{i}) {
	$INPUTTYPE = 1 if ($options{i} =~ /^sgm$/i);
	$INPUTTYPE = 0 if ($options{i} =~ /^text$/i);
    }

    # Turn on MT eval-style hyp and ref normalization
    $MTEVAL_NORMALIZE = 0;
    if (defined $options{N}) {
	$MTEVAL_NORMALIZE = 1;
	$LEAVE_PUNC = 1;
    }
}

sub get_score_breakdown {
    # Calculate the score breakdown by type
    my ($path, $shifts) = @_;
    # INS DEL SUB SHIFT WORDS_SHIFTED
    my @spieces = (0, 0, 0, 0, 0);
    $spieces[0] = ($path =~ tr/I//);
    $spieces[1] = ($path =~ tr/D//);
    $spieces[2] = ($path =~ tr/S//);
    $spieces[3] = $#{$shifts} + 1;
    foreach my $s (@{$shifts}) {
	$spieces[4] += ($s->[1] - $s->[0]) + 1;
    }
    return @spieces;
}

sub mark_error {
    # modify the case if there is an error and case senstivity is off
    my ($s) = @_;
    return $s if ($CASE_ON);
    return uc($s);
}

sub print_detail_shift {
    # Print detailed shift information
    my ($fh, $start, $end, $dest,
	$orig_hyp, $new_hyp, $score, 
	$path, $ref) = @_;
    $new_hyp = rev_dictstr($new_hyp);
				   
    my @hwords = split(/\s+/, $orig_hyp);
    my $shifted_words = join(" ", @hwords[$start..$end]);
    # little stuff so we can print out the shift type
    my $dist = 0;
    my $direction = "";
    my $offset = 0;
    if ($dest < $start) {
	$dist = (($start - $dest) - 1);
	$offset = ($end - $start) + 1;
	$direction = "left";
    } else {
	$dist = ($dest - $end);
	$direction = "right";
    }      

    printf($fh "Shift [%s] %i words %s\n", rev_dictstr($shifted_words), $dist, $direction);
    my @allshifts;
    my @first_shift = ($start, $end, $dest);
    push @allshifts, \@first_shift;
    my ($hstr, $rstr, $estr, $sstr) = gen_str_out($new_hyp, $ref, $path, \@allshifts);
    print($fh " $rstr\n $hstr\n");
}

sub max {
    # Take the max of the args...
    my $cur;
    my $first = 1;
    foreach my $val (@_) {
	if ($first || ($val > $cur)) {
	    $cur = $val;
	    $first = 0;
	}
    }
    return $cur;
}

sub min {
    # Take the min of the args...
    my $cur;
    my $first = 1;
    foreach my $val (@_) {
	if ($first || ($val < $cur)) {
	    $cur = $val;
	    $first = 0;
	}
    }
    return $cur;
}

sub gen_str_out {    
    # create a sclite-like pra string alignment output
    my ($hyp, $ref, $path, $slist) = @_;
    my ($hstr, $rstr, $estr, $sstr) = ("HYP: ", "REF: ", "EVAL:", "SHFT:");

    my $NUMBER_SHIFT_WORDS;

    my @p = split(/,/, $path);
    my ($i, $j) = (0, 0);
    my @orig_hyp = split(/\s+/, $hyp);
    my @orig_ref = split(/\s+/, $ref);

    my @align_info;
    my $anum = 1;
    my ($ind_start, $ind_end, $ind_from, $ind_in) = 0..3;
    foreach my $shift (@{$slist}) {
	my ($ostart, $oend, $odest) = @{$shift};
	my $slen = ($oend - $ostart) + 1;
	my ($nstart, $nend, $nfrom);
	if ($odest >= $oend) {
	    #shift right
	    $nstart = ($odest + 1) - $slen;
	    $nend = $nstart + ($slen - 1);
	    $nfrom = $ostart;
	} else {
	    # shift left
	    $nstart = $odest + 1;
	    $nend = $nstart + ($slen - 1);
	    $nfrom = $ostart + $slen;
	}

	if ($anum > 1) { 
	    perform_shift_inarr(\@align_info, $ostart, $oend, $odest);
	}

	
	push (@{$align_info[$nstart][$ind_start]}, $anum);
	push (@{$align_info[$nend][$ind_end]}, $anum);
	push (@{$align_info[$nfrom][$ind_from]}, $anum);
	if ($NUMBER_SHIFT_WORDS) {
	    for (my $i = $nstart; $i <= $nend; $i++) {
		push (@{$align_info[$i][$ind_in]}, $anum);
	    }
	}
	$anum++;	
    }

    for (my $mp = 0; $mp <= $#p; $mp++) {
	my $shift_in_str = "";
	if ($p[$mp] ne "D") {
	    foreach my $a (@{$align_info[$i][$ind_from]}) {
		my $l = length(sprintf("%i", $a));
		$hstr .= sprintf(" %-${l}s", '@');
		$rstr .= sprintf(" %-${l}s", " ");
		$estr .= sprintf(" %-${l}s", " ");
		$sstr .= sprintf(" %-${l}i", $a);
	    }
	    foreach my $a (@{$align_info[$i][$ind_start]}) {
		my $l = length(sprintf("%i", $a));
		$hstr .= sprintf(" %-${l}s", "[");
		$rstr .= sprintf(" %-${l}s", " ");
		$estr .= sprintf(" %-${l}s", " ");
		$sstr .= sprintf(" %-${l}i", $a);
	    }
	    
	    $shift_in_str = join(",", @{$align_info[$i][$ind_in]}) if ($NUMBER_SHIFT_WORDS);
	}
	if ($p[$mp] eq " ") {
	    my $l = max(length($orig_hyp[$i]), length($orig_ref[$j]), length($shift_in_str));
	    $hstr .= " " . sprintf("%-${l}s", $orig_hyp[$i]);
	    $rstr .= " " . sprintf("%-${l}s", $orig_ref[$j]);
	    $estr .= " " . sprintf("%-${l}s", " ");
	    $sstr .= " " . sprintf("%-${l}s", $shift_in_str);
	    $i++; $j++;
	} elsif (($p[$mp] eq "S") || ($p[$mp] eq "T")) {
	    my $l = max (length($orig_hyp[$i]), length($orig_ref[$j]), 
			 $p[$mp], length($shift_in_str));
	    $hstr .= " " . sprintf("%-${l}s", mark_error($orig_hyp[$i]));
	    $rstr .= " " . sprintf("%-${l}s", mark_error($orig_ref[$j]));
	    $estr .= " " . sprintf("%-${l}s", $p[$mp]); 
	    $sstr .= " " . sprintf("%-${l}s", $shift_in_str);
	    $i++; $j++;
	} elsif ($p[$mp] eq "D") {
	    my $l = length($orig_ref[$j]);
	    $hstr .= " " . ("*" x $l);
	    $rstr .= " " . mark_error($orig_ref[$j]);
	    $estr .= " " . sprintf("%-${l}s", "D");
	    $sstr .= " " . sprintf("%-${l}s", " ");
	    $j++;	    
	} else {
	    my $l = max(length($shift_in_str), length($orig_hyp[$i]));
	    $rstr .= " " . ("*" x $l);
	    $hstr .= " " . mark_error($orig_hyp[$i]);
	    $estr .= " " . sprintf("%-${l}s", "I");
	    $sstr .= " " . sprintf("%-${l}s", $shift_in_str);
	    $i++;	    
	}
	if ($p[$mp] ne "D") {
	    foreach my $a (rev(@{$align_info[$i-1][$ind_end]})) {
		my $l = length(sprintf("%i", $a));
		$hstr .= sprintf(" %-${l}s", "]");
		$rstr .= sprintf(" %-${l}s", " ");
		$estr .= sprintf(" %-${l}s", " ");
		$sstr .= sprintf(" %-${l}i", $a);
	    }
	}
    }

    return ($hstr, $rstr, $estr, $sstr);    
}

sub rev {
    my @a = @_;
    return reverse(@a);
}

sub score_sent {
    # try all references, and find the one with the lowest score
    # return the score, path, shifts, etc
    my ($prefixed_id, $id) = @_;
    my @tmparr = ();
    my $best_score = -1;
    my $best_ref = "";
    my $best_path = "";
    my $best_hyp = $HYPS{$prefixed_id}[0];
    my $best_allshift = \@tmparr;

    foreach my $ref (@{$REFS{$id}}) {
	print "Trying Ref ($ref)\n" if ($DIAGNOSTICS);
	my ($s, $p, $newhyp, $allshifts) = 
	    calc_shifts($HYPS{$prefixed_id}[0], $ref);
	if (($best_score < 0) || ($s < $best_score)) {
	    $best_score = $s;
	    $best_path = $p;
	    $best_ref = $ref;
	    $best_hyp = $newhyp;
	    $best_allshift = $allshifts;
	}
    }
    return ($best_score, $best_path, $best_ref, $best_hyp, 
	    $best_allshift);
}

sub build_word_matches {
    my ($harr, $rarr) = @_;
    # take in two arrays of words
    # build a hash mapping each valid subseq of the ref to its location
    # this is a utility func for calculating shifts
    my %rloc;

    # do a quick pass to check to see which words occur in both strings
    my %hwhash;
    my %cor_hash;
    foreach my $w (@{$harr}) {$hwhash{$w}++;}
    foreach my $w (@{$rarr}) {
	$cor_hash{$w} += $hwhash{$w} if (exists $hwhash{$w});
    }

    # build a hash of all the reference sequences
    for (my $start = 0; $start <= $#{$rarr}; $start++) {
	next unless (exists $cor_hash{$rarr->[$start]});
	for (my $end = $start; ((! $no_match) &&
				($end <= $#{$rarr}) && 
				($end <= $start + $MAX_SHIFT_SIZE)); 
	     $end++) {
	    last unless (exists $cor_hash{$rarr->[$end]});
	    # add sequence $start...$end to hash
	    my $topush = join(" ", @{$rarr}[$start..$end]);
	    push @{$rloc{$topush}}, $start;
	}
    }
    return \%rloc;
}

sub calc_shifts {
    # calculate all of the shifts
    my ($hyp, $ref) = @_;
    my @hwords = split(/\s+/, $hyp);
    my @rwords = split(/\s+/, $ref);
    my $rloc = build_word_matches(\@hwords, \@rwords);

    my ($med_score, $med_path) = min_edit_dist($hyp, $ref, 1);

    my $edits = 0;
    my $cur = $hyp;
    my @all_shifts;

    while (1) {
	my ($new_hyp, $new_score, $new_path, $sstart, $send, $sdest) = 
	    calc_best_shift($cur, $ref, \@hwords, 
			    \@rwords, $rloc, $med_score, $med_path);
	last if ($new_hyp eq "");

	my @tmp_shift = ($sstart, $send, $sdest, 
			 $cur, $new_hyp, $new_score, $new_path);
	push @all_shifts, \@tmp_shift;

	$edits += $SHIFT_COST;
	$med_score = $new_score;
	$med_path = $new_path;
	$cur = $new_hyp;
	@hwords = split(/\s+/, $cur);	
    }
    return ($med_score + $edits, $med_path, $cur, \@all_shifts);
}

sub gather_all_poss_shifts {
    # find all possible shifts to search through
    my ($hwords, $rloc, $ralign, $herr, $rerr, $min_size) = @_;
    my @poss;
    # return an array (@poss), indexed by len of shift
    # each entry is ($start, $end, $moveto);
    for (my $start = 0; $start <= $#{$hwords}; $start++) {
	next unless (exists $rloc->{$hwords->[$start]});
	my $ok = 0;

	foreach my $moveto (@{$rloc->{$hwords->[$start]}}) {
	    $ok = 1 if (($start != $ralign->[$moveto]) &&
			($ralign->[$moveto] - $start <= $MAX_SHIFT_DIST) &&
			(($start - $ralign->[$moveto])-1 <= $MAX_SHIFT_DIST));
	    last if ($ok);
	}
	next unless ($ok);
	
	my $ok = 1;
	for (my $end = $start + ($min_size - 1); ($ok && ($end <= $#{$hwords}) && 
				($end < $start + $MAX_SHIFT_SIZE)); 
	     $end++) {
	    my $cand = join(" ", @{$hwords}[$start..$end]);
	    $ok = 0;	    
	    last unless (exists $rloc->{$cand});
	    
	    my $any_herr = 0;
	    for ($i = 0; ($i <= $end - $start) && (! $any_err); $i++) {
		$any_herr = 1 if ($herr->[$start+$i]);
	    }
	    if ($any_herr == 0) {
		$ok = 1;
		next;
	    }
	    
	    # consider moving $start..$end
	    foreach my $moveto (@{$rloc->{$cand}}) {		
		next unless (($ralign->[$moveto] != $start) &&
			     (($ralign->[$moveto] < $start) || 
			      ($ralign->[$moveto] > $end)) &&
			     ($ralign->[$moveto] - $start <= $MAX_SHIFT_DIST) &&
			     (($start - $ralign->[$moveto])-1 <= $MAX_SHIFT_DIST));
		$ok = 1;

		# check to see if there are any errors in either string 
		# (only move if this is the case!)
		my $any_rerr = 0;
		for ($i = 0; ($i <= $end - $start) && (! $any_rerr); $i++) {
		    $any_rerr = 1 if ($rerr->[$moveto+$i]);
		}
		next unless ($any_rerr);
	       
		for (my $roff = 0; $roff <= ($end - $start); $roff++) {
		    if (($start != $ralign->[$moveto+$roff]) && 
			(($roff == 0) || 
			 ($ralign->[$moveto + $roff] != $ralign->[$moveto]))) {
			my @tmp = ($start, $end, $moveto + $roff);
			push(@{$poss[$end-$start]}, \@tmp);
		    }
		}
	    }
	}
    }
    return \@poss;
}

sub calc_best_shift {
    # one greedy step in finding the shift
    # find the best one at this point and return it
    my ($hyp, $ref, $hwords, $rwords, $rloc, $curerr, $med_path) = @_;

    my $cur_best_score = $curerr;
    my $cur_best_shift_cost = 0;
    my $cur_best_path = "";
    my $cur_best_hyp = "";
    my ($cur_best_start, $cur_best_end, $cur_best_dest) = (0,0,0); 

    my @path_vals = split(/,/, $med_path);
    my @ralign;

    # boolean. true if $words->[$i] is an error
    my @herr;
    my @rerr;

    my $hpos = -1;
    foreach my $sym (@path_vals) {
	if ($sym eq " ") {
	    $hpos++;
	    push @herr, 0; 
	    push @rerr, 0;
	    push @ralign, $hpos;
	} elsif ($sym eq "S") {
	    $hpos++;
	    push @herr, 1;
	    push @rerr, 1;
	    push @ralign, $hpos;
	} elsif ($sym eq "I") {
	    $hpos++;
	    push @herr, 1; 
	} elsif ($sym eq "D") {
	    push @rerr, 1; 
	    push @ralign, $hpos;
	} else {
	    print "Error!  Invalid mini align sequence $sym in ($med_path)\n";
	    exit(-1);
	}
    }

    if ($DIAGNOSTICS) {
	print "  ". join("\n  ", gen_str_out($hyp, $ref, $med_path)) . "\n";
    }

    # Have we found any good shift yet?
    my $anygain = 0;

    my $poss_shifts = gather_all_poss_shifts($hwords, $rloc, \@ralign, \@herr, \@rerr, 1);    

    for (my $i = $#{$poss_shifts}; $i >= 0; $i--) {
	my ($curfix, $maxfix) = (($curerr - ($cur_best_shift_cost + $cur_best_score)),
				 ((2 * (1 + $i)) - $SHIFT_COST));
	last if (($curfix > $maxfix) || 
		 ((! $COSMETIC_SH) && ($curfix >= $maxfix)) || 		 
		 (($cur_best_shift_cost != 0) && ($curfix == $maxfix)));
	my ($work_start, $word_end) = (-1, -1);

	foreach my $s (@{$poss_shifts->[$i]}) {
	    my ($curfix, $maxfix) = (($curerr - ($cur_best_shift_cost + $cur_best_score)),
				     ((2 * (1 + $i)) - $SHIFT_COST));
	    last if (($curfix > $maxfix) || 
		     ((! $COSMETIC_SH) && ($curfix >= $maxfix)) ||
		     (($cur_best_shift_cost != 0) && ($curfix == $maxfix)));
	    
	    my ($start, $end, $moveto) = @{$s};
	    if ($work_start == -1) {
		($work_start, $work_end) = ($start, $end);
	    } elsif (($work_start != $start) && ($work_end != $end)) {
		if ($anygain) {
		    if ($FAST_LEVEL >= 4) {
			return ($cur_best_hyp, $cur_best_score, $cur_best_path, 
				$cur_best_start, $cur_best_end, $cur_best_dest);
		    }
		} else {
		    ($work_start, $work_end) = ($start, $end);
		}
	    }



	    if ($DIAGNOSTICS) {
		my $cand = join(" ", @{$hwords}[$start..$end]);
		print " considering shifting ($cand) from $start to $ralign[$moveto]\n";
		printf ("   curFix: %i maxFix: %i\n",  $curfix, $maxfix);
	    }

	    my $shifted_str = perform_shift($hwords, $start, $end, $ralign[$moveto]);
	    my ($try_score, $try_path) = 
		min_edit_dist($shifted_str, $ref, 1);
	    print "   result = $shifted_str ($try_score+$SHIFT_COST vs $cur_best_score+$cur_best_shift_cost)\n" if ($DIAGNOSTICS);
	    
	    my $gain = ($cur_best_score + $cur_best_shift_cost) - ($try_score + $SHIFT_COST);
	    if (($gain > 0) || ($COSMETIC_SH && ($cur_best_shift_cost == 0) && ($gain == 0))) {
		print "   Possible Best, Gain: $gain\n" if ($DIAGNOSTICS);
		$anygain = 1;
		$cur_best_score = $try_score;
		$cur_best_shift_cost = $SHIFT_COST;
		$cur_best_path = $try_path;
		$cur_best_hyp = $shifted_str;
		$cur_best_start = $start;
		$cur_best_end = $end;
		$cur_best_dest = $ralign[$moveto];
	    }
	}
    }
    print " Choosing shift of ($cur_best_start..$cur_best_end) to $cur_best_dest.\n" if ($DIAGNOSTICS);
    return ($cur_best_hyp, $cur_best_score, $cur_best_path, 
	    $cur_best_start, $cur_best_end, $cur_best_dest);
}

sub perform_shift_inarr {
    # perform a shift on an array of words
    my ($hwords, $start, $end, $moveto) = @_;
    my @new;
    if ($moveto == -1) {
	push(@new, @{$hwords}[$start..$end], 
	     @{$hwords}[0..$start-1],
	     @{$hwords}[$end+1..$#{$hwords}]);
    } elsif ($moveto < $start) {
	push(@new, @{$hwords}[0..$moveto], 
	     @{$hwords}[$start..$end],
	     @{$hwords}[$moveto+1..$start-1], 
	     @{$hwords}[$end+1..$#{$hwords}]);
    } elsif ($moveto > $end) {
	push(@new, @{$hwords}[0..$start-1], 
	     @{$hwords}[$end+1..$moveto],
	     @{$hwords}[$start..$end], 
	     @{$hwords}[$moveto+1..$#{$hwords}]);
    } else {
	# we are moving inside of ourselves
	push (@new, @{$hwords}[0..$start-1],
	      @{$hwords}[$end+1..($end + ($moveto - $start))],
	      @{$hwords}[$start..$end],
	      @{$hwords}[($end+($moveto-$start)+1)..$#{$hwords}]);
    }
    @{$hwords} = @new;
    return;
}

sub perform_shift {
    # perform a shift on a string of words
    my ($hwords, $start, $end, $moveto) = @_;
    my $hyp;
    if ($moveto == -1) {
	$hyp = join(" ", @{$hwords}[$start..$end], 
		    @{$hwords}[0..$start-1],
		    @{$hwords}[$end+1..$#{$hwords}]);
    } elsif ($moveto < $start) {
	$hyp = join(" ", @{$hwords}[0..$moveto], 
		    @{$hwords}[$start..$end],
		    @{$hwords}[$moveto+1..$start-1], 
		    @{$hwords}[$end+1..$#{$hwords}]);
    } elsif ($moveto > $end) {
	$hyp = join(" ", @{$hwords}[0..$start-1], 
		    @{$hwords}[$end+1..$moveto],
		    @{$hwords}[$start..$end], 
		    @{$hwords}[$moveto+1..$#{$hwords}]);
    } else {
	# we are moving inside of ourselves
	$hyp = join(" ", @{$hwords}[0..$start-1],
		    @{$hwords}[$end+1..($end + ($moveto - $start))],
		    @{$hwords}[$start..$end],
		    @{$hwords}[($end+($moveto-$start)+1)..$#{$hwords}]);
    }
    return $hyp;
}

sub min_edit_dist_arr {
    # calculate the min-edit-dist if the words have been split already
    my ($hw, $rw, $full) = @_;

    if ($FAST_LEVEL >= 2) {
	return beam_min_edit($hw, $rw);
    }

    my @mat;
    my @pat;

    _min_edit_dist($#{$hw}, $#{$rw}, $hw, $rw, \@mat, \@pat, $full);

    my $score = $mat[$#{$hw}][$#{$rw}];
    my $path = backtrace_path(\@pat, $#{$hw}, $#{$rw});
    return ($score, $path);
}



sub min_edit_dist {
    # calculate the min-edit distance between the hyp and the ref
    my ($hyp, $ref, $full) = @_;
    my @hw = split(/\s+/, $hyp);
    my @rw = split(/\s+/, $ref);

    return min_edit_dist_arr(\@hw, \@rw, $full);
}


sub beam_min_edit {
    my ($hw, $rw) = @_;
    my @S;
    my @P;
    beam_search($hw, $rw, \@S, \@P);

    my $score = $S[$#{$rw}+1][$#{$hw}+1];
    my $path = backtrace_beam(\@P, $#{$rw}+1, $#{$hw}+1);

    return ($score, $path);
}

sub beam_search {
    my ($hw, $rw, $S, $P) = @_;
    my ($current_best, $last_best) = ($INF, $INF);
    my ($first_good, $current_first_good, $last_good) = (0, 0, -1);
    my $cur_last_good = 0;
    my ($last_peak, $cur_last_peak) = (0, 0);

    $S->[0][0] = 0;

    foreach my $j (0..$#{$hw}+1) {
	$last_best = $current_best;
	$current_best = $INF;

	$first_good = $current_first_good;
	$current_first_good = -1;

	$last_good = $cur_last_good;
	$cur_last_good = -1;
	
	$last_peak = $cur_last_peak;
	$cur_last_peak = 0;

	foreach my $i ($first_good..$#{$rw}+1) {
	    break if ($i > $last_good);
	    next if (! defined($S->[$i][$j]));
	    my $score = $S->[$i][$j];
	    next if (($j < $#{$hw}+1) && ($score > $last_best+$BEAMWIDTH));

	    $current_first_good = $i if ($current_first_good == -1);
	    
	    if (($i <= $#{$rw}) && ($j <= $#{$hw})) {
		if ($rw->[$i] == $hw->[$j]) {
		    my $cost = $MATCH_COST + $score;
		    if ((! defined($S->[$i+1][$j+1])) || 
			($cost < $S->[$i+1][$j+1])) {
			$S->[$i+1][$j+1] = $cost;
			$P->[$i+1][$j+1] = " ";
		    }
		    if ($cost < $current_best) {
			$current_best = $cost;
		    }		
		    $cur_last_peak = $i+1 if ($current_best == $cost);
		} else {
		    my $cost = $SUB_COST + $score;
		    if ((! defined($S->[$i+1][$j+1])) || 
			($cost < $S->[$i+1][$j+1])) {
			$S->[$i+1][$j+1] = $cost;
			$P->[$i+1][$j+1] = "S";
			if ($cost < $current_best) {
			    $current_best = $cost;
			}
			$cur_last_peak = $i+1 if ($current_best == $cost);
		    }
		}
	    }

	    $cur_last_good = $i+1;

	    my $icost = $score+$INSERT_COST;
	    if ((! defined($S->[$i][$j+1])) || ($S->[$i][$j+1] > $icost)) {
		$S->[$i][$j+1] = $icost;
		$P->[$i][$j+1] = "I";
		$cur_last_peak = $i if (($cur_last_peak < $i) && 
					($current_best == $icost));
	    }

	    my $dcost = $score+$DELETE_COST;
	    if ((! defined($S->[$i+1][$j])) || ($S->[$i+1][$j] > $dcost)) {
		$S->[$i+1][$j] = $dcost;
		$P->[$i+1][$j] = "D";
		$last_good = $i+1 if ($i == $last_good);
	    }
	}
    }
    return $S->[$#{$rw}+1][$#{$hw}+1];
}

sub _min_edit_dist {
    # recursively calculate the min edit path
    my ($i, $j, $hw, $rw, $mat, $pat, $full) = @_;

    return 0 if (($i == -1) && ($j == -1));
    return $INF if (($i < -1) || ($j < -1));
    return (($j + 1) * $DELETE_COST) if ($i == -1);
    return (($i + 1) * $INSERT_COST) if ($j == -1);
    return $mat->[$i][$j] if (defined $mat->[$i][$j]);

    my ($dia_cost) = 
	(_min_edit_dist($i-1, $j-1,$hw,$rw,$mat,$pat,$full));
    my $mcost = $INF;
    my $scost = $INF;
    if ($hw->[$i] == $rw->[$j]) {
	$mcost = $MATCH_COST + $dia_cost;
	if ($FAST_LEVEL >= 1) {
	    $mat->[$i][$j] = $mcost;
	    $pat->[$i][$j] = " ";
	    $did_calc->[$i][$j] = 1;	    
	    return $mat->[$i][$j];
	}
    } else {
	$scost = get_subcost($hw->[$i], $rw->[$j]) + $dia_cost;
    }

    my ($ip_cost, $dp_cost) = 
	(_min_edit_dist($i-1, $j,$hw,$rw,$mat,$pat,$full),
	 _min_edit_dist($i, $j-1,$hw,$rw,$mat,$pat,$full));

    my $icost = get_inscost($hw->[$i]) + $ip_cost;
    my $dcost = get_delcost($rw->[$i]) + $dp_cost;

    if (%SUB_MATRIX) {
	if (exists $POSSIBLE_SUB{$hw->[$i]}) {
	    my $maxslength = $POSSIBLE_SUB{$hw->[$i]};
	}
    }

    if (($mcost <= $icost) && ($mcost <= $dcost) && ($mcost <= $scost)) {
	# Match is best
	$mat->[$i][$j] = $mcost;
	$pat->[$i][$j] = " ";	    
    } elsif ($full && ($scost <= $icost) && ($scost <= $dcost)) {
	# Sub is best
	$mat->[$i][$j] = $scost;
	$pat->[$i][$j] = "S";
    } elsif ($icost <= $dcost) {
	# Insert is best
	$mat->[$i][$j] = $icost;
	$pat->[$i][$j] = "I";		    
    } else {
	# Deletion is best
	$mat->[$i][$j] = $dcost;
	$pat->[$i][$j] = "D";		    
    }
    $did_calc->[$i][$j] = 1;

    return $mat->[$i][$j];
}

sub backtrace_path {
    # backtrace the min-edit-path
    my ($pat, $i, $j) = @_;
    my @path;
    while (($i >= 0) || ($j >= 0)) {
	if ($i < 0) {
	    push @path, "D";
	    $j--;	    
	} elsif ($j < 0) {
	    push @path, "I";
	    $i--;
	} else {
	    push @path, $pat->[$i][$j];
	    if (($pat->[$i][$j] eq " ") || ($pat->[$i][$j] eq "S")) {
		$i--; $j--;
	    } elsif ($pat->[$i][$j] eq "I") {
		$i--;
	    } else {
		$j--;
	    }	    
	}
    }
    return join(",", reverse(@path));
}

sub backtrace_beam {
    # backtrace the min-edit-path
    my ($pat, $i, $j) = @_;
    my @path;
    while (($i != 0) || ($j != 0)) {
	if ($i < 0) {
	    push @path, "I";
	    $j--;	    
	} elsif ($j < 0) {
	    push @path, "D";
	    $i--;
	} else {
	    push @path, $pat->[$i][$j];
	    if (($pat->[$i][$j] eq " ") || ($pat->[$i][$j] eq "S")) {
		$i--; $j--;
	    } elsif ($pat->[$i][$j] eq "D") {
		$i--;
	    } elsif ($pat->[$i][$j] eq "I") {
		$j--;
	    } else {
		print "Error.  Invalid traceback.\n";
		exit(-1);
	    }	    
	}
    }
    return join(",", reverse(@path));
}

sub get_subcost {
    # return the substitution cost for word1 and word2
    my ($s1, $s2) = @_;
    if ((exists $SUB_MATRIX{$s1}) && (exists($SUB_MATRIX{$s1}{$s2}))) {
	return $SUB_MATRIX{$s1}{$s2};
    } else {
	return $SUB_COST;
    }
}

sub get_delcost {
    # return the deletion cost for word
    my ($s1) = @_;
    if (exists $DEL_MATRIX{$s1}) {
	return $DEL_MATRIX{$s1};
    } else {
	return $DELETE_COST;
    }
}

sub get_inscost {
    # return the deletion cost for word
    my ($s1) = @_;
    if (exists $INS_MATRIX{$s1}) {ou want
	return $INS_MATRIX{$s1};
    } else {
	return $INSERT_COST;
    }
}



sub add_word {
    my ($word) = @_;
    return $DICT{$word} if (exists $DICT{$word});
    push @REVDICT, $word;
    $DICT{$word} = $#REVDICT;
    return $DICT{$word};
}

sub rev_dictstr {
    my ($st) = @_;
    my @words = split(/\s+/, $st);
    return join(" ", map({$REVDICT[$_]} @words));
}

sub load_data {
    # load the sentences from the data file into the hash reference
    # works for hyp and ref files
    # set $count_only to 1 if you just want to record the lengths
    my ($fname, $href, $hyps, $count_only) = @_;
    if ($INPUTTYPE == 0) {
	load_text_data($fname, $href, $hyps, $count_only);
    } elsif ($INPUTTYPE == 1) {
	load_sgm_data($fname, $href, $hyps, $count_only);
    } else {
	print "INVALID INPUT TYPE.\n";
	exit(-1);
    }
}

sub load_sgm_data {
    my ($fname, $href, $hyps, $count_only) = @_;
    open (FH, $fname) or 
	(print ("Cannot open $fname for reading\n") and exit(-1));
    my ($docid, $segid) = ("", "");
    while (<FH>) {
	chomp;
	my $to_process = $_;
	while ($to_process) {
	    my ($found, $name, $content, $rest) = get_next_tag($to_process);
	    break if (! $found);
	    $name = lc($name);
	    $to_process = $rest;
	    if ($name eq "") {
		# content between tags

		if ($docid && $segid) {
		    &process_sent($content, "$docid-$segid", $href, $hyps, $count_only);
		}
	    } elsif ($name eq "seg") {
		if (exists $content->{id}) {
		    $segid = $content->{id};
		}
	    } elsif ($name eq "doc") {
		if (exists $content->{docid}) {
		    $docid = $content->{docid};
		}		
	    }
	}
    }
    close(FH);
}

sub load_text_data {
    my ($fname, $href, $hyps, $count_only) = @_;
    open (FH, $fname) or 
	(print ("Cannot open $fname for reading\n") and exit(-1));
    my $sent_counter = 0;
    while (<FH>) {
	chomp;
	s/^\#.*$//g;
	if (m/^\s*(.*)\s*\((\S+)\)\s*$/) {
	    my ($sent, $id) = ($1, $2);
	    &process_sent($sent, $id, $href, $hyps, $count_only);
	} elsif ($_ !~ m/^\s*$/) {
	    print "Invalid line: $_\n";
	    exit(-1);
	}
    }
    close(FH);
}

sub process_sent {
    my ($sent, $orig_id, $href, $hyps, $count_only) = @_;
    # We prepend a counter to each hypothesis sent id in order to preserve the
    # order in which we load data in the hash table (after sorting the keys).
    # This is important when we process N-best lists.
    my $id = ($hyps) ? sprintf("%07d_%s", ++$sent_counter, $orig_id) : $orig_id;
    $sent = lc($sent) if (! $CASE_ON);
    $sent = &NormalizeText($sent) if ($MTEVAL_NORMALIZE);
    $sent = remove_punctuation($sent) if ($REMOVE_PUNC);
    $sent = separate_punctuation($sent) unless ($LEAVE_PUNC);
    my @words = split(/\s+/, $sent);
    if ($count_only) {
	push(@{$href->{$id}}, $#words + 1);
    } else {
	push(@{$href->{$id}},
	     join(" ",
		  map {add_word($_)} @words));
    }
}

sub remove_punctuation {
    # remove all punc from string
    my ($sent) = @_;

    $sent =~ s/[,\":;.!?\(\)]//g;

    return $sent;
}

sub separate_punctuation {
    # tokenize punctuation
    my ($sent) = @_;

    $sent =~ s/(\S)([,\":;.!?\(\)])/$1 $2/g;
    $sent =~ s/([,\":;.!?\(\)])(\S)/$1 $2/g;

    return $sent;
}

sub average_rlen {
    # calculate the average number of words in a set of strings
    my @refs = @_;
    my $total = 0.0;
    foreach my $r (@refs) {
	$total += scalar(split(/\s+/,$r));
    }
    return $total / ($#refs + 1);
}

sub load_cost_file {
    # Load Cost Matrix from file
    # file format is <TYPE>\t<COST>\t<SEQ1>\t<SEQ2>
    # type must be 's' 'd' or 'i'
    my ($fname, $smatrix, $possub, $imatrix, $possi, $dmatrix, $possd) = @_;
    open (FH, $fname) or (print "Cannot open $fname for reading.\n" and 
			  exit(-1));
    while (<FH>) {
	chomp;
	next if (m/^\s*$/);
	next if (m/^\s*\#/);
	s/\s+$//g;
	s/  +/ /g;
	my @vals =  split(/\t+/); 
	if (($#vals != 2) && ($#vals != 3)) {
	    print "Invalid line in cost matrix: $_\n";
	    exit(-1);
	}
	my ($type, $cost, $seq1, $seq2) = @vals;
	
	
	my @seq1words = map({add_word($_)} split(/\s+/, $seq1));	
	my $seq1size = scalar(@seq1words);
	my $lastword = $seq1words[$#seq1words];
	
	if ($type eq "s") {
	    $smatrix->{$seq1}{$seq2} = $cost;	
	    $possub->{$lastword} = max($possub->{$firstword}, $seq1size);

	    # Change this if we don't want symetric costs
	    my @seq2words = map({add_word($_)} split(/\s+/, $seq2));
	    my $seq2size = scalar(@seq2words);
	    my $lastword = $seq2words[$#seq2words];
	    $smatrix->{$seq2}{$seq1} = $cost;	
	    $possub->{$lastword} = max($possub->{$firstword}, $seq2size);
	} elsif ($type eq "i") {
	    $imatrix->{$seq1} = $cost;
	    $posi->{$lastword} = max($posi->{$firstword}, $seq1size);
	} elsif ($type eq "d") {
	    $dmatrix->{$seq1} = $cost;
	    $posd->{$lastword} = max($posd->{$firstword}, $seq1size);
	} else {
	    print "Invalid cost matrix line: $_\n";
	    exit(-1);
	}
    }
    close(FH);
    return;
}

sub get_next_tag {
    my ($str) = @_;
    if ($str =~ m|^\s*\<([^> ]*)\s*([^>]*)\>(.*)$|i) {
	my ($name, $inner, $rest) = ($1, $2, $3);
	my %hsh;
	while ($inner ne "") {
	    if ($inner =~ m|^\s*(\S+)=\"([^\"]*)\"\s*(.*)$|i) {
		$hsh{lc($1)} = $2;
		$inner = $3;
	    } elsif ($inner =~ m|^\s*(\S+)=([^ ]*)\s*(.*)$|i) {
		$hsh{lc($1)} = $2;
		$inner = $3;
	    } else {
		$inner = "";
	    }
	}
	return (1, $name, \%hsh, $rest);
    } elsif ($str =~ m|^\s*([^<]*?)\s*(\<\S.*)$|) {
	return (1, "", $1, $2);
    } elsif ($str =~ m|^\s*(.*?)\s*$|) {
	return (1, "", $1, "");
    } else {
	return (0, "", "", "");
    }
}

sub NormalizeText {
    my ($norm_text) = @_;

# language-independent part:
    $norm_text =~ s/<skipped>//g; # strip "skipped" tags
    $norm_text =~ s/-\n//g; # strip end-of-line hyphenation and join lines
    $norm_text =~ s/\n/ /g; # join lines
    $norm_text =~ s/&quot;/"/g;  # convert SGML tag for quote to "
    $norm_text =~ s/&amp;/&/g;   # convert SGML tag for ampersand to &
    $norm_text =~ s/&lt;/</g;    # convert SGML tag for less-than to >
    $norm_text =~ s/&gt;/>/g;    # convert SGML tag for greater-than to <

# language-dependent part (assuming Western languages):
    $norm_text = " $norm_text ";
    $norm_text =~ s/([\{-\~\[-\` -\&\(-\+\:-\@\/])/ $1 /g;   # tokenize punctuation
    $norm_text =~ s/([^0-9])([\.,])/$1 $2 /g; # tokenize period and comma unless preceded by a digit
    $norm_text =~ s/([\.,])([^0-9])/ $1 $2/g; # tokenize period and comma unless followed by a digit
    $norm_text =~ s/([0-9])(-)/$1 $2 /g; # tokenize dash when preceded by a digit
    $norm_text =~ s/\s+/ /g; # one space only between words
    $norm_text =~ s/^\s+//;  # no leading space
    $norm_text =~ s/\s+$//;  # no trailing space

    return $norm_text;
}
