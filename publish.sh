#!/bin/bash

if [[ "$1" = "--local" ]]; then local=true; fi

if ! [[ ${local} ]]; then
  ./gradlew -p moshi-ir/moshi-gradle-plugin publish -x dokkaHtml --no-configuration-cache
  ./gradlew publish -x dokkaHtml --no-configuration-cache
else
  ./gradlew -p moshi-ir/moshi-gradle-plugin publishToMavenLocal -x dokkaHtml
  ./gradlew publishToMavenLocal -x dokkaHtml
fi