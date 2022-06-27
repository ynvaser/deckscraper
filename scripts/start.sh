#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
THRESHOLD=80
MONTHS=1
SKIP_LOOKUP=false
java -Xmx4G -jar deckscraper.jar "$SCRIPT_DIR" $THRESHOLD $MONTHS $SKIP_LOOKUP
