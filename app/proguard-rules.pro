# Project-specific R8 rules. Library rules (Readium, kotlinx.serialization, Ktor, Room) ship as
# consumer rules inside the dependencies, so keep this file small and justified.

# Keep stack traces readable in crash logs.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp/Ktor reference optional platform classes.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
