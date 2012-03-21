#!/usr/bin/env bash
#
# TODO(spenceg): Merge this with setup_default_db.sh
#

# Database parameters
# TODO: Change as needed.
dbhost=localhost
dbname=djangodb
dbadmin=django

# Paths to various files
# TODO: Change these paths as needed
data_dir=/home/rayder441/sandbox/javanlp/projects/mt/ptm/data
script_dir=/home/rayder441/sandbox/javanlp/projects/mt/ptm/scripts

# Documents for exp1 (user study)
echo Loading the exp1 documents...
# En documents

# TODO(spenceg): Set this....
tgt_seq_pk=58
src_seq_pk=1
doc="$data_dir"/en/proc/training.txt
n_lines=`wc -l "$doc" | awk '{ print $1 }'`
echo Loading "$doc"
let src_seq_pk="$src_seq_pk + $n_lines"


doc="$data_dir"/en/proc/Flag_of_Japan_Wikipedia.txt
n_lines=`wc -l "$doc" | awk '{ print $1 }'`
echo Loading "$doc"
let src_seq_pk="$src_seq_pk + $n_lines"


doc="$data_dir"/en/proc/Schizophrenia_Wikipedia.txt
n_lines=`wc -l "$doc" | awk '{ print $1 }'`
echo Loading "$doc"
let src_seq_pk="$src_seq_pk + $n_lines"


doc="$data_dir"/en/proc/Infinite_monkey_theorem_Wikipedia.txt
n_lines=`wc -l "$doc" | awk '{ print $1 }'`
echo Loading "$doc"
./load_target_doc.sh "$data_dir"/en/proc/trans/Infinite_monkey_theorem_Wikipedia.ar "$tgt_seq_pk" 2 "$src_seq_pk"
let tgt_seq_pk="$tgt_seq_pk + $n_lines"
./load_target_doc.sh "$data_dir"/en/proc/trans/Infinite_monkey_theorem_Wikipedia.fr "$tgt_seq_pk" 3 "$src_seq_pk"
let tgt_seq_pk="$tgt_seq_pk + $n_lines"
./load_target_doc.sh "$data_dir"/en/proc/trans/Infinite_monkey_theorem_Wikipedia.de "$tgt_seq_pk" 4 "$src_seq_pk"
let tgt_seq_pk="$tgt_seq_pk + $n_lines"
let src_seq_pk="$src_seq_pk + $n_lines"

doc="$data_dir"/en/proc/1896_Summer_Olympics_Wikipedia.txt
n_lines=`wc -l "$doc" | awk '{ print $1 }'`
echo Loading "$doc"
./load_target_doc.sh "$data_dir"/en/proc/trans/1896_Summer_Olympics_Wikipedia.ar "$tgt_seq_pk" 2 "$src_seq_pk"
let tgt_seq_pk="$tgt_seq_pk + $n_lines"
./load_target_doc.sh "$data_dir"/en/proc/trans/1896_Summer_Olympics_Wikipedia.fr "$tgt_seq_pk" 3 "$src_seq_pk"
let tgt_seq_pk="$tgt_seq_pk + $n_lines"
./load_target_doc.sh "$data_dir"/en/proc/trans/1896_Summer_Olympics_Wikipedia.de "$tgt_seq_pk" 4 "$src_seq_pk"
let tgt_seq_pk="$tgt_seq_pk + $n_lines"
let src_seq_pk="$src_seq_pk + $n_lines"

echo "Done!"
