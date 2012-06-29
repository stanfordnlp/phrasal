#!/usr/bin/perl -w
#
# Copyright 2008 Google Inc.
# All Rigthts Reserved.
# Author: Alex Franz (alex@google.com)
#
# Tokenizer that is compatible with n-gram data.
#
# This tokenizer requires the --datafile flag to point it to
# tokenizer.data.  For example, --datafile=mydir/tokenizer.data #
#
# It also requires a language argument to tell it which language we
# are tokenizing.  For example, --language=SPANISH.  The language
# identifier must match what is used in the tokenizer.data file.  It
# is used to look up abbreviations (for avoiding spurious
# end-of-sentence), and word & character mappings (for normalizing the
# input text).
#
# If you provide the -debug flag, you will see a detailed trace of the
# normalization and tokenization process.
#
# Example usage:
#
# $ tokenizer.pl --datafile=mydir/tokenizer.data --language=SPANISH 
#   < infile > outfile

use strict;

# decode/encode UTF-8
use Encode;

# decode HTML character entities
use HTML::Entities;

# input & output are in UTF-8
binmode STDIN, ":utf8";
binmode STDOUT, ":utf8";

# command line processing
use Getopt::Long;

#----------------------------------------------------------------------
# global constants

my $MAX_WORD_LEN = 32;

my $MAX_NUMBER_LEN = 16;

# these characters represent potential sentence breaks
my $EOS_CHARS = "[.!?;]";


#----------------------------------------------------------------------
# global vars

# default path to tokenizer data file
my $g_datafile = "tokenizer.data";

# if true, then print debugging output
my $g_debug = 0;

# language of text to be tokenized (SPANISH, etc.)
my $g_language = "UNKNOWN_LANGUAGE";

# Map from language name (e.g. ENGLISH) to a set of
# abbreviations in that language. Example: "Mr." in English.

my %g_abbrevs = ();

# Map from language name (e.g. FRENCH) to a set of
# contraction prefixes in that language. Example: "l'" in French.

my %g_contraction_prefixes = ();

# Map from language name (e.g. ENGLISH) to a character map.
# The character map is a map from characters to their "normalized"
# replacements, which can be any string. Example: Map Unicode
# ideographic space to ASCII space.

my %g_char_maps = ();

# Map from language name (e.g. ENGLISH) to a word map.  The
# word map is a map from words to their "normalized" replacements,
# which can be any string. Example: Map "can't" to "can n't".

my %g_word_maps = ();


#----------------------------------------------------------------------
# subroutine prototypes

sub main();

# print usage example
sub usage();

# read in the datafiles
sub read_datafile();

# print out the data tables - for debugging
sub print_data_tables();

# tokenize from stdin to stdout
sub tokenize( $ );

# note that we have found a word
sub found_word( $$$ );

# note that we have found a symbol
sub found_symbol( $$ );

# note that we have found a number
sub found_number( $$ );

# emit token; keeps track of spaces & newlines
sub emit_token( $$ );

# check whether word is an abbreviation in given language
sub is_abbrev( $$ );

# check whether word is a contraction prefix in given language
sub is_contraction_prefix( $$ );

# check whether word has a mapping in given language, and return it
sub has_word_mapping( $$ );

# check whether char has a mapping in given language, and return it
sub has_char_mapping( $$ );

# unescape \x hex characters
sub unescape_hex( $ );

#----------------------------------------------------------------------
# main
#
# Read in data file. Read text from stdin, and print tokenized text to
# stdout.

main();

sub main() {
  my $ret = Getopt::Long::GetOptions("datafile=s" => \$g_datafile,
                                     "language=s" => \$g_language,
                                     "debug" => \$g_debug);

  if ($ret == 0) {
    usage();
    exit;
  }
  read_datafile();
  if ($g_debug) {
    print_data_tables();
  }

  tokenize($g_language);
}

#----------------------------------------------------------------------
# read in the datafile

