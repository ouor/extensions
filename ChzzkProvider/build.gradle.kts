// Use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "Watch livestreams from Chzzk"
    authors = listOf("User")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 3 // Beta-only

    tvTypes = listOf("Live", "TvSeries")
    iconUrl = "https://ssl.pstatic.net/static/nng/glive/icon/favicon.png"

    isCrossPlatform = true
}

dependencies {
    implementation(kotlin("stdlib")) // Adds Standard Kotlin Features
    implementation("com.github.Blatzar:NiceHttp:0.4.11") // HTTP Lib
    implementation("org.jsoup:jsoup:1.18.3") // HTML Parser
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1") // JSON Parser
}
