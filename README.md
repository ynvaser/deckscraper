# ynvaser's EDH Deckscraper

##Required software
* Java 17 installed

##How to use  
* Extract the archive to a folder of your choice.
* Export your DeckBox inventory as a .csv, and place it into the ***/input*** folder
* Start the program with either the provided start.bat or start.sh files.
  * Open the script of your choice with a text editor to change your settings.
* Be patient, scraping can take a long time depending on the amount of commander candidates that you have.
  * Future runs will be faster as results are cached on disk.
  * [You can grab my database to possibly speed things up.](https://drive.google.com/file/d/1e76_Za23k0apmHlha644AsbX8gU4saQ8/view?usp=sharing)
    * Extract, and place contents in ***/database*** (replace all if prompted)
* Once the tool finishes, check the ***/output*** folder for the decks above your set thresholds.
  * Subfolders are named after the commanders of the decks within. 
  * Filename format: **{*percentage of cards you own*}_{*calculated deck hash*}.txt**