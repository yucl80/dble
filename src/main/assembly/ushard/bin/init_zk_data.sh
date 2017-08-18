#!/bin/bash

echo "check JAVA_HOME & java"
JAVA_CMD=$JAVA_HOME/bin/java
MAIN_CLASS=com.actionsky.ushard.config.loader.zkprocess.xmltozk.XmltoZkMain
if [ ! -d "$JAVA_HOME" ]; then
    echo ---------------------------------------------------
    echo WARN: JAVA_HOME environment variable is not set. 
    echo ---------------------------------------------------
    JAVA_CMD=java
fi

echo "---------set HOME_DIR------------"
CURR_DIR=`pwd`
cd ..
USHARD_HOME=`pwd`
cd $CURR_DIR
$JAVA_CMD -Xms256M -Xmx1G -XX:MaxPermSize=64M  -DUSHARD_HOME=$USHARD_HOME -cp "$USHARD_HOME/conf:$USHARD_HOME/lib/*" $MAIN_CLASS
echo "---------finished------------"