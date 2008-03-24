# -*- coding: utf-8; -*-

package ChineseNumbers;

require Exporter;

use utf8;
use strict;
use Lingua::EN::Numbers qw(num2en num2en_ordinal);

use subs qw{EnglishToChineseNumber ChineseToEnglishNumber chinese_output english_output};

# Author: Erik Peterson
# E-mail: erik@mandarintools.com
# Source: http://www.mandarintools.com/numbers.html
#
# Usage:
#
# use ChineseNumbers;
#
# ChineseNumbers->EnglishToChineseNumber(enumber, [output_type])
#   enumber is an integer
#   output_type (which is optional) can be
#     trad       : Output with traditional Chinese characters
#     formaltrad : Output as formal numbers with traditional characters
#     simp       : Output using simplified Chinese characters
#     formalsimp : Output as formal numbers in simplified characters
#     unicodehex : Output as 4-digit Unicode hex blocks
#     pinyin     : Output as Hanyu Pinyin
#     jyutpin    : Output as Cantonese jyutpin romanization
#     yalecant   : Output as Cantonese Yale romanization
#    The default is trad
#
# ChineseNumbers->ChineseToEnglishNumber(cnumber, [english_type])
#   cnumber is a string in UTF-8
#   english_type is 
#      arabic    : plain Arabic numerals
#      comma     : plain Arabic numbers with commas
#      words     : written out using English words
#
# ChineseNumbers->chinese_output([option])
#   Set the default output type used by EnglishToChineseNumber
#    option can be any of the output options for EnglishToChineseNumber
#    If no arguments, returns the current default
#
# ChineseNumbers->english_output([option])
#   Set the default output type used by ChineseToEnglishNumber
#    option can be any of the output options for ChineseToEnglishNumber
#    If no arguments, returns the current default
#



BEGIN { }

my $default_outputtype = "trad";

my $default_englishtype = "arabic";

my $MINUS = "負";
my $DECIMAL = "點";

my @digits = ("零", "一", "二", "三", "四", "五", "六", "七", "八", "九"); 

my %digits = ("０", 0, "0", 0, "零", 0, "〇", 0, "○", 0,	
	      "１", 1, "1", 1, "一", 1, "壹", 1,
	      "２", 2, "2", 2, "二", 2, "貳", 2, "贰", 2, "兩", 2, "两", 2, 
	      "３", 3, "3", 3, "三", 3, "參", 3, "叄", 3, "叁", 3, 
	      "４", 4, "4", 4, "四", 4, "肆", 4,
	      "５", 5, "5", 5, "五", 5, "伍", 5,
	      "６", 6, "6", 6, "六", 6, "陸", 6, "陆", 6,
	      "７", 7, "7", 7, "七", 7, "柒", 7,
	      "８", 8, "8", 8, "八", 8, "捌", 8,
	      "９", 9, "9", 9, "九", 9, "玖", 9); 

my @beforeWan = ("十", "百", "千"); 

my %beforeWan = ("十", 10, "拾", 10,
		 "百", 100, "佰", 100,
		 "千", 1000, "仟", 1000); 


my @afterWan = ("", "萬", "億", "兆", "京"); 

my %afterWan = ("萬", 10000, "万", 10000,
		"億", 100000000, 	"亿", 100000000,
		"兆", 1000000000000,
		"京", 10000000000000000); 


my $ALTTWO = "兩";
my $TEN = 10;

my %trad2simp = ("負" => "负", 
		 "點" => "点",
		 "零" => "零", 
		 "一" => "一",
		 "二" => "二",
		 "三" => "三", 
		 "四" => "四",
		 "五" => "五",
		 "六" => "六",
		 "七" => "七",
		 "八" => "八",
		 "九" => "九",
		 "十" => "十",
		 "百" => "百",
		 "千" => "千", 
		 "萬" => "万",
		 "億" => "亿",
		 "兆" => "兆", 
		 "兩" => "两",
		 "點" => "点");


