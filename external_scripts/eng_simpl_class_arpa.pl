#!/usr/bin/env perl 

### made compatible with ngram count format

### modified by Ahmad Emami 5/17/2006

### created by Fei on 12/8/04, last major modification: 3/10/05.
## modified from /nls/p/051/e/eng_class.pl and /nls/p/169/e/classnum_en.pl
##
## The 7 classes produced by this tool: num, ennum, ordinal, tel, url, email, tgtlang

## The classer merge only numbers (e.g., one hundred) and percent (e.g., 90 percent)

## This new classer only classifies 
##    - Arabic numbers and ordinal numbers (e.g., 21st), 
##    - email addresses and urls.
##    - the spell-out numbers 
##         ONLY if $class_spelt_out_num is set to 1.

## The difference between this classer and /nls/p/169/e/classnum_en.pl:
##  -- this classer does not mark part of a token as a num.
##  -- this classer recognizes email addresses and urls.


## This code is different from /nls/p/051/e/eng_class.pl in that
##  -- this classer is much simplier, and it does not recognize types such as 
##       $DATE, $DATEYMD, $MONEY, $MONTH, $WEEKDAY, 
##
##  -- it uses $num for all numbers including those used to be tagged as
##       $PERCENT, $TIME, $TIMEDUR, $YEAR, and $RANGE, etc.
## 
##  -- it recognizes url, email, tgtlang, etc.
##
##  -- the patterns assumes that the tokenizer is making the right decision, 
##     therefore generally it checks each token only, instead of the 
###    whole string
##     ===> so it is much faster.
##          Speed: about 3M words/minute on one machine.

### Bugs that are not fixed:  3/7/05
##   - "second" is marked as $ordinal_(second): the problem becomes irrelevant
##          later when knb279.exec converts it back to "second" again.
##
##   - "one third" now is treated as two numbers, rather than one,
##      because it could be part of "one third - year student".
##      We need to modify kna279.exec to make sure that 1/3 in Chinese 
##        is not marked as number either.


### Spelt-out numbers
##   - several hundred, in the past ten plus years, five-year plan
##   - 90 percent, five million

## to run:
##   cat input | $0 keep_cont class_spell_out_num > output

use strict;


### main options for classing
my $class_spelt_out_num = 0;  # 1: to classify words such as "eight", "eighth"
my $merge_num_with_percent = 0;   # 1: merge "80 percent"

### English numbers
my $one2nine = 'one|two|three|four|five|six|seven|eight|nine';
my $digit = 'zero|'.$one2nine;
my $teens = 'ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen';
my $tens = 'ten|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninty';
my $powers = 'hundred|thousand|million|billion|trillion';
my $numall = "$digit|$teens|$tens|$powers";


my $one2nine_ord = 'first|second|third|fourth|fifth|sixth|seventh|eighth|ninth';
my $three2ten_ord = 'third|fourth|fifth|sixth|seventh|eighth|ninth|tenth';
my $teens_ord = 'tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth';
my $tens_ord = 'tenth|twentieth|thirtieth|fourtieth|fiftieth|sixtieth|seventieth|eightieth|nintieth';

my $powers_ord = 'hundredth|thousandth|millionth|billionth|trillionth';
my $numall_ord = "$one2nine_ord|$teens_ord|$tens_ord|$powers_ord";



die("usage: cat input | $0 keep_content classify_spelt_out_num\n") if (@ARGV != 2);


my $keep_cont = $ARGV[0] ;

$class_spelt_out_num = $ARGV[1];  


my $prev_word_num = 0;
my $new_word_num = 0;

my $line_num = 0;
while(<STDIN>){ 
    chomp;

    if(/^(\[b\s+|\]b|\]f|\[f\s+)/ || (/^\[[bf]$/) || (/^\s*$/)) {
	## markup or empty line
	print STDOUT "$_\n";
	next;
    }
    
    s/^\s*//;
    s/\s*\n//;

    my ($ngram,$count) = split(/\t+/,$_);
    my @words = split(/\s+/,$ngram);

    $prev_word_num += scalar @words;

    $line_num ++;
    if($line_num % 1000 == 0){
	#print STDERR "eng_simp_class.pl: finish ", $line_num / 1000, "K lines\n";
    }

    ## step 1: class each word
    my @newwords = () ;
    foreach my $w (@words){
	$w = classNum($w);
	push(@newwords,$w);
    }

    my $new_str = join(" ",@newwords);

    ## step 2: merge number and percent, etc.
    if($class_spelt_out_num && ($new_str =~ /\$ennum\_\(/i)){
	$new_str = merge_ennum(\@newwords, $new_str);
    }

    if($merge_num_with_percent){
	## $num_(80) percent => $num_(80@@percent)
	$new_str = ' ' . $new_str . ' ';
	$new_str =~ s/\s+\$([a-z]*num)\_\(([^\)]+)\)\s+(percent|\%)\s/ \$$1\_\($2\@\@$3\) /gi;
	## 5 per cent => $num_(80@@per@@cent)
	$new_str =~ s/\s+\$([a-z]*num)\_\(([^\)]+)\)\s+(per)\s+(cent)\s/ \$$1\_\($2\@\@$3\@\@$4\) /gi;
    }

    $new_str =~ s/^\s+//;
    $new_str =~ s/\s+$//;


    print STDOUT "$new_str\t$count\n"; 

    my @new_parts = split(/\s+/, $new_str);
    $new_word_num += scalar @new_parts;
}

