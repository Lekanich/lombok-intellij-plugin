#!/bin/bash
DEPLOY_DIR=$1
cp lombok-plugin/target/lombok-plugin.jar ${DEPLOY_DIR}/lombok-plugin.jar
cp lombok-plugin/src/main/resources/lombokUpdate.xml ${DEPLOY_DIR}/lombokUpdate.xml
cp lombok-plugin/src/main/resources/makeUpdateXml.sh ${DEPLOY_DIR}/makeUpdateXml.sh
sh ${DEPLOY_DIR}/makeUpdateXml.sh ${DEPLOY_DIR}
