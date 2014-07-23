#!/bin/bash
echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?><plugins>" > $1/updatePlugins.xml
cat $1/*Update.xml >> $1/updatePlugins.xml
echo "</plugins>" >> $1/updatePlugins.xml
