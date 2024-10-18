#!/bin/bash

if [[ "$1" = "--local" ]]; then local=true; fi

if ! [[ ${local} ]]; then
  ./gradlew -p moshi-ir/moshi-gradle-plugin publish --no-configuration-cache
  ./gradlew publish --no-configuration-cache
else
  ./gradlew -p moshi-ir/moshi-gradle-plugin publishToMavenLocal
  ./gradlew publishToMavenLocal
fi