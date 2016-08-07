use strict;
use warnings;


# Note: this script generates the bash script which can create the test. 
# It does not literally create the test.
die "Usage: $0 param-file > bash_script" if @ARGV != 1;

my $paramFile = shift;

my $params = &readParams($paramFile);

# We are only doing this because it's slightly safer to not risk accidentally overwriting any of the data files
$params = &copyFilesToWorkDir($params);

# The f_ prefix refers to the filtered version
my ($f_test, $f_ref) = &selectTestData($params);
my ($f_en, $f_fr, $f_feAlign, $f_efAlign) = &selectTrainingData($params, $f_test);
my ($f_phr, $f_lex_phr) = &extractPhrases($params, $f_en, $f_fr, $f_feAlign, $f_efAlign, $f_test);
my ($f_lmarray) = &filterLMs($params, $f_phr);
my ($f_ini) = &createDecoderParFile($params, $f_phr, $f_lex_phr, $f_lmarray);
my ($f_decOutput) = &runDecoder($params, $f_ini, $f_test);


sub copyFilesToWorkDir {
    my ($params) = @_;
    my %outParams = (%$params);
    
    my $workDir = &getPar($params, "workDir");

    &execCommand("mkdir -p ".$workDir);

    foreach my $str ("enTrain", "frTrain", "feAlign", "efAlign", "test", "ref") {
	my $outFile = "$workDir/$str";
	&copyFile(&getPar($params, $str), $outFile);
	$outParams{$str} = $outFile;
    }

    my @lms = split /\s+/, &getPar($params, "arpa_lms");
    my @outputLMs = ();
    for(my $i=0; $i<@lms; $i++) {
	my $outFile = "$workDir/lm.arpa.$i";
	&copyFile($lms[$i], $outFile);
	push @outputLMs, $outFile;
    }
    $outParams{"arpa_lms"} = join(" ", @outputLMs);

    return \%outParams;
}

sub copyFile {
    my ($in, $out) = @_;
    &execCommand("cp $in $out");
}

sub selectTestData {
    my ($params) = @_;
    
    my $selectScript = &getPar($params, "selectTestDataScript");
    my $refSelector = &getPar($params, "indexBasedSelector");
    my $workDir = &getPar($params, "workDir");
    my $test   = &getPar($params, "test");
    my $ref    = &getPar($params, "ref");
    my $numSents = &getPar($params, "numTestSentences");
    my $maxSentLen = &getPar($params, "maxTestSentenceLen");

    my $f_test    = "$workDir/test.filtered";
    my $f_indices = "$workDir/test.filtered.indices";
    my $f_ref     = "$workDir/ref.filtered";
    &execCommand("$selectScript $test $f_test $f_indices $numSents $maxSentLen");
    &execCommand("$refSelector $f_indices $ref $f_ref 1");
		 
    return ($f_test, $f_ref);
}

sub selectTrainingData {
    my ($params, $f_test) = @_;
    
    my $corpusSelector = &getPar($params, "corpusSelector");
    my $alignSelector = &getPar($params, "indexBasedSelector");
    my $numSentences = &getPar($params, "numTrainSentences");
    my $workDir = &getPar($params, "workDir");

    my $en = &getPar($params, "enTrain");
    my $fr = &getPar($params, "frTrain");

    my $efAlign = &getPar($params, "efAlign");
    my $feAlign = &getPar($params, "feAlign");

    my $f_en = "$workDir/enTrain.filtered";
    my $f_fr = "$workDir/frTrain.filtered";
    my $f_indices = "$workDir/train.filtered.indices";

    my $f_feAlign = "$workDir/feAlign.filtered";
    my $f_efAlign = "$workDir/efAlign.filtered";

    &execCommand("$corpusSelector $numSentences $en $fr $f_test $f_en $f_fr $f_indices");
    &execCommand("$alignSelector $f_indices $efAlign $f_efAlign 3");
    &execCommand("$alignSelector $f_indices $feAlign $f_feAlign 3");

    return ($f_en, $f_fr, $f_feAlign, $f_efAlign);
}

