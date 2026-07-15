version = 2

cloudstream {
    language = "vi"
    description = "KKPhim organized by genre, using only the KKPhim API"
    authors = listOf("Daniel")
    status = 1

    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
