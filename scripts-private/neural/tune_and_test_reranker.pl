use strict;
use warnings;

die "Usage: $0 pars" if scalar(@ARGV) != 1;

my $parsFile = shift @ARGV;

my $pars = &readParams($parsFile);

my $src = &getParam($pars, "source_file");
my $nbest = &getParam($pars, "nbest_input_file");
my $info = &getParam($pars, "test_set_info_file");
my $models = &getParam($pars, "model_prefixes");
my $testFlags = &getParam($pars, "test_flags");
my $outputDir = &getParam($pars, "output_dir");

~lmthang/lmthang-dl/nnlm/src11.tgt5.lr0.1.256-512-512-512.relu.selfnorm0.1.finetune2

my $SCRIPTS = $ENV{"JAVANLP_HOME"}."/projects/mt/scripts-private";

my $add_neural_scores = "$SCRIPTS/neural/add_neural_scores.sh";
my $extract_components = "~/javanlp/projects/mt/scripts-private/neural/extract_nbest.py";

perl $add_length_to_nbest $nbest $outputDir/nbest.with_len
bash $add_neural_scores $src $outputDir/nbest.with_len $outputDir/nbest.with_neural $models $testFlags gpu0 "LM,LM2,LM3" 0
$extract_components  sms_cts_all.nov14.tune_v11.sms_cts.1000best.nnlm.withlen info components/


bash ~/javanlp/projects/mt/scripts-private/neural/tune_reranker.sh tuned components/p2r2smscht_dev_2refs.nbest ~/bolt_data/tune_and_test/dryrun_060214/p2r2smscht_dev_2refs/ref len,LM,LM2,LM3,dm,nnlm0

perl ~/javanlp/projects/mt/scripts-private/rerank.pl tuned/train.wts < sms_cts_all.nov14.tune_v11.sms_cts.1000best.nnlm.withlen > sms_cts_all.nov14.tune_v11.sms_cts.1000best.nnlm.withlen.reranked



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
