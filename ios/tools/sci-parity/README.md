# Fen kütüphanesi eşlik testi (Linux/macOS)

Android `V45ChemistryTest`, `V45ParamShapesTest`, `V46ParamMechanicsTest` testlerinin Swift karşılığı.
UIKit gerektirmeyen çekirdek dosyalarla tek başına derlenir (Swift 6.0, `-swift-version 5`):

    cd ios/DrawGuidance
    swiftc -swift-version 5 $(saf çekirdek dosyalar) Core/Content/Science/*.swift Core/Content/Science/Chem/*.swift \
        ../tools/sci-parity/main.swift -o /tmp/sci && /tmp/sci

2026-11-09: checks=11128 failures=0.
