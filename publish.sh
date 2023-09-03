#!/bin/bash

if [[ "$1" = "--local" ]]; then local=true; fi

if ! [[ ${local} ]]; then
  ./gradlew -p moshi-ir/moshi-gradle-plugin publish -x dokkaHtml
  ./gradlew publish -x dokkaHtml
else
  ./gradlew -p moshi-ir/moshi-gradle-plugin publishToMavenLocal -x dokkaHtml
  ./gradlew publishToMavenLocal -x dokkaHtml
fi