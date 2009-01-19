#!/usr/bin/perl -w
`mkdir -p projects/mt/src/mt/translationtreebank/data/ctb_parsed/bracketed/`;
`mkdir -p projects/mt/src/mt/translationtreebank/data/ctb_parsed/err/`;
$PM = "/user/pichuan/scr/mt/parse-experiments/lex-exp-20060609/326-3145.chineseFactored.ser.gz";
for ($i = 1; $i <= 325; $i++) {
    $idx = sprintf("%04d", $i);
    $outseg = "projects/mt/src/mt/translationtreebank/data/ctb_parsed/segmented/chtb_$idx.seg";
    $outfid = "projects/mt/src/mt/translationtreebank/data/ctb_parsed/bracketed/chtb_$idx.fid";
    $err    = "projects/mt/src/mt/translationtreebank/data/ctb_parsed/err/chtb_$idx.err";
    $cmd = "java -Xmx4g edu.stanford.nlp.parser.lexparser.LexicalizedParser -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -tokenized -sentences newline -escaper edu.stanford.nlp.trees.international.pennchinese.ChineseEscaper -encoding UTF-8 -outputFormat penn $PM $outseg > $outfid 2> $err";
    print $cmd,"\n";
    #`$cmd`;
}
