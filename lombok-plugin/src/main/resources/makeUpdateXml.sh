#!/bin/bash
echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?><plugins>" > updatePlugins.xml
cat *Update.xml >> updatePlugins.xml
echo "</plugins>" >> updatePlugins.xml
