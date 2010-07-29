#!/bin/sh -e

# called by uscan with '--upstream-version' <version> <file>
DIR=jruby-$2

# clean up the upstream tarball
tar zxf $3
GZIP=--best tar czf $3 -X debian/orig-tar.exclude $DIR
rm -rf $DIR