my %trad2formal = ("負" => "負", 
		   "點" => "點",
		   "零" => "零", 
		   "一" => "壹",
		   "二" => "貳",
		   "三" => "參", 
		   "四" => "肆",
		   "五" => "伍",
		   "六" => "陸",
		   "七" => "柒",
		   "八" => "捌",
		   "九" => "玖",
		   "十" => "拾",
		   "百" => "佰",
		   "千" => "仟", 
		   "萬" => "萬",
		   "億" => "億",
		   "兆" => "兆", 
		   "兩" => "兩",
		   "點" => "點");


my %trad2formalsimp = ("負" => "负", 
		       "點" => "点",
		       "零" => "零", 
		       "一" => "壹",
		       "二" => "贰",
		       "三" => "叁", 
		       "四" => "肆",
		       "五" => "伍",
		       "六" => "陆",
		       "七" => "柒",
		       "八" => "捌",
		       "九" => "玖",
		       "十" => "拾",
		       "百" => "佰",
		       "千" => "仟", 
		       "萬" => "万",
		       "億" => "亿",
		       "兆" => "兆", 
		       "兩" => "两");



my %trad2pinyin = ("負" => "fu4", 
		   "點" => "dian3",
		   "零" => "ling2", 
		   "一" => "yi1",
		   "二" => "er4",
		   "三" => "san1", 
		   "四" => "si4",
		   "五" => "wu3",
		   "六" => "liu4",
		   "七" => "qi1",
		   "八" => "ba1",
		   "九" => "jiu3",
		   "十" => "shi2",
		   "百" => "bai3",
		   "千" => "qian1", 
		   "萬" => "wan4",
		   "億" => "yi4",
		   "兆" => "zhao4", 
		   "兩" => "liang3");

my %trad2yalecant = ("負" => "fu", 
		     "點" => "dim2",
		     "零" => "ling2", 
		     "一" => "yat",
		     "二" => "yih7",
		     "三" => "saam1", 
		     "四" => "sei5",
		     "五" => "ng4",
		     "六" => "luhk",
		     "七" => "chat1",
		     "八" => "baat1",
		     "九" => "gao3",
		     "十" => "sap7",
		     "百" => "baak5",
		     "千" => "chin1", 
		     "萬" => "maahn",
		     "億" => "yik1",
		     "兆" => "siu", 
		     "兩" => "leung4");


my %trad2jyutpin = ("負" => "fu6", 
		    "點" => "dim4",
		    "零" => "ling4", 
		    "一" => "jat1",
		    "二" => "ji6",
		    "三" => "saam1", 
		    "四" => "sei3",
		    "五" => "ng5",
		    "六" => "luk6",
		    "七" => "cat1",
		    "八" => "baat3",
		    "九" => "gau2",
		    "十" => "sap6",
		    "百" => "baak3",
		    "千" => "cin1", 
		    "萬" => "maan6",
		    "億" => "jik1",
		    "兆" => "siu6", 
		    "兩" => "loeng5");


sub new {
    return bless {};
}


# The heart of the program.  Does the actual conversion