print STDERR "eng_simpl_class.pl: finish all: non_empty_line_num=$line_num prev_word_num=$prev_word_num new_word_num=$new_word_num\n";



sub classNum(){
    my ($orig_str) = @_ ;   
    ## recognized patterns: .12, 45.6,  34,567 , 34:12, 34:45:67, 23-12

    my $str = $orig_str;
    $str =~ tr/A-Z/a-z/;

    ### many patterns come from eng_tokenizer.pl
    my $label = "";
    my $t1 =  '[a-z\d\_\-\.]';

    if($str =~ /^\-?(\.\d+|\d+((\.|\,|\:|\-|\/)\d+)*)\%?$/){
        ## 0.7, 5%
	$label = "num";
    }elsif($str =~ /^(\S+)\@(\S+)$/){
	### email address: xxx@yy.zz
	$label = "email";
    }elsif($str =~ /^(http|https|ftp|gopher|telnet|file)\:\/{0,2}([^\.]+)(\.(.+))*$/){
	### http://xx.yy.zz
	$label = "url";
    }elsif($str =~ /^(www)(\.(.+))+$/){
	### www.yy.dd/land/
	$label = "url";
    }elsif($str =~ /^\S+\.(com|co|edu|org|gov)(\.[a-z]{2,3})?\:{0,2}(\/\S*)?$/){
	### url: upenn.edu/~xx
	$label = "url";
    }elsif($str =~ /^\(\d+\)\s*\d+(\-\d+)*$/){
	### phone number: (914)244-4567
	$label = "tel";
    }elsif($str =~ /^\d+(st|nd|rd|th)$/){
	$label = "ordinal";
    }elsif($str =~ /^\/(($t1)+\/)+($t1)+\/?$/){
	## /nls/p/...
	$label = "tgtlang";
    }elsif($str =~ /^\\(($t1)+\\)+($t1)+\\?$/){
	## \nls\p\...
	$label = "tgtlang";
    }else{
	if($class_spelt_out_num){
	    if(($str =~ /^($numall)$/) ||
	       ($str =~ /^($tens)\-($one2nine)$/) ||
	       ($str =~ /^($one2nine)\-($three2ten_ord)s?$/)){
		## twenty-one, two-thirds, hundred
		$label = "ennum";
	    }elsif(($str =~ /^($numall_ord)$/) ||
		   ($str =~ /^($tens)\-($one2nine_ord)$/) ||
		   ($str =~ /^(($tens)\-)?($three2ten_ord)s$/)){
		## twenty-third, thirds, 
		$label = "ordinal";
	    }else{
		return $orig_str;
	    }
	}else{
	    return $orig_str;
	}
    }
	    
    if ($keep_cont){
	return "\$$label\_($orig_str)";
    }else{
	return "\$$label";
    }
}


