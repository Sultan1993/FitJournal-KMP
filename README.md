# This is a repository that holds Kotlin Multiplatform code for Fit Journal application.

## Android
```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Sultan1993/FitJournal-KMP")
        credentials {
            username = "username"
            password = "password"
        }
    }
}

dependencies {
    implementation "kz.maestrosultan.fitjournal.kmp:shared-android:<version>"
}
```

## iOS
Add Swift Package: https://github.com/Sultan1993/FitJournal-KMP
