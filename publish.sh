#!/bin/bash

if [[ "$1" = "--snapshot" ]]; then snapshot=true; fi
if [[ "$1" = "--local" ]]; then local=true; fi

if ! [[ ${local} ]]; then
  ./gradlew -p moshi-ir/moshi-gradle-plugin publish -x dokkaHtml
  if ! [[ ${snapshot} ]]; then
    ./gradlew closeAndReleaseRepository
  fi
  ./gradlew publish -x dokkaHtml
  if ! [[ ${snapshot} ]]; then
    ./gradlew closeAndReleaseRepository
  fi
else
  ./gradlew -p moshi-ir/moshi-gradle-plugin publishToMavenLocal -x dokkaHtml
  ./gradlew publishToMavenLocal -x dokkaHtml
fi