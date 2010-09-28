#!/usr/bin/perl -w
`mkdir -p projects/mt/src/mt/translationtreebank/data/ctb_parsed/segmented/`;
for ($i = 1; $i <= 325; $i++) {
    $file   = sprintf("/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/segmented/chtb_%04d.seg", $i);
    $output = sprintf("projects/mt/src/mt/translationtreebank/data/ctb_parsed/segmented/chtb_%04d.seg", $i);
    $cmd = 'grep -v "^<" ' . $file . " > $output";
    print $cmd,"\n";
    `$cmd`;
}
