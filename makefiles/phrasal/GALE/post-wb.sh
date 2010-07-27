#!/bin/bash

remove_unk | \
americanize | \
aren-postprocess-wb | \
#aren-postprocess-text | \
cat
