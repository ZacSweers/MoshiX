#!/bin/bash

if [[ "$1" = "--local" ]]; then local=true; fi

if ! [[ ${local} ]]; then
  cd moshi-ir/moshi-gradle-plugin publish
  ./gradlew publish -x dokkaHtml
  cd ..
  ./gradlew publish -x dokkaHtml
else
  ./gradlew -p moshi-ir/moshi-gradle-plugin publishToMavenLocal -x dokkaHtml
  ./gradlew publishToMavenLocal -x dokkaHtml
fi