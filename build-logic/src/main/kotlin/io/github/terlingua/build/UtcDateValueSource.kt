package io.github.terlingua.build

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.time.LocalDate
import java.time.ZoneOffset

/** Re-evaluated on every build, so a cached configuration still sees the current UTC date. */
abstract class UtcDateValueSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String = LocalDate.now(ZoneOffset.UTC).toString()
}
