#!/usr/bin/env bash

echo "Cloning osstrich..."
mkdir tmp
cd tmp
git clone git@github.com:square/osstrich.git
cd osstrich
echo "Packaging..."
mvn package
echo "Running..."
rm -rf tmp/moshi-sealed && java -jar target/osstrich-cli.jar tmp/moshi-sealed git@github.com:ZacSweers/moshi-sealed.git dev.zacsweers.moshisealed
echo "Cleaning up..."
cd ../..
rm -rf tmp
echo "Finished!"
