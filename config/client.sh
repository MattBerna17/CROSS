#!/bin/bash

# directory dei sorgenti e libreria
SRC_DIR="../CROSS/src"
LIB_DIR="../CROSS/lib"
GSON_JAR="gson-2.11.0.jar"
CLASSPATH="$SRC_DIR:$LIB_DIR/$GSON_JAR"

# compilazione dei file del client
javac -cp "$CLASSPATH" -d "$SRC_DIR" $SRC_DIR/ClientMain.java
if [ $? -ne 0 ]; then
    echo "Errore durante la compilazione dei file client."
    exit 1
fi

# avvio del client
java -cp "$CLASSPATH" ClientMain

