package dev.zacsweers.moshix.immutable.adapters

import com.squareup.moshi.Moshi

/**
 * Provides type adapters for [kotlinx.collections.immutable.PersistentList] and [kotlinx.collections.immutable.PersistentMap]
 * types. See [PersistentListJsonAdapterFactory] and [PersistentMapJsonAdapterFactory] for examples.
 */
public fun Moshi.Builder.addKotlinXImmutableAdapters(): Moshi.Builder =
    this.add(PersistentListJsonAdapterFactory)
        .add(PersistentMapJsonAdapterFactory)