sub EnglishToChineseNumber {
    my($self) = shift;
    my($enumber) = shift;
    my($outputtype) = shift;

    if ($outputtype eq "") {
	$outputtype = $default_outputtype;
    }

    $outputtype = lc($outputtype);

#    print "Output type : $outputtype\n";

    my(@powers) = ();
    my($power) = 0;
    my($value) = 0;
    my($negative) = 0;     # is it a negative integer?
    my($inzero) = 0;       # are we in a stretch or 1 or more zeros (only add one zero for the stretch)
    my($canaddzero) = 0;   # only add a zero if there's something non-zero on both sides of it
    my($cnumber) = "";     # the final result
    my($remainder) = "";

    # Remove all non-digits

    $enumber =~ s/[^0-9\.-]//g;

    # If zero, just return zero
    if ($enumber == 0) {
	return $digits[0];
    }

    # Check if it's negative, set the negative flag and make it positive
    if ($enumber < 0) {
	$negative = 1;
	$enumber = -$enumber;
    }

    if ($enumber =~ m/([0-9]*)\.([0-9]+)/) {
	$remainder = $2;
	$enumber = $1;
    }

    # Get the value of the coefficient for each power of ten
    while ($TEN ** $power <= $enumber) {
	$value = ($enumber % ($TEN** ($power+1)))/($TEN**$power);
	$powers[$power] = $value;

	# Subtract out the current power's coefficient and increase the power
	$enumber -= $enumber % ($TEN**($power+1));
	$power++;
    }

    my($i);

    # Take the decomposition of the number for above and generate the Chinese equivalent
    for ($i = 0; $i < $power; $i++) {
	#System.out.println("10^" + i + ":\t" + powers[i]);
	if (($i % 4) == 0) {  # Reached the next four powers up level
	    if ($powers[$i] != 0) {
		$inzero = 0;
		$canaddzero = 1;
		$cnumber =  $digits[$powers[$i]] . $afterWan[$i/4] . $cnumber;
	    } else {
		# Check that something in the next three powers is non-zero before adding 
		if ((($i+3 < $power) && $powers[$i+3] != 0) ||
		    (($i+2 < $power) && $powers[$i+2] != 0) ||
		    (($i+1 < $power) && $powers[$i+1] != 0)) 
		{
		    $cnumber = $afterWan[$i/4] . $cnumber;
		    $canaddzero = 0; # added
		}
	    }
	} else {  # Add one, tens, hundreds, or thousands place for each level
	    if ($powers[$i] != 0) {
		$inzero = 0;
		$canaddzero = 1;
		if ($power == 2 && $i == 1 && $powers[$i] == 1) {  # No 一 with 10 through 19
		    $cnumber = $beforeWan[($i % 4)-1] . $cnumber;
		    #} else if ((i%4 = 3) && powers[i] == 2) {  # when to use liang3 vs. er4
		    #cnumber.insert(0, ALTTWO + beforeWan[(i%4)-1]);
		} else {
		    $cnumber = $digits[$powers[$i]] . $beforeWan[($i%4)-1] . $cnumber;
		}
	    } else {
		if ($canaddzero == 1 && $inzero == 0) { # Only insert one 零 for all consecutive zeroes
		    $inzero = 1;
		    $cnumber = $digits[$powers[$i]] . $cnumber;
		}
	    }
	}
    }

    if ($remainder ne "") {
	$cnumber .= $DECIMAL;
	for ($i = 0; $i < length($remainder); $i++) {
	    $cnumber .= $digits[substr($remainder, $i, 1)];
	}
    }

    # Add the negative character
    if ($negative == 1) {
	$cnumber = $MINUS . $cnumber;
    }
 
    my($result, $j);
    if ($outputtype eq "trad") {
	$result = $cnumber;
    } elsif ($outputtype eq "simp") {
	for ($j = 0; $j < lengthu8($cnumber); $j++) {
	    $result .= $trad2simp{substru8($cnumber, $j, 1)};
	}
    } elsif ($outputtype eq "formaltrad") {
	for ($j = 0; $j < lengthu8($cnumber); $j++) {
	    $result .= $trad2formal{substru8($cnumber, $j, 1)};
	}

    } elsif ($outputtype eq "formalsimp") {
	for ($j = 0; $j < lengthu8($cnumber); $j++) {
	    $result .= $trad2formalsimp{substru8($cnumber, $j, 1)};
	}

    } elsif ($outputtype eq "pinyin") {
	for ($j = 0; $j < lengthu8($cnumber); $j++) {
	    $result .= $trad2pinyin{substru8($cnumber, $j, 1)} . " ";
	}

    } elsif ($outputtype eq "jyutpin") {
	for ($j = 0; $j < lengthu8($cnumber); $j++) {
	    $result .= $trad2jyutpin{substru8($cnumber, $j, 1)} . " ";
	}

    } elsif ($outputtype eq "yalecant") {
	for ($j = 0; $j < lengthu8($cnumber); $j++) {
	    $result .= $trad2yalecant{substru8($cnumber, $j, 1)} . " ";
	}

    } else {
	$result = $cnumber;
    }

    return $result;
}