sub merge_ennum {
    my ($words_ptr, $orig_str) = @_;

    my $new_str = "";
    my @flags = ();  ## 0: should not be merged with others (e.g., 10%)
                     ## 1: cardinal num, can be merged; 2: ordinal number
    my @tokens = ();
    my @orig_tokens = ();
    my @tags = ();
    my $tok_num = scalar @$words_ptr;

    foreach my $w (@$words_ptr){
	my $tag = "";
	my $orig_tok = $w;
	my $tok = $w;
	$tok =~ tr/A-Z/a-z/;
	my $flag = 0;
	if($w =~ /^\$([a-z]*num|ordinal)\_\(((.*)[^\%])\)$/i){
	    # $*num, or $ordinal, whose $tok does not end with "%".
	    $tag = $1;
	    $orig_tok = $2;
	    $tok = $orig_tok;
	    $tok =~ tr/A-Z/a-z/;
	    if($tag =~ /ordinal/i){
		$flag = 2;
	    }else{
		## cardinal number
		$flag = 1;
		if(($tok =~ /^($powers)$/) && ($tokens[$#tokens] =~ /^a$/)){
		    ## change the tag of the word "a" in "a hundred"
		    $flags[$#flags] = 1;
		}
	    }
	}else{
	    if($tok =~ /^(and|-)$/){
		$flag = 3;
	    }
	}

	push(@tags, $tag);
	push(@tokens, $tok);
	push(@orig_tokens, $orig_tok);
	push(@flags, $flag);
	##print STDERR "flag=$flag, tok=$tok, tag=$tag\n";
    }

    my $cur_ptr = 0;
    while($cur_ptr < $tok_num){
	my $flag = $flags[$cur_ptr];
	my $token = $tokens[$cur_ptr];
	if($flag != 1){
	    ## the current word is not a cardinal number
	    $new_str .= $words_ptr->[$cur_ptr] . " ";
	    $cur_ptr ++;
	    next;
	}

	my $cur_begin = $cur_ptr;
	$cur_ptr = $cur_begin + 1;

	my $prev_tok = $tokens[$cur_begin];
	my $prev2_tok = "";
	while(($cur_ptr < $tok_num) && ($flags[$cur_ptr] > 0)){
	    my $tok = $tokens[$cur_ptr];
	    my $res = nums_are_compatible($prev2_tok, $prev_tok, $tok, $flags[$cur_ptr], 
					  \$tags[$cur_ptr]);
	    if($res == 2){
		## merge prev_tok and tok, and check the next one
		$cur_ptr ++;
		$prev2_tok = $prev_tok;
		$prev_tok = $tok;
	    }elsif($res == 1){
		## merge prev_tok and tok, but no more merging with the next one
		$cur_ptr ++;
		last;
	    }else{
		## do not merge prev_tok and tok
		last;
	    }
	}

	if($tokens[$cur_ptr-1] =~ /^(and|-)$/i){
	    ## one hundred and we => $ennum_(one@@hundred) and we
	    $cur_ptr --;
	}
	my $cur_end = $cur_ptr - 1;

	## print STDERR "begin=$cur_begin end=$cur_end\n";
	my $new_tok = join("@@", @orig_tokens[$cur_begin..$cur_end]);
	
	my $new_tag = $tags[$cur_end];

	$new_str .= "\$$new_tag\_\($new_tok\)" . " ";
    }
    return $new_str;
}


### decide whether the current number b should be merged with prev token a 
##    in function merge_num()
## There are three final results:
##   (1) merge a and b, and keep going: => return 2
##   (2) merge a and b, and the next token should not be merged with b,
##            => return 1
##   (3) do not merge a and b
##            => return 0

### We assume that $prev_tok and $cur_tok are in lower case.
sub nums_are_compatible {
    my ($prev2_tok, $prev_tok, $cur_tok, $cur_flag, $cur_tag_ptr) = @_;

    ## case 1: the cur_tok is not a num or ordinal    
    if($cur_flag >= 3){
	if((($cur_tok =~ /^\-$/) && ($prev_tok =~ /^($tens|$one2nine)$/)) ||
	   (($cur_tok =~ /^and$/) && ($prev_tok =~ /^($powers)$/))){
	    ## "and" in "one hundred and ...", or "-" in "twenty-three"
	    return 2;
	}

	return 0;
    }

    ## case 2: cur_tok is an ordinal number
    if($cur_flag == 2){

	## case 2.1: "third" in "one third", or "thirds" in "two thirds", "two - thirds".
	if($cur_tok =~ /^($three2ten_ord)s?$/){
	    if(($prev_tok =~ /^($one2nine)$/) ||
               (($prev2_tok =~ /^($one2nine)$/) &&
		($prev_tok =~ /^\-$/))){
		$$cur_tag_ptr = "ennum"; ## change the tag
		return 1;
	    }
	}


	## case 2.2: "third" in "twenty third" or "twenty - third" or "two - thirds"
	if(($cur_tok =~ /^($one2nine_ord)/) &&
	   ($prev_tok =~ /^($tens|and|-)/)){
	    return 1;
	}


	## case 2.3: other cases: e.g., "first" in "five first" ... => do not merge
	return 0;
    }

    #### case 3: the current token is a cardinal number
    ## case 3.1: 12 in "xxx 12" => do not merge
    if($cur_tok =~ /\d/){
	return 0;
    }

    
    ## case 3.2: "thousand" in "1.1 thousand" or "two thousand" => merge
    if($cur_tok =~ /^($powers)$/){
	if(($prev_tok =~ /\d/) || ($prev_tok =~ /^($numall|a)$/)){
	    return 2;
	}else{
	    return 0;
	}
    }

    ## case 3.3: "twenty" in "one hundred and twenty" => merge
    if($cur_tok =~ /^($teens|$tens)$/){
	if(($prev_tok =~ /^and$/) || ($prev_tok =~ /^($powers)$/)){
	    return 2;	
	}else{
	    return 0;
	}
    }

    ## case 3.4: "one" in "twenty - one" => merge
    if($cur_tok =~ /^($one2nine)$/){
	if(( ($prev_tok =~ /^(and|-)$/) && ($prev2_tok =~ /^($powers|$tens)$/)) ||
	   ($prev_tok =~ /^($powers|$tens)$/) ){
	    return 2;	
	}else{
	    return 0;
	}
    }

    return 0;
}
