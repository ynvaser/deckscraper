SET mypath=%~dp0
cd %mypath%
echo "Running batch script from %mypath%"
@REM This is the required percentage of a given deck you need to own for it to be saved to the output folders.
SET threshold=80
@REM This is the lookback period, decks older than this value won't be scraped.
SET months=1
@REM Set this to true if you'd like to skip scraping for regular decks. Average decks will always be looked up for now. Useful for saving time.
SET skipLookup=false
@REM The maximum amount of basic lands a deck can have for it to be considered. Useful to filter out garbage decks.
SET maxlands=60
@REM This is the required percentage of a given AVERAGE deck you need to own for it to be saved to the output folders.
SET averageDeckThreshold=80
@REM Set this to true if you'd like to scrape decks for every commander. This will significantly increase the time for the program to finish.
SET searchUnownedCommanders=false
java -Xmx4G -jar deckscraper.jar %mypath% %threshold% %months% %skipLookup% %maxlands% %averageDeckThreshold% %searchUnownedCommanders%