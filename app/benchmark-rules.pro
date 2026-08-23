# The benchmark build type runs release code, so the macrobenchmark needs the class and method
# names it drives to survive R8. Only the entry points matter; everything else stays obfuscated.
-dontobfuscate
