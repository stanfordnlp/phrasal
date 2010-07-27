#!/bin/bash

remove_unk | \
americanize | \
#aren-postprocess-nw | \
aren-postprocess-text | \
cat