sub ChineseToEnglishNumber {
    my($self) = shift;
    my($cnumber) = shift;
    my($outputtype) = shift;

    if ($outputtype eq "") {
	$outputtype = $default_englishtype;
    }

    $outputtype = lc($outputtype);

    my($i, $j, $result);

    my($alldigits) = 1;

    my($ordinal) = 0;

    if ($cnumber =~ m/^第/) {
	$ordinal = 1;
    }

    if ($cnumber =~ m/分之/) {
	my($denom) = ($cnumber =~ m/^(.+?)分之/);
	my($numer) = ($cnumber =~ m/分之(.+)$/);
	$result = &ChineseToEnglishFull($numer)/&ChineseToEnglishFull($denom);

    } elsif (lengthu8($cnumber) > 1) {
	for ($i = 0; $i < lengthu8($cnumber); $i++) {
	    if (!defined($digits{substru8($cnumber, $i, 1)})) {
		$alldigits = 0;
	    }
	}

	if ($alldigits == 1) {
	    $result = &ChineseToEnglishBrief($cnumber);
	} else {
	    $result = &ChineseToEnglishFull($cnumber);
	}

    } else {
	$result = &ChineseToEnglishFull($cnumber);
    }

    if ($outputtype eq "arabic") {
	if ($ordinal) {
	    my($lastdigit) = substru8($result, lengthu8($result)-1, 1);
	    if ($lastdigit eq "1") {
		$result .= "st";
	    } elsif ($lastdigit eq "2") {
		$result .= "nd";
	    } elsif ($lastdigit eq "3") {
		$result .= "rd";
	    } else {
		$result .= "th";
	    }
	}

	return $result;

    } elsif ($outputtype eq "comma") {
	my $withcomma = "" . $result;
	my $start; 
	if ($withcomma =~ m/\./) {
	    
	} else {
	    $start = (lengthu8($withcomma) % 3);
	    for ($i = $start; lengthu8($withcomma) > 3 and $i < lengthu8($withcomma); $i+=3) {
		if ($i != 0) {
		    substr($withcomma, $i, 0, ",");
		    $i++;
		}
	    }
	}

	if ($ordinal) {
	    my($lastdigit) = substru8($withcomma, lengthu8($withcomma)-1, 1);
	    if ($lastdigit eq "1") {
		$withcomma .= "st";
	    } elsif ($lastdigit eq "2") {
		$withcomma .= "nd";
	    } elsif ($lastdigit eq "3") {
		$withcomma .= "rd";
	    } else {
		$withcomma .= "th";
	    }
	}

	return $withcomma;


    } elsif ($outputtype eq "words") {
	if ($ordinal) {
	    return num2en_ordinal($result);
	} else {
	    return num2en($result);
	}
    }

    
}


sub ChineseToEnglishBrief {
    my($cnumber) = shift;
    my($nextcchar);

    my($place, $digitval, $total) = (0,0,0);

    for ($place = 0; $place < lengthu8($cnumber); $place++) {
	$total *= 10;
	$digitval = $digits{substru8($cnumber, $place, 1)};
	$total += $digitval;
    }

    return $total;
}



