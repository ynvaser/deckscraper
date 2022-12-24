#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd "$SCRIPT_DIR" || (echo "Can't cd to deckscraper directory" && exit 1)
echo "Running bash script from $SCRIPT_DIR"
#This is the required percentage of a given deck you need to own for it to be saved to the output folders.
THRESHOLD=80
#This is the lookback period, decks older than this value won't be scraped.
MONTHS=1
#Set this to true if you'd like to skip scraping for regular decks. Average decks will always be looked up for now. Useful for saving time.
SKIP_LOOKUP=false
#The maximum amount of basic lands a deck can have for it to be considered. Useful to filter out garbage decks.
MAX_LANDS=60
#This is the required percentage of a given AVERAGE deck you need to own for it to be saved to the output folders.
AVERAGE_DECK_THRESHOLD=80
#Set this to true if you'd like to scrape decks for every commander. This will significantly increase the time for the program to finish.
SEARCH_UNOWNED_COMMANDERS=false

java -Xmx4G -jar deckscraper.jar "$SCRIPT_DIR" $THRESHOLD $MONTHS $SKIP_LOOKUP $MAX_LANDS $AVERAGE_DECK_THRESHOLD $SEARCH_UNOWNED_COMMANDERS
