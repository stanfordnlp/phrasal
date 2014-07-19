#!/usr/bin/env perl

# created on 3/9/05 by Fei, modified from /nls/p/051/e/eng_norm.pl

# The input and output should have the same number of tokens and the same tagsets.
#
# The input and output tag sets have 7 classes:
#   NUM, ENNUM, ORDINAL, URL, EMAIL, TEL, TOKEN

# eng_simp_norm.pl: 
# It processes the output of eng_simpl_class.pl

# The major differences between eng_norm.pl and eng_simpl_norm.pl
#  - the differences between eng_class.pl and eng_simpl_class.pl are 
#    carried over to eng_*norm.pl: 
#    => the following are not handled by eng_simpl_norm.pl:
#        * now part of $NUM: $PERCENT, $YEAR, $TIME, $TIMEDUR, $IP 
#        * not classed anymore: $WEEK, $DATE*, $MONEY
#    => the following are handled by eng_simpl_norm.pl, not eng_norm.pl
#       $EMAIL, 


# optional perl line to turn on warnings before any code is compiled
# equivalent to '-w' on the normaly #!/.././perl line

BEGIN {$^W = 1;}

# input: (the input was created by eng_simpl_class.pl)
#   sentence with $NUM_(eng), etc.
#
# output:
#   sentence with $NUM_(eng || \d+) ....

# The input has the following classes (either in uppercase or lowercase)
# $NUM, $ENNUM, $ORDINAL
# $URL, $TEL, $EMAIL 
# 

use FileHandle;
use strict;

######### step 0: options for the code
my $keep_orig = 1;
my $keep_norm_form = 1;
my $keep_orig_tag_for_unk = 0;



## delimiter used in type_str()
my $rdem = "||";   #  if the value is modified, remember to modify 
                   # split_orig_and_norm_str function in this file

my $word_delim = "@@"; # seperate words in $orig_ch if it has multiple words

my $date_delim = "-";  # date_delim for the output.


## constants, used in type_str()
my $NORM_ONLY = 1;    # output "norm_form" only; no class info
my $CLASS_FORM = 2;   # output $TYPE_(orig || norm_form) or
                      #   $TYPE_(norm_form) depending on $keep_orig
                      #  and $keep_norm_form
my $ORIG_WORD_ONLY = 3;  # output "orig"

my $debug = 1;

######### step 1: initalization

###### initialization for %word2num
my %word2num = ();
my %ord2card = ();

initialize_word2num();

# cardinal numbers 
my $hundred = "hundred";
my $thousand = "thousand";
my $ten = "ten";
my $billion = "billion";
my $trillion = "trillion";

## ordinal numbers
my $one2nine_ord = 'first|second|third|fourth|fifth|sixth|seventh|eighth|ninth';
my $three2ten_ord = 'third|fourth|fifth|sixth|seventh|eighth|ninth|tenth';
my $teens_ord = 'tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth';
my $tens_ord = 'tenth|twentieth|thirtieth|fourtieth|fiftieth|sixtieth|seventieth|eightieth|nintieth';
my $powers_ord = 'hundredth|thousandth|millionth|billionth|trillionth';
my $numall_ord = "$one2nine_ord|$teens_ord|$tens_ord|$powers_ord";


### cardinal number
my $one2nine = 'one|two|three|four|five|six|seven|eight|nine';
my $digit = 'zero|'.$one2nine;
my $teens = 'ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen';
my $tens = 'ten|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety';
my $powers = 'hundred|thousand|million|billion|trillion';
my $numall = "$digit|$teens|$tens|$powers";