sub read_datafile() {
  if (not open(DATAFILE, "<", $g_datafile)) {
    print "Can't open datafile $g_datafile\n";
    usage();
    exit(1);
  }
  while (<DATAFILE>) {
    next if /^\s*#/;
    next if /^\s*$/;
    chomp;
    my @fields = split(/\t/, $_);
    ($#fields >= 2 && $#fields <= 4) or
      die "Malformed line in datafile " . $g_datafile . " " . $_;
    my $lang = $fields[0];
    my $op = $fields[1];
    # unescape the value fields
    my $value1 = unescape_hex($fields[2]);
    my $value2 = "";
    if (@fields > 3) {
      $value2 = unescape_hex($fields[3]);
    }

    if ($op =~ m/abbrev/i) {
      # store abbreviation
      $g_abbrevs{$lang}->{$value1} = 1;
    } elsif ($op =~ m/contraction_prefix/i) {
      # store contraction prefix
      $g_contraction_prefixes{$lang}->{$value1} = 1;
    } elsif ($op =~ m/char_map/i) {
      # store character mapping
      $g_char_maps{$lang}->{$value1} = $value2;
    } elsif ($op =~ m/word_map/i) {
      # store word mapping
      $g_word_maps{$lang}->{$value1} = $value2;
    } else {
      print "Malformed line in datafile: ", join("\t", @fields), "\n";
      exit(1);
    }
  }
}

#----------------------------------------------------------------------
# Our definitions of the Unicode character ranges that make up
# different kinds of tokens.

# basic latin letters

sub InMyBasicLatinLetter {
  return <<END;
0041\t005A
0061\t007A
END
}

# ASCII digits

sub InMyDigit {
  return <<END;
0030\t0039
END
}

# letters from Latin-1 supplement

sub InMyLatin1Letter {
  return <<END;
00C0\t00D6
00D8\t00F6
00F8\t00FF
END
}

# letters from Latin Extended-A

sub InMyLatinExtendedALetter {
  return <<END;
0100\t017F
END
}

# letters from Latin Extended-B

sub InMyLatinExtendedBLetter {
  return <<END;
0180\t01BF
01C4\t01F5
01FA\t0217
END
}

# combining diacritical marks

sub InMyCombiningDiacriticalMark {
  return <<END;
0300\t0345
0360\t0361
END
}

# letters from Latin Extended Additional

sub InMyLatinExtendedAdditionalLetter {
  return <<END;
1E00\t1E9B
1EA0\t1EF9
END
}

# all latin letters

sub InMyLatinLetter {
  return <<END;
+main::InMyBasicLatinLetter
+main::InMyLatin1Letter
+main::InMyLatinExtendedALetter
+main::InMyLatinExtendedBLetter
+main::InMyCombiningDiacriticalMark
+main::InMyLatinExtendedAdditionalLetter
END
}

# latin letters and digits
sub InMyLatinAlnum {
  return <<END;
+main::InMyLatinLetter
+main::InMyDigit
END
}

# greek letters from greek and greek extended

sub InMyGreekLetter {
  return <<END;
037A
0384\t0386
0388\t038A
038C
038E\t03A1
03A3\t03CE
03D0\t03D7
03DB
03F0\t03F2
03F4\t03F5
03F9
1F00\t1FFE
END
}

# greek letters and digits

sub InMyGreekAlnum {
  return <<END;
+main::InMyGreekLetter
+main::InMyDigit
END
}

# cyrillic letters

sub InMyCyrillicLetter {
  return <<END;
0401\t040C
040E\t044F
0451\t045C
045E\t0481
0483\t0486
0490\t04C4
04C7\t04C8
04CB\t04CC
04D0\t04EB
04EE\t04F5
04F8\t04F9
END
}

# cyrillic letters and digits

sub InMyCyrillicAlnum {
  return <<END;
+main::InMyCyrillicLetter
+main::InMyDigit
END
}

# hebrew letters

sub InMyHebrewLetter {
  return <<END;
0590\t05FF
END
}

# hebrew letters and digits

sub InMyHebrewAlnum {
  return <<END;
+main::InMyHebrewLetter
+main::InMyDigit
END
}

# punctuation characters:
#
# 0022 - 002D " # $ % & ' ( ) * + , -
# 002F        /
# 003A - 0040 : ; < = > ? @
# 005B - 0060 [ \ ] ^ _ `
# 007B - 007E { | } ~
# 00A1 - 00BF Latin-1 Punctuation signs
# 00D7        Multiplication sign
# 00F7        Division sign
# 2018 - 201F Quotes from General Punctuation
# 2039 - 203A Angle brackets from General Punctuation
# 203C        Double-! from General Punctuation


sub InMyPunctuation {
  return <<END;
0022\t002D
002F
003A\t0040
005B\t0060
007B\t007E
00A1\t00BF
00D7
00F7
2018\t201F
2039\t203A
203C
END
}


#----------------------------------------------------------------------
# tokenize from stdin to stdout

# tokenize() takes the language as argument and reads from <>.
# it returns the tokenized output: spaces between tokens,
# newlines between sentences.

sub tokenize($) {
  my ($language) = @_;

  my $output = "";

  LINE: while (my $input = <>) {

    # apply character mappings for this language.
    # we loop over the characters in the string, and
    # check whether there is a substitution defined
    # using char_map in the tokenizer datafile.

    for (my $i = 0; $i < length($input); $i++) {
      my $char = substr($input, $i, 1);
      if (has_char_mapping(\$char, $language)) {
        if ($g_debug) {
          print("Applying char_map " . substr($input, $i, 1) . " ==> $char\n");
        }
        substr($input, $i, 1) = $char;
      }
    }

    if ($input =~ /^(\s*)$/) {
      if ($g_debug) {
        print("Found an empty line; starting new sentence: $1\n");
      }
      found_word("\n", $language, \$output);
      next LINE;
    }

    # reset \G to the initial position of the line
    pos($input) = 0;

    while (pos($input) < length($input)) {
      if ($input =~ /\G(<\/?\p{InMyBasicLatinLetter}[^<>]{0,512}\>)/gc) {
        if ($g_debug) {
          print("Found XML tag; discarding: $1\n");
        }

      } elsif ($input =~ /\G((([\p{InMyPunctuation}]|$EOS_CHARS) ?){5,})/gc) {
        if ($g_debug) {
          print("Found five or more punctuation characters; discarding: $1\n");
        }

      } elsif ($input =~ /\G(\p{InMyDigit}{3}[ \.-]
                             \p{InMyDigit}{2}[ \.-]
                             \p{InMyDigit}{4})/gcx) {
        if ($g_debug) {
          print("Found long number sequence: $1\n");
        }
        found_symbol($1, \$output);

      } elsif ($input =~ /\G(\p{InMyDigit}{4}[ \.-]
                             \p{InMyDigit}{4}[ \.-]
                             \p{InMyDigit}{4}[ \.-]
                             \p{InMyDigit}{3,4})/gcx) {
        if ($g_debug) {
          print("Found long number sequence: $1\n");
        }
        found_symbol($1, \$output);

      } elsif ($input =~ /\G(\p{InMyDigit}{4}[ \.-]
                             \p{InMyDigit}{6}[ \.-]
                             \p{InMyDigit}{5})/gcx) {
        if ($g_debug) {
          print("Found long number sequence: $1\n");
        }
        found_symbol($1, \$output);

      } elsif ($input =~ /\G((\+?\p{InMyDigit}{1,3}[ \-\/])?
                             \(?\p{InMyDigit}{2,5}\)?[ \-]
                             \p{InMyDigit}{3,4}[ \-]
                             \p{InMyDigit}{3,4})/gcx) {
        if ($g_debug) {
          print("Found telephone number (will separate parens): $1\n");
        }
        # separate any parens with space
        my $word = $1;
        $word =~ s/(\S)([\(\)])/$1 $2/g;
        $word =~ s/([\(\)])(\S)/$1 $2/g;
        found_symbol($word, \$output);

      } elsif ($input =~ /\G((1[ \.-])
                             \(?\p{InMyDigit}{3}\)?
                             [ \.-](\p{InMyDigit}|-|[A-Z]){7,10})/gcx) {
        if ($g_debug) {
          print("Found telephone number with phone word (will separate parens): $1\n");
        }
        # separate any parens with space
        my $word = $1;
        $word =~ s/(\S)([\(\)])/$1 $2/g;
        $word =~ s/([\(\)])(\S)/$1 $2/g;
        found_symbol($word, \$output);

      } elsif ($input =~ /\G(\p{InMyDigit}{1,3}\.
                             \p{InMyDigit}{1,3}\.
                             \p{InMyDigit}{1,3}\.
                             \p{InMyDigit}{1,3})/gcx) {
        if ($g_debug) {
          print("Found IP address: $1\n");
        }
        found_symbol($1, \$output);

      } elsif ($input =~ /\G(\p{InMyDigit}+
                             (-\p{InMyDigit}*)+)(?=\s)/gcx) {
        if ($g_debug) {
          print("Found numbers connected by hyphen(s) (will separate at hyphens): $1\n");
        }
        # separate hyphens with space
        my $word = $1;
        $word =~ s/([^\-]*)-([^\-]*)/$1 - $2/g;
        # remove any final spaces
        $word =~ s/\s+$//;
        found_symbol($word, \$output);

      } elsif ($input =~ /\G([\p{InMyBasicLatinLetter}\p{InMyDigit}]
                             [\p{InMyBasicLatinLetter}\p{InMyDigit}._\-]*
                             \@
                             [\p{InMyBasicLatinLetter}\p{InMyDigit}]
                             [\p{InMyBasicLatinLetter}\p{InMyDigit}._\-]*
                             (\.[\p{InMyBasicLatinLetter}\p{InMyDigit}._\-]*)+)/gcix) {
        if ($g_debug) {
          print("Found Email address: $1\n");
        }
        found_symbol($1, \$output);

      } elsif ($input =~ /\G(((shttp|http|ftp)\:\/{0,2})?
                             [\p{InMyBasicLatinLetter}\p{InMyDigit}\-]+
                             (\.[\p{InMyBasicLatinLetter}\p{InMyDigit}\-]+)*
                             \.\p{InMyBasicLatinLetter}{2,4}
                             (:[0-9]+)?
                             (\/|\?)?
                             [\p{InMyBasicLatinLetter}\p{InMyDigit}\-\$_\+\!\*\'\(\),;\/\?\:\@=&\.]*)/gcix) {
        if ($g_debug) {
          print("Found URL: $1\n");
        }
        found_symbol($1, \$output);

      } elsif ($input =~ /\G(\p{InMyDigit}{1,3})\.(?!\p{InMyDigit})/gc) {
        # number less than 1000 ending in period
        # note we don't include the period in the saved match
        my $number = $1;
        if ($language =~ /GERMAN|BOSNIAN|CROATION|DANISH|FINNISH|HINDI|
            HUNGARIAN|ICELANDIC|NORWEGIAN|POLISH|SERBIAN|SLOVAK|SLOVENIANK|
            TURKISH/ix) {
          # add the period to the number
          $number = $number . ".";
          if ($g_debug) {
            print("Found ordinal number with period: $number\n");
          }
          found_number($number, \$output);
        } else {
          if ($g_debug) {
            print("Found number: $number\n");
          }
          found_number($number, \$output);
          # back up over the period
          pos($input) = pos($input) - 1;
        }

      } elsif ($input =~ /\G((\p{InMyLatinLetter}|
                              \p{InMyGreekLetter}|
                              \p{InMyCyrillicLetter}|
                              \p{InMyHebrewLetter})\.)/gcx) {
        if ($g_debug) {
          print("Found single letter with period; " .
                "treating as abbreviation: $1\n");
        }
        found_word($1, $language, \$output);

      } elsif ($input =~ /\G(\p{InMyLatinLetter}+\'s)/gci) {
        my $word = substr($1, 0, length($1) - 2);
        if ($g_debug) {
          print("Found word: $word\n");
        }
        found_word($word, $language, \$output);
        if ($g_debug) {
          print("Found word: 's\n");
        }
        found_word("'s", $language, \$output);

      } elsif ($input =~ /\G(\p{InMyLatinLetter}
                             ([\p{InMyLatinLetter}\-]*\')+
                             [\p{InMyLatinLetter}\-]+)/gcx) {
        # word with apostrophe; check whether it should be split
        my $word = $1;
        $word =~ /(^[\p{InMyLatinLetter}\-]*\')(.*)$/;
        my $contraction_prefix = $1;
        my $remainder = $2;
        if (is_contraction_prefix($contraction_prefix, $language)) {
          if ($g_debug) {
            print("Found contraction prefix: $contraction_prefix\n");
          }
          found_word($contraction_prefix, $language, \$output);
          if ($g_debug) {
            print("Found word: $remainder\n");
          }
          found_word($remainder, $language, \$output);
        }  else {
          if ($g_debug) {
            print("Found word: $word\n");
          }
          found_word($word, $language, \$output);
        }

      } elsif ($input =~ /\G(\.?((\p{InMyLatinAlnum}-?)*
                                 \p{InMyLatinLetter}
                                 [\p{InMyLatinAlnum}\-]*\.?)+
                             ([\p{InMyLatinAlnum}\-]+\.?)*)/gcx) {
        my $word = $1;
        if ($word =~ /\.$/) {
          # word ends in period; look up whether it's an abbreviation, 
          # or an end of sentence
          if (is_abbrev($word, $language)) {
            if ($g_debug) {
              print("Found latin-script word ending in period; " .
                    "found in abbreviations table: $word\n");
            }
            found_word($word, $language, \$output);
          } else {
            my $prefix = substr($word, 0, length($word) - 1);
            if ($g_debug) {
              print("Found latin-script word: $prefix\n");
            }
            found_word($prefix, $language, \$output);
            # back up over the period
            pos($input) = pos($input) - 1;
          }
        } else {
          # word does not end in period
          if ($g_debug) {
            print("Found latin-script word: $1\n");
          }
          found_word($1, $language, \$output);
        }

      } elsif ($input =~ /\G(\.?((\p{InMyGreekAlnum}-?)*
                                 \p{InMyGreekLetter}
                                 [\p{InMyGreekAlnum}\-]*\.?)+
                             ([\p{InMyGreekAlnum}\-]+\.?)*)/gcx) {
        my $word = $1;
        if ($word =~ /\.$/) {
          # word ends in period; look up whether it's an abbreviation, 
          # or an end of sentence
          if (is_abbrev($word, $language)) {
            if ($g_debug) {
              print("Found Greek word ending in period; " .
                    "found in abbreviations table: $word\n");
            }
            found_word($word, $language, \$output);
          } else {
            my $prefix = substr($word, 0, length($word) - 1);
            if ($g_debug) {
              print("Found Greek word: $prefix\n");
            }
            found_word($prefix, $language, \$output);
            # back up over the period
            pos($input) = pos($input) - 1;
          }
        } else {
          # word does not end in period
          if ($g_debug) {
            print("Found Greek word: $1\n");
          }
          found_word($1, $language, \$output);
        }

      } elsif ($input =~ /\G(\.?((\p{InMyCyrillicAlnum}-?)*
                                 \p{InMyCyrillicLetter}
                                 [\p{InMyCyrillicAlnum}\-]*\.?)+
                             ([\p{InMyCyrillicAlnum}\-]+\.?)*)/gcx) {
        my $word = $1;
        if ($word =~ /\.$/) {
          # word ends in period; look up whether it's an abbreviation,
          # or an end of sentence
          if (is_abbrev($word, $language)) {
            if ($g_debug) {
              print("Found Cyrillic word ending in period; " .
                    "found in abbreviations table: $word\n");
            }
            found_word($word, $language, \$output);
          } else {
            my $prefix = substr($word, 0, length($word) - 1);
            if ($g_debug) {
              print("Found Cyrillic word: $prefix\n");
            }
            found_word($prefix, $language, \$output);
            # back up over the period
            pos($input) = pos($input) - 1;
          }
        } else {
          # word does not end in period
          if ($g_debug) {
            print("Found Cyrillic word: $1\n");
          }
          found_word($1, $language, \$output);
        }

      } elsif ($input =~ /\G(\.?((\p{InMyHebrewAlnum}-?)*
                                 \p{InMyHebrewLetter}
                                 [\p{InMyHebrewAlnum}\-]*\.?)+
                             ([\p{InMyHebrewAlnum}\-]+\.?)*)/gcx) {
        my $word = $1;
        if ($word =~ /\.$/) {
          # word ends in period; look up whether it's an abbreviation,
          # or an end of sentence
          if (is_abbrev($word, $language)) {
            if ($g_debug) {
              print("Found Hebrew word ending in period; " .
                    "found in abbreviations table: $word\n");
            }
            found_word($word, $language, \$output);
          } else {
            my $prefix = substr($word, 0, length($word) - 1);
            if ($g_debug) {
              print("Found Hebrew word: $prefix\n");
            }
            found_word($prefix, $language, \$output);
            # back up over the period
            pos($input) = pos($input) - 1;
          }
        } else {
          # word does not end in period
          if ($g_debug) {
            print("Found Hebrew word: $1\n");
          }
          found_word($1, $language, \$output);
        }

      } elsif ($input =~ /\G(([\.\,+]|-)?
                             \p{InMyDigit}+
                             ([\.\,]?\p{InMyDigit}{1,4})*
                             ([\.\,]\p{InMyDigit}+)?)/gcx) {
        if ($g_debug) {
          print("Found number expression: $1\n");
        }
        found_number($1, \$output);

      } elsif ($input =~ /\G(\&
                             ((\#[0-9]{2,4})|
                              (\p{InMyBasicLatinLetter}+))
                             ;)/gcx) {
        my $word = decode_entities($1);
        if ($g_debug) {
          print("Found character entity: $1 (decoded to: $word)\n");
        }
        found_word($word, $language, \$output);

      } elsif ($input =~ /\G(\.{2,4})/gc) {
        if ($g_debug) {
          print("Found ellipsis: $1\n");
        }
        found_word($1, $language, \$output);

      } elsif ($input =~ /\G($EOS_CHARS( ?$EOS_CHARS(?=[\s\n\r]))*)/gc) {
        if ($g_debug) {
          print("Found one or more end-of-sentence characters; " .
                "starting new sentence: $1\n");
        }
        found_word($1, $language, \$output);

      } elsif ($input =~ /\G(\p{InMyPunctuation})/gc) {
        if ($g_debug) {
          print("Found punctuation character: $1\n");
        }
        found_word($1, $language, \$output);

      } elsif ($input =~ /\G(\t)/gc) {
        if ($g_debug) {
          print("Found tab character - will break sentence.\n");
        }
        found_word($1, $language, \$output);

      } elsif ($input =~ /\G([ \n\r])/gc) {
        if ($g_debug) {
          print("Found whitespace character: $1\n");
        }
        found_word(" ", $language, \$output);

      } elsif ($input =~ /\G(\p{InPrivate_Use_Area}+)/gc) {
        if ($g_debug) {
          print "Found private use area character; discarding: $1\n";
        }

      } elsif ($input =~ /\G(.)/gc) {
        # note that we need a catch-all like this to make sure
        # tokenization can advance through the input.
        if ($g_debug) {
          print "Found individual character: $1\n";
        }
        found_word($1, $language, \$output);
      }
    }
  } # end LINE:

  # emit remaining tokens, make sure there is one final newline
  if ($output ne "") {
    print "$output\n";
  } else {
    print("$output");
  }
}


#----------------------------------------------------------------------
# functions called from tokenize()

# note that we have found a "word"

sub found_word( $$$ ) {
  my ($word, $language, $output) = @_;
  if (length($word) > $MAX_WORD_LEN) {
    if ($g_debug) {
      print("Discarding word of length over $MAX_WORD_LEN: $word\n");
    }
    return;
  }
  # look for word substitution
  if (has_word_mapping(\$word, $language)) {
    if ($g_debug) {
      print("Mapping word to: $word\n");
    }
  }
  emit_token($word, $output);
}

# note that we have found a "symbol".
# symbols have no length limit, and no word mappings.

sub found_symbol( $$ ) {
  my ($symbol, $output) = @_;
  emit_token($symbol, $output);
}

# note that we have found a number

sub found_number( $$ ) {
  my ($number, $output) = @_;
  if (length($number) > $MAX_NUMBER_LEN) {
    # discard numbers that are too long
    if ($g_debug) {
      print("Discarding number of length over $MAX_NUMBER_LEN: $number\n");
    }
    $number = "";
  }
  emit_token($number, $output);
}

# emit a token to the output

{
  # track whether to emit space or start new sentence before the next token
  my $emit_space = 0;
  my $start_new_sentence = 0;

  sub emit_token( $$ ) {
    my ($token, $output) = @_;

    if ($token =~ /^$EOS_CHARS( ?$EOS_CHARS)*$/ &&
        not $token =~ /^\.{2,4}$/) {
      # end-of-sentence token - emit space if necessary, emit token,
      # start new sentence
      # (ellipsis does not start a new sentence)
      if ($emit_space) {
        $$output = $$output . " $token";
      } else {
        $$output = $$output . $token;
      }
      $emit_space = 1;
      $start_new_sentence = 1;

    } elsif ($token =~ /^\t$/) {
      # tab character - break sentence immediately
      if ($$output ne "") {
        print "$$output\n";
      }
      $$output = "";
      $start_new_sentence = 0;
      $emit_space = 0;

    } elsif ($token =~ /^\s*[\n\r]$/) {
      # empty line - start a new sentence
      $start_new_sentence = 1;

    } elsif ($token =~ /^\s+$/s) {
      # whitespace character(s) - ignore

    } else {
      if ($start_new_sentence) {
        if ($$output ne "") {
          print "$$output\n";
        }
        $start_new_sentence = 0;
        $$output = $token;
      } elsif ($emit_space) {
        $$output = $$output . " $token";
      } else {
        $$output = $$output . $token;
      }
      $emit_space = 1;
    }
  }
}


#----------------------------------------------------------------------
# access to data tables. check entries for UNKNOWN_LANGUAGE first, then
# the specified language.


sub is_abbrev( $$ ) {
  my ($word, $language) = @_;
  if (exists($g_abbrevs{"UNKNOWN_LANGUAGE"}{$word}) ||
      exists($g_abbrevs{$language}{$word})) {
    return 1;
  } else {
    return 0;
  }
}

# contraction prefixes are only listed in lower-case in the data file.

sub is_contraction_prefix( $$ ) {
  my ($prefix, $language) = @_;
  $prefix = lc($prefix);
  if (exists($g_contraction_prefixes{"UNKNOWN_LANGUAGE"}{$prefix}) ||
      exists($g_contraction_prefixes{$language}{$prefix})) {
    return 1;
  } else {
    return 0;
  }
}

sub has_word_mapping( $$ ) {
  my ($word, $language) = @_;
  if (exists($g_word_maps{"UNKNOWN_LANGUAGE"}{$$word})) {
    $$word = $g_word_maps{"UNKNOWN_LANGUAGE"}{$$word};
    return 1;
  } elsif (exists($g_word_maps{$language}{$$word})) {
    $$word = $g_word_maps{language}{$$word};
    return 1;
  } else {
    return 0;
  }
}

sub has_char_mapping( $$ ) {
  my ($char, $language) = @_;
  if (exists($g_char_maps{"UNKNOWN_LANGUAGE"}{$$char})) {
    $$char = $g_char_maps{"UNKNOWN_LANGUAGE"}{$$char};
    return 1;
  } elsif (exists($g_char_maps{$language}{$$char})) {
    $$char = $g_char_maps{language}{$$char};
    return 1;
  } else {
    return 0;
  }
}

#----------------------------------------------------------------------
# utility subroutines

sub usage () {
  print("tokenizer usage example:\n");
  print("% tokenizer.pl\n");
  print(" [-debug]\n");
  print(" [--datafile=</path/to/tokenizer.data>\n");
  print(" [--language={SPANISH,ENGLISH,PORTUGUESE,...}\n");
  print(" < infile > outfile\n");
}

# print out the data tables
sub print_data_tables() {
  my ($lang, $raw, $normalized);
  print("Contents of data tables:\n");
  for $lang (sort keys %g_abbrevs) {
    print "Abbreviations for language $lang:\n";
    for my $abbrev (sort keys %{$g_abbrevs{$lang}}) {
      print("$abbrev ");
    }
    print "\n";
  }
  for $lang (sort keys %g_char_maps) {
    print "Character mappings for language $lang:\n";
    while (($raw, $normalized) = each %{$g_char_maps{$lang}}) {
      print("$raw-->");
      if (not $normalized eq "") {
        print("$normalized ");
      } else {
        print("DELETED ");
      }
    }
    print "\n";
  }
  for $lang (sort keys %g_word_maps) {
    print "Word mappings for language $lang:\n";
    while (($raw, $normalized) = each %{$g_word_maps{$lang}}) {
      print("$raw-->");
      if (not $normalized eq "") {
        print("$normalized ");
      } else {
        print("DELETED ");
      }
    }
    print "\n";
  }
}

# Unescape \x hex character escape sequences:
#
# 1. Convert from Unicode characters to UTF-8 byte sequence.
# 2. Replace "\xAB" sequence with single byte of that value.
# 3. Convert UTF-8 byte sequence back to Unicode characters.

sub unescape_hex( $ ) {
  my ($input) = @_;
  my $octets = encode_utf8($input);
  $octets =~ s/\\x([0-9A-Fa-f]{2})/chr(hex($1))/ge;
  my $output = decode_utf8($octets);
  return $output;
}
