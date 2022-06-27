SET mypath=%~dp0
SET threshold=80
SET months=2
java -Xmx4G -jar deckscraper.jar %mypath% %threshold% %months%