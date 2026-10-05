version = 1

cloudstream {
    language = "vi"
    description = "NguonC by genre: movies, series, and multiple language servers via its public API"
    authors = listOf("Daniel")
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
