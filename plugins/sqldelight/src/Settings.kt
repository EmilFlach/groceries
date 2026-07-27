package sqldelight.amper

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface SqlDelightSettings {
    @Suppress("unused")
    val packageName: String
    @Suppress("unused")
    val databaseName: String get() = "AppDatabase"

    @Suppress("unused")
    val generateAsync: Boolean get() = false
}
