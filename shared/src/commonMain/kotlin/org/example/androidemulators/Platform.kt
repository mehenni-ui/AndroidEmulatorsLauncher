package org.example.androidemulators

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform