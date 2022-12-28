SET mypath=%~dp0
cd "%mypath%"
echo "Running batch script from %mypath%"
java -Xmx4G -jar deckscraper.jar