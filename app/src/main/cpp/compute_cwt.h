#include <vector>
#include <complex>
#include <cmath>
#include <algorithm>
#include "gmw.h"
#include "fcwt.h"
#include "helpers.h"
#include <iomanip>

struct CWTResult {
    std::vector<double> freqs_interp;
    std::vector<std::vector<double>> coefs_interp;
};
/*Compute and interpolate CWT over acceleration data
  input: bout-> bout with valid periods only
         fs-> sampling frequency */
CWTResult compute_interpolate_cwt(const std::vector<double>& bout,
                                  int fs )
{
    CWTResult result;

    // if bout too small, return empty
    if (fs <= 0 || bout.size() < static_cast<std::size_t>(2 * fs)) {
        return result;
    }

    const std::size_t N = bout.size();

    // 1) Tukey window (alpha=0.02), smooth the edges of the signal to minimize coin of influence
    std::vector<double> window = tukey_window(N, 0.02);
    std::vector<double> windowed_bout(N);
    for (std::size_t i = 0; i < N; ++i) {
        windowed_bout[i] = bout[i] * window[i];
    }

    // 2) zero padding 5*fs left-right
    const int padding = 5 * fs;
    std::vector<double> padded_signal;
    padded_signal.reserve(N + 2 * static_cast<std::size_t>(padding));
    padded_signal.insert(padded_signal.end(), padding, 0.0);
    padded_signal.insert(padded_signal.end(),
                         windowed_bout.begin(), windowed_bout.end());
    padded_signal.insert(padded_signal.end(), padding, 0.0);


    if (!padded_signal.empty()) {
        padded_signal.pop_back();
    }
    const int padded_N = static_cast<int>(padded_signal.size());

    // 3) CWT with  Generalized Morse wavelet  (β=90, γ=3)
    const int   n_freqs = 193;
    const float f0      = 0.32f;
    const float f1      = 5.0f;
    // Wavelet and Scales  with log scales
    GeneralizedMorse gmw(90.0f, 3.0f);
    Scales scales(&gmw, FCWT_LOGSCALES, static_cast<float>(fs),
                  f0, f1, n_freqs);


    std::vector<float> freqs_f(n_freqs);
    scales.getFrequencies(freqs_f.data(), n_freqs);
    std::vector<double> freqs(freqs_f.begin(), freqs_f.end());


    std::vector<float> scales_f(n_freqs);
    scales.getScales(scales_f.data(), n_freqs);


    std::vector<float> sig_f(padded_N);
    for (int i = 0; i < padded_N; ++i) {
        sig_f[i] = static_cast<float>(padded_signal[i]);
    }


    std::vector<std::complex<float>> cwt_out(
            static_cast<std::size_t>(n_freqs) *
            static_cast<std::size_t>(padded_N));

    // FCWT computation
    FCWT fcwt_obj(&gmw, /*threads*/1, /*use_opt*/false, /*use_norm*/true);
    fcwt_obj.cwt(sig_f.data(), padded_N, cwt_out.data(), &scales);

    // 4) compute |W|^2 and normalize by scale
    const int Ntime     = padded_N;
    const int Ntime_ext = Ntime + 1;

    std::vector<std::vector<double>> mag2(
            n_freqs, std::vector<double>(Ntime_ext, 0.0));

    for (int f = 0; f < n_freqs; ++f) {
        for (int t = 0; t < Ntime; ++t) {
            const auto& z = cwt_out[static_cast<std::size_t>(f) * Ntime + t];
            const double re = static_cast<double>(z.real());
            const double im = static_cast<double>(z.imag());
            mag2[f][t] = re * re + im * im;  // |W|^2
        }
        mag2[f][Ntime_ext - 1] = mag2[f][Ntime - 1];
    }



    for (int f = 0; f < n_freqs; ++f) {
        double s = static_cast<double>(scales_f[f]);
        if (s <= 0.0) {
            continue;
        }
        double factor = 1.0 / s;

        for (int t = 0; t < Ntime_ext; ++t) {
            mag2[f][t] *= factor;
        }
    }

    //  ensure freqs ascending
    if (freqs.size() > 1 && freqs[1] < freqs[0]) {
        std::reverse(freqs.begin(), freqs.end());
        std::reverse(mag2.begin(), mag2.end());

    }

    //  freq range 0.5–4.45 Hz with a step of 0.05 Hz
    std::vector<double> freqs_interp;
    const double f_start = 0.5;
    const double f_end   = 4.5;
    const double step    = 0.05;

    const int nfi = static_cast<int>(std::round((f_end - f_start) / step));
    freqs_interp.reserve(nfi);
    for (int i = 0; i < nfi; ++i) {
        freqs_interp.push_back(f_start + step * i);
    }


    std::vector<std::vector<double>> coefs_interp(
            nfi, std::vector<double>(Ntime_ext, 0.0));

    for (int t = 0; t < Ntime_ext; ++t) {
        std::vector<double> col(n_freqs);
        for (int f = 0; f < n_freqs; ++f) {
            col[f] = mag2[f][t];
        }

        auto col_interp = interp1d(freqs, col, freqs_interp);
        for (int fi = 0; fi < nfi; ++fi) {
            coefs_interp[fi][t] = col_interp[fi];
        }
    }

    // 5) trim spwctogram from the coin of influence
    const int trim = 5 * fs;
    if (Ntime_ext <= 2 * trim) {
        result.freqs_interp = std::move(freqs_interp);
        result.coefs_interp = std::move(coefs_interp);
        return result;
    }

    const int t_start = trim;
    const int t_end   = Ntime_ext - trim;
    const int Ntrim   = t_end - t_start;

    std::vector<std::vector<double>> coefs_trim(
            nfi, std::vector<double>(Ntrim));

    for (int fi = 0; fi < nfi; ++fi) {
        for (int tt = 0; tt < Ntrim; ++tt) {
            coefs_trim[fi][tt] = coefs_interp[fi][t_start + tt];
        }
    }

    result.freqs_interp = std::move(freqs_interp);
    result.coefs_interp = std::move(coefs_trim);

    std::cout << std::scientific << std::setprecision(6);

    return result;
}
