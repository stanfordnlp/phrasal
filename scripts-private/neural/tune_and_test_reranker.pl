use strict;
use warnings;

die "Usage: $0 pars mode" if scalar(@ARGV)!=2;

my $parsFile = shift @ARGV;
my $mode = shift @ARGV;

# modes
my $RUN = "run";
my $PRINT = "print";

&ensureIsMember($mode, [$PRINT, $RUN]);

my $pars = &readParams($parsFile);

my $src = &getParam($pars, "source_file");
my $nbest = &getParam($pars, "nbest_input_file");
my $tuneInfo = &getParam($pars, "test_set_info_for_tuning");
my $packageInfo = &getParam($pars, "test_set_info_for_package");
my $test_set_root_dir = &getParam($pars, "test_set_root_dir");
my $tune_components = &getParam($pars, "reranker_tune_components");
my $evalMetric = &getParam($pars, "eval_metric");
my $doAddLength = &getParam($pars, "add_length");
my $mertOpt = &getParam($pars, "mert_opt");
my $models = &getParam($pars, "model_prefixes");
my $testFlags = &getParam($pars, "test_flags");
my $rerankFeatures = &getParam($pars, "rerank_features");
my $gpuDevice = &getParam($pars, "gpu_device");
my $outputDir = &getParam($pars, "output_dir");
my $packageForIBM = &getParam($pars, "package_for_ibm");
my $source_lang = &getParam($pars, "source_lang");
my $system_name = &getParam($pars, "system_name");
my $date = &getParam($pars, "date");


my $SCRIPTS = $ENV{"JAVANLP_HOME"}."/projects/mt/scripts-private";
my $neural = "$SCRIPTS/neural";
my $bolt = "$SCRIPTS/bolt";

my $add_length_to_nbest = "$neural/add_length_to_nbest.pl";
my $add_neural_scores = "$neural/add_neural_scores.sh";
my $extract_components = "$neural/extract_nbest.py";
my $tune_reranker = "$neural/tune_reranker.sh";
my $rerank = "$SCRIPTS/rerank.pl";
my $extract_1best = "$neural/extract_1best.pl";
my $score = "$bolt/score.pl";
my $make = "$bolt/make_package_for_ibm.pl";
my $make_composite_ref = "$bolt/make_composite_ref.pl";
my $renumber_nbest = "$bolt/renumber_nbest_from_zero.pl";

my $intermediateNBest = "$outputDir/nbest.with_len";
my $completeNBest = "$outputDir/nbest.with_neural";
my $rerankedNBest = "$outputDir/nbest.reranked";

my $ckptDir = "$outputDir/checkpoints";
&execute($mode, "$ckptDir/mkdir", "mkdir -p $outputDir $ckptDir");

if($doAddLength) {
    &execute($mode, "$ckptDir/add_length", "$add_length_to_nbest < $nbest > $intermediateNBest");
}
else {
    $intermediateNBest = $nbest;
}


&manualExecute($mode, "jagupard*", "$add_neural_scores $src $intermediateNBest $completeNBest $models \"$testFlags\" $gpuDevice", "$ckptDir/add_neural_scores");
&execute($mode, "$ckptDir/extract_components", "$extract_components  $completeNBest $tuneInfo $outputDir/components");

my @components = split /,/, $tune_components;
my @comp_nbests = map {"$outputDir/components/$_.nbest"} @components;
my $comp_nbests_str = join(" ", @comp_nbests);
&execute($mode, "$ckptDir/concat_nbests", "cat $comp_nbests_str | $renumber_nbest > $outputDir/tune_components.nbest");
&execute($mode, "$ckptDir/concat_refs", "$make_composite_ref $test_set_root_dir $tune_components $outputDir/tune_components.ref");

$mertOpt = &trim($mertOpt);
my $mertOptArg = (lc $mertOpt eq "none" ? "" : " \"$mertOpt\"");
&execute($mode, "$ckptDir/tune_reranker", "$tune_reranker $outputDir/tuned_weights $outputDir/tune_components.nbest $outputDir/tune_components.ref $rerankFeatures $evalMetric$mertOptArg");
&execute($mode, "$ckptDir/rerank", "$rerank $outputDir/tuned_weights/train.wts < $completeNBest > $rerankedNBest");
&execute($mode, "$ckptDir/extract_1best", "$extract_1best < $rerankedNBest > $outputDir/reranked.trans");
&execute($mode, "$ckptDir/score_1best", "$score $outputDir/reranked.trans $tuneInfo > $outputDir/results.reranked 2> $outputDir/score.stderr");

if($packageForIBM) {
    &execute($mode, "$ckptDir/make_ibm_output", "$make $rerankedNBest $packageInfo $source_lang Stanford $system_name $date $outputDir/reranked.package");
}


sub execute {
    my ($mode, $ckptFile, $cmd) = @_;
    if(-e $ckptFile) {
	print STDERR "Skipping stage because checkpoint $ckptFile found.\n";
    }
    else {
	print STDERR "Command: $cmd\n";
	if($mode eq $RUN) {
	    if(system($cmd)==0) {	
		system("touch $ckptFile")==0 or die "Could not update checkpoint: $ckptFile";
	    }
	    else {
		die "FAILED.\n";
	    }
	}
    }
}

sub manualExecute {
    my ($mode, $comp_name, $command, $checkpoint) = @_;
    if($mode eq $RUN) {
	print STDERR "Please run the following command on a '$comp_name' computer.\n\n";
	print STDERR "$command && touch $checkpoint\n\n";
	&waitUntilExists($checkpoint);
    }
    elsif($mode eq $PRINT) {
	print STDERR "Command to be run on '$comp_name' computer:\n\n";
	print STDERR "$command && touch $checkpoint\n\n";
    }
}

sub waitUntilExists {
    my ($file) = @_;
    print STDERR "Waiting for file '$file' to exist...";
    while(! -e $file) {
	sleep(10);
    }
    print STDERR " done.\n";
}

sub readParams {
    my ($fn) = @_;
    my %pars = ();
    open IN, $fn or die $!;
    while(<IN>) {
	next if m/^\s*$/;
	next if m/^\s*\#/;
	$_ = &trim($_);
	my @tokens = split /:/, $_, 2;
	$tokens[0] = &trim($tokens[0]);
	$tokens[1] = &trim($tokens[1]);
	die "Bad line in param file: $_" if scalar(@tokens)!=2;
	die "Redundant parameter at line: $_" if exists($pars{$tokens[0]});
	
	$pars{$tokens[0]} = $tokens[1];
    }
    close IN;
    return \%pars;
}

sub trim {
    my ($str) = @_;
    $str =~ s/^\s+//;
    $str =~ s/\s+$//;
    return $str;
}

sub getParam {
    my ($pars, $name) = @_;
    die "Missing required param: $name" if !exists($pars->{$name});
    return $pars->{$name};
}

sub ensureIsMember {
    my ($str, $set) = @_;
    foreach my $i (@$set) {
	if($str eq $i) { return; }
    }
    die "Error: string '$str' is not member of set: [".join(", ",@$set)."]";
}
