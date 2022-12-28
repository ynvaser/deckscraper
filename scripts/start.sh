#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd "$SCRIPT_DIR" || (echo "Can't cd to deckscraper directory" && exit 1)
echo "Running bash script from $SCRIPT_DIR"
java -Xmx4G -jar deckscraper.jar
