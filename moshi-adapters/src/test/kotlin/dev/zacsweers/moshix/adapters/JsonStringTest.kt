/*
 * Copyright (C) 2020 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshix.adapters

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.GET

class JsonStringTest {
  @Test
  fun simpleCase() {
    // language=JSON
    val json = "{\"type\":1,\"rawJson\":{\"a\":2,\"b\":3,\"c\":[1,2,3]}}"

    val moshi = Builder().add(JsonString.Factory()).addLast(KotlinJsonAdapterFactory()).build()

    val example = moshi.adapter<ExampleClass>().fromJson(json)!!

    assertThat(example.type).isEqualTo(1)

    // language=JSON
    assertThat(example.rawJson).isEqualTo("{\"a\":2,\"b\":3,\"c\":[1,2,3]}")
  }

  data class ExampleClass(val type: Int, @JsonString val rawJson: String)

  @Test
  fun nullableCase() {
    // language=JSON
    val json = "{\"type\":1,\"rawJson\":null}"

    val moshi = Builder().add(JsonString.Factory()).addLast(KotlinJsonAdapterFactory()).build()

    val example = moshi.adapter<NullableExampleClass>().fromJson(json)!!

    assertThat(example.type).isEqualTo(1)

    // language=JSON
    assertThat(example.rawJson).isNull()
  }

  data class NullableExampleClass(val type: Int, @JsonString val rawJson: String?)

  @Test
  fun retrofitServiceMethodCase() {
    // language=JSON
    val json = "{\"a\":2,\"b\":3,\"c\":[1,2,3]}"

    val moshi = Builder().add(JsonString.Factory()).build()

    val server = MockWebServer()
    server.enqueue(MockResponse().setBody(json))
    server.enqueue(MockResponse().setBody(json))
    server.start()

    val aService =
      Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create<AService>()

    assertThat(aService.aJsonStringMethod().execute().body()).isEqualTo(json)

    val exception =
      assertThrows(JsonDataException::class.java) {
        aService.aNonJsonStringMethod().execute().body()
      }

    assertThat(exception)
      .hasMessageThat()
      .contains("Expected a string but was BEGIN_OBJECT at path \$")

    server.shutdown()
  }

  interface AService {

    @JsonString @GET("/") fun aJsonStringMethod(): Call<String>

    @GET("/") fun aNonJsonStringMethod(): Call<String>
  }
}
