#!/usr/bin/env bash

./make-experiment.py subject 4 2 fr en ~/sandbox/data/mt/ptm/taus-data/fr-en/pilot_study/med/file.index /static/data/fren/med ~/sandbox/data/mt/ptm/taus-data/fr-en/pilot_study/sw/file.index /static/data/fren/sw ~/sandbox/data/mt/ptm/taus-data/fr-en/training/file.index /static/data/fren/train > pilot-study.fr-en.users
mv experiment.json pilot-study.fr-en.json