sub ChineseToEnglishFull {
    my($cnumber) = shift;
    my($negative) = 0;
    my($cnumlength);
    my($i);
    my($j, $digitval, $cchar, $afterdecimal);
    my($power) = 0;
    my($leveltotal) = 0;
    my($total) = 0;
    my($nextcchar);

    $afterdecimal = 0;

    # simplified 万亿 ==> trad 萬億
    #    order doesn't matter
    # times 
    #  万=10000
    # 650 450 4057 says:
    # 亿=100000000
    # 650 450 4057 says:
    # 億=100000000
    # 650 450 4057 says:
    # 萬=10000
    #
    $cnumber =~ s/万亿/兆/;
    $cnumber =~ s/萬億/兆/;
    $cnumber =~ s/亿万/兆/;
    $cnumber =~ s/億萬/兆/;
    $cnumber =~ s/個//;
    $cnumber =~ s/个//;
    $cnumber =~ s/廿/二十/;
    $cnumber =~ s/卄/二十/;
    $cnumber =~ s/卅/三十/;
    $cnumber =~ s/卌/四十/;

    $cnumlength = lengthu8($cnumber);

    #print "In Chinese to English Full<BR>";

    for ($i = 0; $i < $cnumlength; $i++) {
	#print "i $i ";
	$cchar = substru8($cnumber, $i, 1);
	#print "$cchar $leveltotal $power";
	if ($i == 0 && ($cchar eq "负" or $cchar eq '負' or $cchar eq '-')) {
	    $negative = 1;
	} elsif ($i == 0 && $cchar eq '第') { # ordinal
	    # Do nothing, handled elsewhere

	} elsif ($cchar eq '點' or $cchar eq '点' or $cchar eq '.' or
	         $cchar eq '．') {
	    $afterdecimal = 1;
	    $power = -1;

	} elsif ($cchar eq '兆') {
	    $power = 12;
	    $leveltotal = 1 if $leveltotal == 0;
	    $total += $leveltotal * (10 ** $power);
	    $leveltotal = 0;
	    $power -= 4;

	} elsif ($cchar eq '億' or $cchar eq '亿') {
	    $power = 8;
	    $leveltotal = 1 if $leveltotal == 0;
	    $total += $leveltotal * (10** $power);
	    $leveltotal = 0;
	    $power -= 4;

	} elsif ($cchar eq '萬' or $cchar eq '万') {
	    $power = 4;
	    $leveltotal = 1 if $leveltotal == 0;
	    $total += $leveltotal * (10**$power);
	    $leveltotal = 0;
	    $power -= 4;

	} elsif ($cchar eq '千' or $cchar eq '仟') {
	    $leveltotal += 1000;

	} elsif ($cchar eq "百" or $cchar eq '佰') {
	    $leveltotal += 100;

	} elsif ($cchar eq "十" or $cchar eq '拾') {
	    $leveltotal += 10;

	} elsif ($cchar eq "零" or $cchar eq "〇" or
	         $cchar eq "0" or $cchar eq "０") {
	    $power = 0;

	} elsif (defined($digits{$cchar})) {

	    $digitval = $digits{$cchar};
	    #print "Digit val is $digitval, $i, $cnumlength\n";
	    if ($afterdecimal) {
		$leveltotal += $digitval * (10**$power);
		$power--;

		while ($i+1 < $cnumlength and defined($digits{substru8($cnumber, $i+1, 1)})) {
		    $leveltotal += $digits{substru8($cnumber, $i+1, 1)} * (10**$power);
		    $power--;
		    $i++;
		}

	    
	    } elsif ($i+1 < $cnumlength) {
		$nextcchar = substru8($cnumber, $i+1, 1);

		if ($nextcchar eq "十" or $nextcchar eq "拾") {
		    $leveltotal += $digitval * 10;
		    $i++;

		} elsif ($nextcchar eq "百" or $nextcchar eq "佰") {
		    $leveltotal += $digitval * 100;
		    $i++;

		} elsif ($nextcchar eq "千" or $nextcchar eq "仟") {
		    $leveltotal += $digitval * 1000;
		    $i++;

		} elsif (defined($digits{$nextcchar})) {
		    $leveltotal *= 10;
		    $leveltotal += $digitval;

		    while ($i+1 < $cnumlength and defined($digits{substru8($cnumber, $i+1, 1)})) {
			$leveltotal *= 10;
			$leveltotal += $digits{substru8($cnumber, $i+1, 1)};
			$i++;
		    }

		} else {
		    $leveltotal += $digitval;
		}

	    } else {
		if ($i+1 == $cnumlength and $i > 0) {
		    my $prevchar = substru8($cnumber, $i-1, 1);
		    if ($prevchar eq '兆') {
			$leveltotal += $digitval * (10**11);

		    } elsif ($prevchar eq '億' or $prevchar eq '亿') {
			$leveltotal += $digitval * (10**7);

		    } elsif ($prevchar eq '萬' or $prevchar eq '万') {
			$leveltotal += $digitval * 1000;
			
		    } elsif ($prevchar eq '千' or $prevchar eq '仟') {
			$leveltotal += $digitval * 100;
			
		    } elsif ($prevchar eq "百" or $prevchar eq '佰') {
			$leveltotal += $digitval * 10;

		    } else {
			$leveltotal += $digitval;
		    }

		} else {
		    $leveltotal += $digitval;
		}
		#print "digit $digitval\n";
	    }

	} else {
	    print STDERR "Seems to be an error in the number. $cnumber\n";
	    return "";

	    # return negative infinity;
	}
    }


    # Catch remaining leveltotal
    #print("Level total " + $leveltotal + " power " + $power + " ten to power " + (10**$power)/10);

    $total += $leveltotal; # * 10** $power;

    #if ($cchar eq '點' or $cchar eq '点' or $cchar eq '.') {
    #$power = -1;
    #for ($j = $i+1; $j < $cnumlength; $j++, $power--) {
    #$digitval = $digits{substru8($cnumber, $j, 1)};
    #$total += $digitval * (10 ** $power);
    # }
    #}
  

    if ($negative == 1) { $total = -$total; }

    return $total;
}



