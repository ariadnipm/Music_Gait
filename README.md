# Development of a Gait Detection and Adaptive Auditory Cueing Application for Parkinson’s Disease

This project was developed as part of my Diploma Thesis in Electrical and Computer Engineering at Aristotle University of Thessaloniki.

The project focuses on real-time gait detection and cadence estimation using smartphone accelerometer data, integrated into an Android application that provides adaptive rhythmic auditory cueing.

## Gait Detection Algorithm

The gait detection methodology is based on:

Straczkiewicz, M., Huang, E. J., & Onnela, J.-P. (2023).  
[A “one-size-fits-most” walking recognition method for smartphones, smartwatches, and wearable accelerometers](https://doi.org/10.1038/s41746-022-00745-z)

The original implementation was ported and adapted to C++ for integration into the Android application and real-time execution.

## External Libraries

This project uses [fCWT](https://github.com/fastlib/fCWT) for the Continuous Wavelet Transform (CWT).

Arts, L. P. A. & van den Broek, E. L. (2022).  
[The fast continuous wavelet transformation (fCWT) for real-time, high-quality, noise-resistant time-frequency analysis](https://doi.org/10.1038/s43588-021-00183-z)

## Technologies

C++ • Kotlin • Android • JNI/NDK • Oboe • fCWT
