# JNI bridge: methods called from C++ via JNI must be kept.
-keep class io.github.ranzlappen.synthpiano.audio.NativeSynth { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class io.github.ranzlappen.synthpiano.**$$serializer { *; }
-keepclassmembers class io.github.ranzlappen.synthpiano.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.ranzlappen.synthpiano.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-keep class androidx.compose.runtime.** { *; }

# Crash reporting friendliness
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