sub chinese_output {
    my($self) = shift;
    if (@_) { $default_outputtype = shift }
    return $default_outputtype;
}

sub english_output {
    my($self) = shift;
    if (@_) { $default_englishtype = shift }
    return $default_englishtype;
}


sub lengthu8 {
    my($utfstring) = shift;
    my($i, $charcount, $byte1);
    return length($utfstring);

    $i = 0; $charcount = 0;
    while ($i < length($utfstring)) {
	#print "i $i $utfstring\n";
	$byte1 = substr($utfstring, $i, 1);
	if (unpack("C", $byte1) <= 0x7F) { # 1 byte long (ASCII)
	    $i++;
	    $charcount++;
	} elsif ((unpack("C", $byte1) & 0xE0) == 0xC0) { # 2 bytes long
	    $i += 2;
	    $charcount++;
	} else {  # 3 bytes long
	    $i += 3;
	    $charcount++;
	}
    }
    return $charcount;
}

sub substru8 {
    my($utfstring, $start, $span) = @_;
    my($i, $charcount, $bytestart, $bytespan, $byte1);
    #print "$utfstring START $start SPAN $span\n";
    return substr($utfstring, $start, $span);

    $i = 0; $charcount = 0;
    while ($i < length($utfstring)) {
	if ($charcount == $start) { $bytestart = $i; }
	if ($charcount == ($start+$span)) { $bytespan = $i - $bytestart; }
	$byte1 = substr($utfstring, $i, 1);
	if (unpack("C", $byte1) <= 0x7F) { # 1 byte long (ASCII)
	    $i++;
	    $charcount++;
	} elsif ((unpack("C", $byte1) & 0xE0) == 0xC0) { # 2 bytes long
	    $i += 2;
	    $charcount++;
	} else {  # 3 bytes long
	    $i += 3;
	    $charcount++;
	}
    }

    if ($charcount == ($start+$span)) { $bytespan = $i - $bytestart; }
    #print "bytestart $bytestart bytespan $bytespan\n";
    return substr($utfstring, $bytestart, $bytespan);

}


END { }

1;

