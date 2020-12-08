#!/bin/bash


# Make sure mooInkC signingConfig (in app level build.gradle)
# has correct sign key information for the keystore
#
# execute "sh scripts/mooinkc-install.sh"

sh gradlew assembleMooInkCDebug
cp app/build/outputs/apk/app-mooInkC-debug.apk limeIme.apk

adb remount
adb push limeIme.apk /system/app/limeIme
adb shell pm install -t -r "/system/app/limeIme/limeIme.apk"
adb shell am startservice -n net.toload.main.hd/net.toload.main.hd.readmoo.MooService
adb shell am startservice -n net.toload.main.hd/net.toload.main.hd.readmoo.MooService