sub extractPhrases {
    my ($params, $f_en, $f_fr, $f_feAlign, $f_efAlign, $f_test) = @_;

    my $workDir = &getPar($params, "workDir");
    my $extractor = &getPar($params, "phraseExtractor");

    my %phrExtractProps = ();
    $phrExtractProps{"fCorpus"} = $f_fr;
    $phrExtractProps{"eCorpus"} = $f_en;
    $phrExtractProps{"feAlign"} = $f_feAlign;
    $phrExtractProps{"efAlign"} = $f_efAlign;

    $phrExtractProps{"fFilterCorpus"} = $f_test;
    $phrExtractProps{"outputDir"} = "$workDir/phrase_extract";

    my $f_phr_param = "phrase_table.filtered.gz";
    my $f_lex_phr_param = "phrase_table.lex_reordering.filtered.gz";

    my $f_phr = $phrExtractProps{"outputDir"}."/".$f_phr_param;
    my $f_lex_phr = $phrExtractProps{"outputDir"}."/".$f_lex_phr_param;

    my $commandString = $extractor;
    foreach my $p (sort keys %phrExtractProps) {
	$commandString .= " -$p ".$phrExtractProps{$p};
    }
    $commandString .= " -extractors edu.stanford.nlp.mt.train.MosesPharoahFeatureExtractor=${f_phr_param}:edu.stanford.nlp.mt.train.CountFeatureExtractor=${f_phr_param}:edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor=${f_lex_phr_param}";

    &execCommand("mkdir -p ". $phrExtractProps{"outputDir"});
    &execCommand($commandString);

    return ($f_phr, $f_lex_phr);
}

sub filterLMs {
    my ($params, $f_phr) = @_;

    my $workDir = &getPar($params, "workDir");

    my $extractTextCommand = &getPar($params, "extractTextFromPhraseTable");
    my $filterCommand = &getPar($params, "kenLMFilter");
    my $buildBinaryCommand = &getPar($params, "kenLMBuildBinary");

    my @arpa_lms = split /\s+/, &getPar($params, "arpa_lms");
    my @ken_lms = ();
    
    my $extractedEnglish = "$workDir/extracted_english_phrases";
    &execCommand("zcat -f $f_phr | $extractTextCommand 1 > $extractedEnglish");
    
    
    for(my $i=0; $i<@arpa_lms; $i++) {
	my $arpa_lm = $arpa_lms[$i];
	my $filtered_arpa_lm = "$workDir/lm.arpa.$i.filtered";
	my $ken_lm = "$workDir/ken_lm.$i.bin";
	push @ken_lms, $ken_lm;
	&execCommand("bzcat -f $arpa_lm | $filterCommand single vocab:$extractedEnglish $filtered_arpa_lm");
	&execCommand("$buildBinaryCommand $filtered_arpa_lm $ken_lm");
    }
    
    return [@ken_lms];
}

sub createDecoderParFile {
    my ($params, $f_phr, $f_lex_phr, $f_lmarray) = @_;

    my $workDir = &getPar($params, "workDir");
    my $replace = &getPar($params, "replaceScript");
    my $iniTemplate = &getPar($params, "decoderIniTemplate");
    my $f_ini = "$workDir/decoder.ini";
    
    my $command_string = "cat $iniTemplate | $replace \"<<PHRASE_TABLE>>\" $f_phr |";
    foreach my $lm (@$f_lmarray) {
	$command_string .= " $replace \"<<LM>>\" $lm |";
    }
    $command_string .= " $replace \"<<HIERARCHICAL_REORDERING_TABLE>>\" $f_lex_phr > $f_ini";
    &execCommand($command_string);

    return $f_ini;
}

sub runDecoder {
    my ($params, $f_ini, $f_test) = @_;
    my $workDir = &getPar($params, "workDir");
    my $decoderCommand = &getPar($params, "decoderCommand");
    my $f_test_expected_output = "${f_test}.expected_output";

    &execCommand("$decoderCommand $f_ini < $f_test > $f_test_expected_output");
    return $f_test_expected_output;
}

sub execCommand {
    my ($cmd) = @_;
    print $cmd ."\n";
}

sub readParams {
    my ($file)= @_;

    my %params = ();
    open IN, $file or die "Cannot open file: $file";
    while(<IN>) {
	next if m/^\s*\#/;
	next if m/^\s*$/;
	my ($a,$b) = split /:/, $_, 2;
	$a = &trim($a);
	$b = &trim($b);
	$params{$a} = $b;
    }
    close IN;
    return \%params;
}

sub trim {
    my ($str) = @_;
    $str =~ s/^\s+//;
    $str =~ s/\s+$//;
    return $str;
}

sub getPar {
    my ($params, $p) = @_;
    if(exists($params->{$p})) {
	return $params->{$p};
    }
    else {
	die "Required parameter does not exist: $p";
    }
}