###################### step 2: normalize numbers and dates
while (<>) {
  chomp;
  my $line = $_;

  if($line =~ /^\[(b|f)/){
      print "$_\n";
      next;
  }

  if($line !~ /\$/){
    print "$_\n";
    next;
  }

  $line = normalize_class_in_line($line, $CLASS_FORM);
  $line =~ s/^\s+//;

  print STDOUT " $line\n";
}

##############################################################################
#################################### subroutines
##############################################################################

# deal with $NUM_(xx) in a line
# return a string where $NUM_(xx) is replaced with sth like $NUM_(xx||yy)
#   yy is the digit form of xx.
#
sub normalize_class_in_line {
  my ($string, $form_opt) = @_;

  my $out = "";

  if($string !~ /\$([A-Za-z]+)\_\(/){
      return $string;
  }

  my @tokens = split(/\s+/, $string);

  foreach my $tok (@tokens){
      my $new_str = $tok;
      
      if($tok =~ /\$([A-Za-z]+)\_\((.*)\)$/){
	  my $class_tag = $1;
	  my $orig = $2;
    
	  my $tmp_tag = $class_tag;
	  $tmp_tag =~ tr/A-Z/a-z/; 

	  #print STDERR "START: $class_tag, $orig\n";
	  if($tmp_tag eq "num"){
	      $new_str = normalize_num($class_tag, $orig, $form_opt);
	  }elsif($tmp_tag eq "ennum"){
	      $new_str = normalize_num($class_tag, $orig, $form_opt);
	  }elsif($tmp_tag eq "ordinal"){
	      $new_str = normalize_ordinal($class_tag, $orig, $form_opt);
	  }elsif($tmp_tag eq "url"){
	      $new_str = normalize_token($class_tag, $orig, $form_opt);
	  }elsif($tmp_tag eq "email"){
	      $new_str = normalize_token($class_tag, $orig, $form_opt);
	  }elsif($tmp_tag eq "tel"){
	      $new_str = normalize_tel($class_tag, $orig, $form_opt);
	  }elsif($tmp_tag eq "token"){
	      $new_str = normalize_token($class_tag, $orig, $form_opt);   
	  }else{
	      print_log("unknown class_tag $class_tag: $orig\n");
	      $new_str = unk_type_str($class_tag, $orig, $form_opt);
	  }

          print_log("normalization result: \$$class_tag\_\($orig\) => $new_str");
      }
      
      $out .= ' ' . $new_str;
  }

  
  return $out;
}

###################################3 normalization for each class
################ normalize numbers
sub normalize_num {
  my ($class_tag, $orig, $form_opt) = @_;
  
  my $tmp = $orig;
  $tmp =~ s/$word_delim//g;
  $tmp =~ s/,//g;

  if(($tmp =~ /^(-?\d+)$/) || ($tmp =~ /^(-?\d+([\.|\-|\,]\d+)*)$/)){
    return normalize_asc_num($class_tag, $orig, $form_opt);
  }else{
    return normalize_eng_num($class_tag, $orig, $form_opt);
  }
}

## similar to cword2float() in /nls/p/051/e/cnumbers4.pm, which is
##  used by ch_norm.pl
##
## the input can include ".", "and", and "@@". 
## It should not include num_mod such as "more", "than", etc.
##
## return the value only.
sub eword2num {
  my ($orig) = @_;
  
  my $num = $orig;

  $num =~ tr/A-Z/a-z/;   ## lowercase the words.
  $num =~ s/$word_delim\s*and$word_delim/$word_delim/g; # remove "and"
  $num =~ s/($word_delim)?,($word_delim)?//g;  ## remove ","
  $num =~ s/$word_delim\.($word_delim)?/\./g;   
  
  $num =~ s/($word_delim)+/$word_delim/g;

  
  #### case 0: a number without English words
  if($num =~ /^\-?(\.\d+|\d+((\.|\,|\:|\-|\/)\d+)*)(($word_delim)?\%)?$/){
    return normalize_asc_num("NUM", $num, $NORM_ONLY);
  }

  ### case 1: percent
  if($num =~ /^(.+)($word_delim)(percent|per($word_delim)cent)$/){
      my $result = eword2num($1);
      if($result eq ""){
	  return "";
      }else{
	  return $result . "%";
      }
  }

  #### case 2: a number starts with "-"
  if($num =~ /^(-)($word_delim)?(.*)$/){
      my $sign = $1;
      my $result = eword2num($3);
      if($result eq ""){
	  return "";
      }else{
	  return $sign . $result;
      }
  }

  # case 3: ordinal number: "two thirds", "two-thirds", but not "twenty-first"
  if($num =~ /^($one2nine)($word_delim)?\-?($word_delim)?($three2ten_ord)s?$/){
      my $tmp1 = $1;
      my $tmp2 = $4;
      my $tmp1_val = $word2num{$tmp1};
      my $tmp2_val = $word2num{$ord2card{$tmp2}};
      if(($tmp1_val =~ /^\d+/) && ($tmp2_val =~ /^\d+/)){
	  my $norm = "$tmp1_val/$tmp2_val";
	  return $norm;
      }else{
	  return "";
      }
  }


  #### case 3': 4-5@@million => 4000000-5000000
  $num =~ s/($word_delim)\-/\-/g;  # remove $word_delim around "-"
  $num =~ s/\-($word_delim)/\-/g;
  if($num =~ /^(\d+([\.|\,]\d+)*)\-(\d+([\.|\,]\d+)*)($word_delim)($powers)$/){
      my $num1 = $1;
      my $num2 = $3;
      my $pow = $word2num{$6};
      my $val1 = $num1 * $pow;
      my $val2 = $num2 * $pow;
      return $val1 . "-" . "$val2";
  }
      
      
  ################ replace "_" and "-" in "twenty-six" with "@@"
  $num =~ s/\_/$word_delim/g;   ## remove "_"
  $num =~ s/\-/$word_delim/g;   ## remove "-" in "twenty-six"
  $num =~ s/($word_delim)+/$word_delim/g;


  my @parts = split(/$word_delim/, $num);
  my $part_num = scalar @parts;
  
  # remove "s" from "thousands"
  for(my $i=0; $i<$part_num; $i++){
    if($parts[$i] =~ /^($numall)s$/){
      #print STDERR "see: $parts[$i], $1\n";
      $parts[$i] = $1;
    }
  }
  
  
  ########## case 4:  simple conversion: e.g., twenty
  if ($part_num == 1) {
    my $foo = $parts[0];
    my $tmp = $word2num{$foo};
    if(defined($tmp)){
      ## "ten" => "10"
      return $tmp;
    }else {
      return "";
    }
  }
  
  ########### case 5:  compound case: e.g., two thousand one hundred
  ### $mid will store the unit w s.t. " x w y " = x*w+y
  #             
  #  e.g., if the string is "two hundred million and five thousand"
  #   $mid will be "million"
  my $pre = "";
  my $mid = "";
  my $post = "";
  my $matched = 0;
  my @units = ("trillion", "billion", "million", "thousand", "hundred");
LEVEL:  for my $i (0..$#units) {
    my $unit = $units[$i];  # $char is the only unit
    
    # modify to deal with "yi1 wan4 san3 qian1 wu3 bai3" => 13500 10k,
    # not 3501 10k
    for(my $i=$part_num-1; $i>=0; $i--){
      my $part = $parts[$i];
      if($part eq $unit){
	$matched = 1;
	$pre = join($word_delim, @parts[0..$i-1]);
	$mid = $unit;
	$post = join($word_delim, @parts[$i+1..$part_num-1]);

	## print STDERR "found: +$pre+, +$mid+, +$post+\n";
	last LEVEL;
      }
    }
  }

  my $result = "";
  if ($matched) {
    #### there is a unit word in the string
    my $vpre = "";
    if($pre ne ""){
      $vpre = eword2num($pre);
      if($vpre eq ""){
	  return "";
      }
    }

    my $vpost = "";
    if($post ne ""){
      $vpost = eword2num($post);
      if($vpost eq ""){
	  return "";
      }
    }

    my $vmid = eword2num($mid);
    
    #print STDERR "value: $vpre $vmid $vpost\n";
    ## case 5.1: "num1 num2 {num4} num3" => "num1*num4*num3 to num2*num4*num3"
    ##  where num3 is a $mid
    ##  e.g., two to three thousand => 2000~3000 
    ##        twenty to one hundred => 20~100, not 2000~100
    ## "~" is inserted by cword2num()
    if ($vpre =~ /^(\d+(\.\d+)?)[\~|\-](\d+(\.\d+)?)$/) {
      if ($vpost eq "") {
	my $tmp1 = $1;
	my $tmp2 = $3;
	
	if($tmp1 < $tmp2){
	    $tmp1 = $tmp1 * $vmid;   # $vmid is shi2 in this case
	}

	$tmp2 = $3 * $vmid;
	$result = $tmp1 . '~' . $tmp2;
	return $result;
      }else { #error pattern
	return "";
      }
    }
    
    # no left part means implicitly one
    # e.g., "shi1 er2" is the same as "yi1 shi1 er2"
    if($vpre eq ""){ 
      $vpre = 1;
    }elsif($vpre !~ /^\d+(\.\d+)?$/){
	## error pattern: e.g., 1/2
	return "";
    }
	
    
    ## case 5.2: the normal case: two thousand one hundred
    if($vpost eq ""){
      return $vpre*$vmid;
    }else{
      ## e.g., "one hundred and two" => 102
      $result =  $vpre*$vmid + $vpost; 
    }

    #print STDERR "cword2num result: $string => $result\n";
    return $result;
  }

  ################# case 6: words with no unit words: e.g., twenty two, nineteen eighty
  if($part_num == 2){
    my $val1 = $word2num{$parts[0]};
    if(!defined($val1)){
	return "";
    }

    my $val2 = $word2num{$parts[1]};
    if(!defined($val2)){
	return "";
    }
    
    if(($parts[0] =~ /^($tens)$/i) && ($parts[1] =~ /^($one2nine)$/i)){
	### twenty two
	return $val1 + $val2;
    }else{
	### nineteen eighty
	return $val1 . $val2;
    }
  }


  ############### case 7: range: 20 to 30
  if(($part_num == 3) && ($parts[1] eq "to")){
      my $val1 = eword2num($parts[0]);
      my $val2 = eword2num($parts[2]);
      
      if(($val1 ne "") && ($val2 ne "")){
	  return $val1 . "~" . $val2;
      }else{
	  return "";
      }
  }

  return "";
}




sub normalize_eng_num {
  my ($class_tag, $orig, $form_opt) = @_;

  my $tmp = $orig;
  my $pref = "";

  my $norm = eword2num($tmp);

  if($norm eq ""){
    return unk_type_str($class_tag, $orig, $form_opt);
  }else{
    $norm = $pref . $norm;
    return type_str($class_tag, $orig, $norm, $form_opt);
  }
}



sub normalize_asc_num {
  my ($class_tag, $orig, $form_opt) = @_;

  my $num = $orig;
  $num =~ s/$word_delim//g;
  $num =~ s/,//g;
  
  ##################### Arabic number
  # case 1: numbers, e.g., 5.67, 1/3, 1:23, 1-23, 12-34%
  if($num =~ /^-?(\.\d+|\d+([\.|\:|\/|\-|\,]\d+)*)\%?$/){
    return type_str($class_tag, $orig, $&, $form_opt);
  }


  return unk_type_str($class_tag, $orig, $form_opt);
}



  
sub normalize_ordinal {
  my ($class_tag, $orig, $form_opt) = @_;

  # case 1:  235th, 21st, 2nd
  my $tmp = $orig;  
  $tmp =~ s/$word_delim//g;
  $tmp =~ s/,//g;
  if($tmp =~ /^(\d+)(st|nd|rd|th)$/i){
    return type_str($class_tag, $orig, $1, $form_opt);
  }

  # case 2: "twenty third" or "third"
  my $num = $orig;
  $num =~ tr/A-Z/a-z/;

  if($num =~ /($numall_ord)s?$/){
    my $last_digit = $1;

    my $card_digit = $ord2card{$last_digit};

    if(defined($card_digit)){
      $num =~ s/$last_digit/$card_digit/;
      ## print STDERR "see this: orig=+$orig+, +$last_digit+,  $card_digit, +$foo+\n";

      my $norm = normalize_num("NUM", $num, $NORM_ONLY);
      if($norm eq ""){
	return unk_type_str($class_tag, $orig, $form_opt);
      }else{
	return type_str($class_tag, $orig, $norm, $form_opt);
      }
    }else{
      print STDERR "Error: ord2card for $last_digit is undefined\n";
      return unk_type_str($class_tag, $orig, $form_opt);
    }
  }

  return unk_type_str($class_tag, $orig, $form_opt);
}


sub normalize_token {
  my ($class_tag, $orig, $form_opt) = @_;

  my $tmp = $orig;
  $tmp =~ s/$word_delim//g;

  return type_str($class_tag, $orig, $tmp, $form_opt);
}

sub normalize_tel {
  my ($class_tag, $orig, $form_opt) = @_;

  my $tmp = $orig;
  $tmp =~ s/$word_delim//g;

  # (914)xxx-yyyy, and "-" is optional.
  if($tmp =~ /^\((\d+)\)(\d+)-(\d+)$/){
      my $norm = $1 . "-" . $2 . "-" . $3;
      return type_str($class_tag, $orig, $norm, $form_opt);
  }

  # all others:
  return type_str($class_tag, $orig, $orig, $form_opt);
}






########## print functions

# e.g., input is (NUM, ten, 10)
#        the output is $NUM_(ten||10) or $NUM_(10), or 10
#        depending on $form_opt, $keep_orig, and $keep_norm_form
#
# We assume that $typ is not "".

sub type_str {
  my ($typ, $orig, $norm, $form_opt) = @_;
  my $ret;

  if($form_opt == $NORM_ONLY){
      $ret = $norm;
  }else{
    if($form_opt == $CLASS_FORM){
      if($keep_orig && $keep_norm_form){
	$ret = " \$" . $typ . "_(" . $orig . $rdem . $norm . ")";
      }elsif(!$keep_orig && !$keep_norm_form){
	$ret = " \$" . $typ;
      }elsif($keep_orig && !$keep_norm_form){
	$ret = " \$" . $typ . "_(" . $orig .  ")";
      }else{
	$ret = " \$" . $typ . "_(" . $norm . ")";
      }
      $ret =~ s/^\s+//;
    }else{
      $ret = $orig;
    }
  }

  return $ret;
}




# for the case where $type_($orig) as input cannot be processed
#   by the normalizer_xx functions.
sub unk_type_str {
    my ($orig_type, $orig, $form_opt) = @_;
    if($form_opt == $NORM_ONLY){
      return "";
    }elsif($form_opt == $CLASS_FORM){
      if($keep_orig_tag_for_unk){
	return "\$" . $orig_type . "_(" . $orig . ")";
      }else{
	return $orig;
      }
    }else{
      return $orig;
    }
}

sub print_log {
  my ($str) = @_;

  if($debug > 0){
    print STDERR "$str\n";
  }
}	


sub lowercase {
  my ($str) = @_;
  
  $str =~ tr/A-Z/a-z/; 
  return $str;
}


######### initialize $word2num
## the key of %word2num should always be in lowercase.
sub initialize_word2num {
  # cardinal numbers

  $word2num{zero} = 0;

  $word2num{a} = 1;

  $word2num{one} = 1;   $word2num{two} = 2;  $word2num{three} = 3;
  $word2num{four} = 4;  $word2num{five} = 5;  $word2num{six} = 6;
  $word2num{seven} = 7;  $word2num{eight} = 8;  $word2num{nine} = 9;

  $word2num{ten} = 10; $word2num{eleven} = 11;  $word2num{twelve} = 12;
  $word2num{thirteen} = 13; $word2num{fourteen} = 14; 
  $word2num{fifteen} = 15; $word2num{sixteen} = 16;
  $word2num{seventeen} = 17; $word2num{eighteen} = 18;
  $word2num{nineteen} = 19;
  
  $word2num{twenty} = 20; $word2num{thirty} = 30;
  $word2num{forty} = 40; $word2num{fifty} = 50;
  $word2num{sixty} = 60;  $word2num{seventy} = 70;
  $word2num{eighty} = 80;  $word2num{ninty} = 90;

  $word2num{hundred} = 100; $word2num{thousand} = 1000;
  $word2num{million} = 1000000;
  $word2num{billion} = 1000000000;
  $word2num{trillion}= 1000000000000;

  # ordinal numbers
  $ord2card{first} = 'one';      $ord2card{second} = 'two';  
  $ord2card{third} = 'three';    $ord2card{fourth} = 'four'; 
  $ord2card{fifth} = 'five';     $ord2card{sixth} = 'six';
  $ord2card{seventh} = 'seven';  $ord2card{eighth} = 'eight'; 
  $ord2card{ninth} = 'nine';     $ord2card{tenth} = 'ten';

  $ord2card{eleventh} = 'eleven';     $ord2card{twelfth} = 'twelve';
  $ord2card{thirteenth} = 'thirteen'; $ord2card{fourteenth} = 'fourteen'; 
  $ord2card{fifteenth} = 'fifteen';   $ord2card{sixteenth} = 'sixteen';
  $ord2card{seventeenth} = 'seventeen'; $ord2card{eighteenth} = 'eighteen';
  $ord2card{nineteenth} = 'nineteen';
  
  $ord2card{twentieth} = 'twenty';    $ord2card{thirtieth} = 'thirty';
  $ord2card{fortieth} = 'forty';      $ord2card{fiftieth} = 'fifty';
  $ord2card{sixtieth} = 'sixty';      $ord2card{seventieth} = 'seventy';
  $ord2card{eightieth} = 'eighty';    $ord2card{nintieth} = 'ninty';

  $ord2card{hundredth} = 'hundred';   $ord2card{thousandth} = 'thousand';
  $ord2card{millionth} = 'million';   $ord2card{billionth} = 'billion';
  $ord2card{trillionth} = 'trillion';

  # weekdays: the following is not needed
  $word2num{sunday} = 0; 
  $word2num{monday} = 1; 
  $word2num{tuesday} = 2;
  $word2num{wednesday} = 3;
  $word2num{thursday} = 4;
  $word2num{friday} = 5;
  $word2num{saturday} = 6;

  $word2num{jan} = $word2num{january} = 1;
  $word2num{feb} = $word2num{february} = 2;
  $word2num{mar} = $word2num{march} = 3;
  $word2num{apr} = $word2num{april} = 4;
  $word2num{may} = 5;
  $word2num{jun} = $word2num{june} = 6;
  $word2num{jul} = $word2num{july} = 7;
  $word2num{aug} = $word2num{august} = 8;
  $word2num{sept} = $word2num{september} = 9;
  $word2num{sep} = 9;
  $word2num{oct} = $word2num{october} = 10;
  $word2num{nov} = $word2num{november} = 11;
  $word2num{dec} = $word2num{december} = 12;
}
