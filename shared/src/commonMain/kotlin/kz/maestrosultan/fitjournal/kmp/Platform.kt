package kz.maestrosultan.fitjournal.kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform