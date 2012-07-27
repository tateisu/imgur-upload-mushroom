#!/bin/bash

find src -name \*.java -exec perl fix_tab.pl 4 \{\} \;
find res -name \*.xml  -exec perl fix_tab.pl 4 \{\} \;
