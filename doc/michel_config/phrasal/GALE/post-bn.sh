#!/bin/bash

remove_unk | \
perl -pe 's/ (, ){3,}/ , um , /g' | \
perl -pe 's/ (b )+/ , uh , /g' | \
aren-postprocess-bc | \
aren-postprocess-text | \
perl -pe 's/^([\.,] )+//' | \
perl -pe 's/\. \.$/./'
