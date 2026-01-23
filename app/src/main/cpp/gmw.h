#ifndef GMW_H
#define GMW_H

#include "fcwt.h"
#include <complex>

/* Generalized Morse Wavelet (bandpass-normalized)

 */
class GeneralizedMorse : public Wavelet {
public:
    /**
     * @param beta
     * @param gamma
     */
    GeneralizedMorse(float beta = 90.0f, float gamma = 3.0f);
    ~GeneralizedMorse();

    // fCWT API:  freq-domain mother
    void generate(int size) override;                         // freq-domain mother
    void generate(float*, float*, int, float) override {}     // not used (time-domain)
    int  getSupport(float) override { return 0; }             // not used
    void getWavelet(float, std::complex<float>*, int) override {} // not used

    float getBeta()  const { return beta_; }
    float getGamma() const { return gamma_; }

private:
    float beta_;
    float gamma_;
};

#endif
