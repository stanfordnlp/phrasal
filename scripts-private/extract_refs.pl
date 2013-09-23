#!/usr/bin/perl

# Thang Sep13: fix the script to not use IPC plus several enhancements:
#   (a) make sure everythign is in utf8.
#   (b) check and create outDir if not exist.
#   (c) print progress info.
#   (d) add comments.

if ($#ARGV != 0 && $#ARGV != 1) {
   print stderr "Usage:\n\t$0 outDir lang < ref.sgm\n";
   exit -1;
}
binmode STDIN, ':utf8';
binmode STDOUT, ':utf8';

my $outDir = glob($ARGV[0]); # get full path
my $lang = $ARGV[1];
# check if directory exists.
if (-d "$outDir"){
  print stderr "# Directory $outDir exists!\n";
} else {
  print stderr "# Creating directory $outDir\n";
  system("mkdir -p $outDir");
}

# Process sgm file
print stderr "# Processing input ... \n";
$refid = "none";
$refidx = 1;
while(<STDIN>) {
  chomp;
  if (/^<doc docid/i) {
    $refid = $_;
    $refid =~ s/.*sysid="//;
    $refid =~ s/">//;
    $refid =~ s/reference_/ref/;
    if ($refid !~ /^[0-9]+$/) {
       $oldid = $refid;
       if (!(defined $refh{$refid})) {
         $refh{$refid} = $refidx++;
         print stderr "# Add new reference file $outDir/ref$refh{$refid}.raw\n";
       } 
       $refid = $refh{$refid};
       #print stderr "REF: $oldid => ref$refid ($refidx)\n";
    }
    $refid = "$outDir/ref$refid.raw";
  }

  if (/^<seg id=/) {
    $seg = $_;
    $seg =~ s/^<seg id="[^"]*"> *//;
    $seg =~ s/ *<\/seg>$//;
    $seg =~ s/ +/ /g;
    
    $seg =~ s/\$\S+\_\(([^|]+)\|\|([^)]*)\)/\2/g;
    $seg =~ s/\$\S+\_\(([^|)]+)\)/\1/g;
    while ($seg =~ /\s(\S+)\@\@(\S+)\s/) { 
       $seg =~ s/\s(\S+)\@\@(\S+)\s/ \1 \2 /g; 
    }

    # output to the corresponding ref
    open(OF, ">>:utf8", $refid) || die "#Can't open file \"$refid\"";
    print OF "$seg\n";
    close OF;
  }
}


# execute PTBTokenizer
#my $options = "keepAssimilations=false,asciiQuotes=true,normalizeParentheses=false,normalizeOtherBrackets=false,americanize=false,normalizeCurrency=false,escapeForwardSlashAsterisk=false"; # ptb3Escaping=false
#my $options = "ptb3Escaping=false,asciiQuotes=true,splitAssimilations=false"; # Sept 18th 2013 suggested options from Chris.
#print stderr "# Executing PTBTokenizer with options=$options ... ";

for(my $refid=1; $refid<$refidx; $refid++){ # go through each reference
  my $lang = "English";
  my $rawFile = "$outDir/ref$refid.raw";
  my $tokenizedFile = "$outDir/ref$refid.tokenized";
  my $isGzip = 0;
  my $isIBMPostProcessing = 1;
  #my $cmd = "java edu.stanford.nlp.process.PTBTokenizer  -preserveLines -options $options < $rawFile > $tokenizedFile";
  my $cmd = "$ENV{JAVANLP_HOME}/projects/mt/scripts-private/tokenize_new.sh $lang $rawFile $tokenizedFile $isGzip $isIBMPostProcessing tolower";
  print stderr "\n# Tokenizing ...\n  $cmd\n";
  system($cmd);
  
  # post processing 
  my $finalFile = "$outDir/ref$refid";
  print stderr "# Post processing, output to $finalFile\n";
  open(IN, "<:utf8", $tokenizedFile) || die "#Can't open file \"$tokenizedFile\"";
  open(OF, ">:utf8", $finalFile) || die "#Can't open file \"$finalFile\"";

  my $tref = "";
  while (<IN>) { chomp;
    $tref = "$_ ";
    $tref =~ s/ *$//;
    $tref = lc($tref); 
    print OF "$tref\n";
  }
  close IN;
  close OF;

  unlink($rawFile);
  unlink($tokenizedFile);
}


