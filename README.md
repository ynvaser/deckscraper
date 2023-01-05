# ynvaser's Deckscraper

## What is new?
* Cube support with data from [CubeCobra!](https://github.com/dekkerglen/CubeCobra)
  * Special thanks to [Dekkaru](https://github.com/dekkerglen) for granting me access to their bulk data exports.
  * Exports run once a month, so the data isn't perfectly up-to-date.
* Fixed a bug for people with "Choose a Background" commanders who don't own any Backgrounds.
* All decks now contain a URL to the source of the deck or cube.
* Added ability to disable parts of the tool entirely

## What does this do?
* This tool takes your collection of cards, and gives you a list of decks from EDHREC that you can build or are close to building.
* Does the same with Cubes from CubeCobra.

## Required software
* Java 17 installed

## How to use 
* If you'd rather watch a video: https://www.youtube.com/watch?v=jZG1FMniB_Y
* Extract the archive to a folder of your choice.
* Export your DeckBox inventory as a .csv, and place it into the ***/input*** folder
* Start the program with either the provided start.bat or start.sh files.
  * Open the **'application.properties'** file with a text editor to change your settings:
    * It has a lot of options, the file contains explanations.
* Be patient, scraping can take a long time depending on the amount of commander candidates that you have (or if you have the searchUnownedCommanders on).
  * Future runs will be faster as results are cached on disk.
  * [You can grab my database to speed things up. (has data from 2022.10 - 2023.01)](https://drive.google.com/file/d/1NWYOHdK8YFtJ012tm6k9Jp5b9FKROINV/view?usp=share_link)
    * Extract, and place contents in ***/database*** (replace all if prompted)
* Once the tool finishes, check the ***/output*** folder for the decks above your set thresholds.
  * Subfolders are named after the commanders of the decks within. 
  * Filename format: **{*percentage of cards you own*}\_{*calculated deck hash*}.txt**
  * There's a folder called **'\_average'**, which contains the average decks that you own above your set percentage.
    * Average deck filename format: **{*percentage of cards you own*}\_{*tribe / budget*}\_{*calculated deck hash*}.txt**
  * There's a folder called **'\_cube'**, which contains the cubes that you own above your set percentage.
    * It has a subfolder called **'\_popular'** which has cubes that you own above a set percentage with followers above a set amount.
    * Cube filename format: **{*percentage of cards you own*}\_{*name of the cube*}\_{*cube unique id*}.txt**