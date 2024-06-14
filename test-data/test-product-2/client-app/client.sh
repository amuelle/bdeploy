#!/usr/bin/env bash

##
# Mini demo client application. Opens the system text editor using xdg-open
##

echo BDeploy Client Application

# Current working directory is set by the launcher, so the file is in a "good" place.
cat > demo.txt <<EOF
BDeploy Launcher is working!

This file has been written by the demo client application on your system and opened with your current text editor.
The command line argument were: "$@"
EOF

file="demo.txt"

[[ -n "$1" && -z "$2" ]] && file="$1"

if [[ $(uname) == "Darwin" ]]; then
    open -e "$file"
else
    xdg-open "$file"
fi
