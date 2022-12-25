# ynvaser's EDH Deckscraper

## What is new?
* Support for Partners, Friends Forever and Backgrounds.
* Support for average deck themes.
* Minor improvements.

## What does this do?
* This tool takes your collection of cards, and the cards you own that you can use as a commander, and gives you a list of decks from EDHREC that you can build or are close to building.

## Required software
* Java 17 installed

## How to use 
* If you'd rather watch a video: https://www.youtube.com/watch?v=jZG1FMniB_Y
* Extract the archive to a folder of your choice.
* Export your DeckBox inventory as a .csv, and place it into the ***/input*** folder
* Start the program with either the provided start.bat or start.sh files.
  * Open the script of your choice with a text editor to change your settings:
    * threshold
      * This is the required percentage of a given deck you need to own for it to be saved to the output folders.
      * default 80
    * months
      * This is the lookback period, decks older than this value won't be scraped.
      * default 1
    * skipLookup
      * Set this to true if you'd like to skip scraping for regular decks. Average decks will always be looked up for now. Useful for saving time.
      * default false
    * maxlands
      * The maximum amount of basic lands a deck can have for it to be considered. Useful to filter out garbage decks.
      * default 60
    * averageDeckThreshold
      * This is the required percentage of a given AVERAGE deck you need to own for it to be saved to the output folders.
      * default 80
    * searchUnownedCommanders
      * Set this to true if you'd like to scrape decks for every commander. This will significantly increase the time for the program to finish.
      * default false
* Be patient, scraping can take a long time depending on the amount of commander candidates that you have.
  * Future runs will be faster as results are cached on disk.
  * [You can grab my database to speed things up. (updated for 2022 November-December)](https://drive.google.com/file/d/1BRNhuJ1DwBUXkeIw7ld4kkQYywUPULkh/view?usp=sharing)
    * Extract, and place contents in ***/database*** (replace all if prompted)
* Once the tool finishes, check the ***/output*** folder for the decks above your set thresholds.
  * Subfolders are named after the commanders of the decks within. 
  * Filename format: **{*percentage of cards you own*}\_{*calculated deck hash*}.txt**
  * There's a folder called **'\_average'**, which contains the average decks that you own above your set percentage.
    * Average deck filename format: **{*percentage of cards you own*}\_{*tribe / budget*}\_{*calculated deck hash*}.txt**