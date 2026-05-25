#include <cmath>
#include <iostream>
#include <numeric>
#include <stdexcept>
#include <string>
#include <vector>

#include "httplib.h"
#include "json.hpp"

using json = nlohmann::json;

struct AnalyticsResult {
    std::string signal;
    double z_score;
    double rolling_mean;
    double rolling_stddev;
    std::string reason;
};

static double round2(double value) {
    return std::round(value * 100.0) / 100.0;
}

static double calculateMean(const std::vector<double>& values) {
    if (values.empty()) {
        throw std::invalid_argument("values is empty");
    }

    double sum = std::accumulate(values.begin(), values.end(), 0.0);
    return sum / static_cast<double>(values.size());
}

static double calculateStdDev(const std::vector<double>& values, double mean) {
    if (values.empty()) {
        throw std::invalid_argument("values is empty");
    }

    double sum = 0.0;
    for (double v : values) {
        double diff = v - mean;
        sum += diff * diff;
    }

    // population stddev
    return std::sqrt(sum / static_cast<double>(values.size()));
}

static AnalyticsResult runZScoreStrategy(const json& req) {
    if (!req.contains("symbol") || !req["symbol"].is_string()) {
        throw std::invalid_argument("symbol is required");
    }
    if (!req.contains("window") || !req["window"].is_number_integer()) {
        throw std::invalid_argument("window is required");
    }
    if (!req.contains("close_prices") || !req["close_prices"].is_array()) {
        throw std::invalid_argument("close_prices is required");
    }
    if (!req.contains("now_price") || !req["now_price"].is_number()) {
        throw std::invalid_argument("now_price is required");
    }

    const std::string symbol = req["symbol"].get<std::string>();
    const int window = req["window"].get<int>();
    const double now_price = req["now_price"].get<double>();

    if (window < 2) {
        throw std::invalid_argument("window must be >= 2");
    }

    std::vector<double> close_prices;
    for (const auto& v : req["close_prices"]) {
        if (!v.is_number()) {
            throw std::invalid_argument("close_prices must contain only numbers");
        }
        close_prices.push_back(v.get<double>());
    }

    if (static_cast<int>(close_prices.size()) < window) {
        throw std::invalid_argument("close_prices size must be >= window");
    }

    std::vector<double> rolling_window(
        close_prices.end() - window,
        close_prices.end()
    );

    const double mean = calculateMean(rolling_window);
    const double stddev = calculateStdDev(rolling_window, mean);

    AnalyticsResult result{};

    result.rolling_mean = round2(mean);
    result.rolling_stddev = round2(stddev);

    if (stddev == 0.0) {
        result.z_score = 0.0;
        result.signal = "HOLD";
        result.reason = "Rolling stddev is zero";
        return result;
    }

    const double z = (now_price - mean) / stddev;
    result.z_score = round2(z);

    if (z >= 2.0) {
        result.signal = "SELL";
        result.reason = "Z-score above upper threshold";
    } else if (z <= -2.0) {
        result.signal = "BUY";
        result.reason = "Z-score below lower threshold";
    } else {
        result.signal = "HOLD";
        result.reason = "Z-score within normal range";
    }

    // if (z >= 2.0) {
    //     result.signal = "BUY";
    //     result.reason = "Z-score above upper threshold";
    // } else if (z <= -2.0) {
    //     result.signal = "BUY";
    //     result.reason = "Z-score below lower threshold";
    // } else {
    //     result.signal = "BUY";
    //     result.reason = "Z-score within normal range";
    // }

    return result;
}

int main() {
    httplib::Server server;

    server.Post("/signal/zscore", [](const httplib::Request& req, httplib::Response& res) {
        try {
            json body = json::parse(req.body);

            AnalyticsResult result = runZScoreStrategy(body);

            json response = {
                {"signal", result.signal},
                {"z_score", result.z_score},
                {"rolling_mean", result.rolling_mean},
                {"rolling_stddev", result.rolling_stddev},
                {"reason", result.reason}
            };

            res.set_content(response.dump(), "application/json");
            res.status = 200;
        } catch (const std::exception& e) {
            json error = {
                {"error", e.what()}
            };
            res.set_content(error.dump(), "application/json");
            res.status = 400;
        }
    });

    std::cout << "C++ zscore server started at http://0.0.0.0:9000\n";
    server.listen("0.0.0.0", 9000);
    return 0;
}