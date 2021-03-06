#!/bin/sh
#
# build-deb.sh Builds the protean deb binary package.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Check that CI variables exist in env
ROOT_DIR=$(dirname $0)/..
VERSION=$1

if [ "$VERSION" != "" ]
then

    # Assumes that lein uberjar has been run and uberjar exists in target directory
    rm -rf $ROOT_DIR/target/deb
    mkdir -p $ROOT_DIR/target/deb
    mkdir -p $ROOT_DIR/target/deb/usr/bin
    mkdir -p $ROOT_DIR/target/deb/usr/lib/protean

    cp $ROOT_DIR/target/*standalone* $ROOT_DIR/target/deb/usr/lib/protean/protean.jar
    cp $ROOT_DIR/defaults.edn $ROOT_DIR/target/deb/usr/lib/protean/defaults.edn
    rm -rf $ROOT_DIR/silk_templates/data/protean-api/*
    rm -rf $ROOT_DIR/silk_templates/site/*
    cp -r $ROOT_DIR/silk_templates $ROOT_DIR/target/deb/usr/lib/protean/silk_templates
    cp -r $ROOT_DIR/public $ROOT_DIR/target/deb/usr/lib/protean
    cp -r $ROOT_DIR/build/etc/protean $ROOT_DIR/target/deb/usr/bin

    # Copy over the DEBIAN directory
    cp -r $ROOT_DIR/build/DEBIAN $ROOT_DIR/target/deb
    # Change the control file to use CI variables from env
    cat $ROOT_DIR/build/DEBIAN/control | sed s/%VERSION%/$1/  > $ROOT_DIR/target/deb/DEBIAN/control

    cp -r $ROOT_DIR/build/etc $ROOT_DIR/target/deb

    dpkg-deb --build $ROOT_DIR/target/deb $ROOT_DIR/target
else
    echo "ERROR: VERSION variable not defined."
    echo "Usage: $(basename "$0") VERSION"
fi
