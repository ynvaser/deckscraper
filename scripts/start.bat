SET mypath=%~dp0
SET threshold=80
SET months=1
SET skipLookup=false
SET maxlands=40
SET averageDeckThreshold=80
java -Xmx4G -jar deckscraper.jar %mypath% %threshold% %months% %skipLookup% %maxlands% %averageDeckThreshold%