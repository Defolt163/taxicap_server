package com.example.demo.pricing;

import java.time.LocalTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Расчёт стоимости поездки.
 *
 * price = max(minFare, round(baseFare + perKm * km + надбавки, roundTo))
 */
@Service
public class TariffService {

    @Value("${tariff.base-fare:90}")
    private int baseFare;

    @Value("${tariff.per-km:15}")
    private double perKm;

    @Value("${tariff.min-fare:110}")
    private int minFare;

    @Value("${tariff.round-to:10}")
    private int roundTo;

    @Value("${tariff.out-of-zone-surcharge:50}")
    private int outOfZoneSurcharge;

    @Value("${tariff.night-multiplier:1.0}")
    private double nightMultiplier;

    @Value("${tariff.night-from:22:00}")
    private String nightFrom;

    @Value("${tariff.night-to:06:00}")
    private String nightTo;

    @Value("${spring.geoborder}")
    private String geoborder;

    public int calculate(double lengthKm, boolean outOfZone) {
        return calculate(lengthKm, outOfZone, LocalTime.now());
    }

    public int calculate(double lengthKm, boolean outOfZone, LocalTime at) {
        double raw = baseFare + perKm * Math.max(0, lengthKm);
        if (outOfZone) {
            raw += outOfZoneSurcharge;
        }
        if (isNight(at)) {
            raw *= nightMultiplier;
        }
        int price = (int) Math.round(raw / roundTo) * roundTo;
        return Math.max(minFare, price);
    }

    private boolean isNight(LocalTime at) {
        LocalTime from = LocalTime.parse(nightFrom);
        LocalTime to = LocalTime.parse(nightTo);
        if (from.isAfter(to)) {
            // интервал через полночь: 22:00 - 06:00
            return at.isAfter(from) || at.isBefore(to);
        }
        return at.isAfter(from) && at.isBefore(to);
    }

    /**
     * Точка вне зоны обслуживания (spring.geoborder).
     * Формат geoborder: lon1,lat1,lon2,lat2 — порядок может быть любым.
     */
    public boolean isOutsideServiceArea(double lat, double lon) {
        String[] parts = geoborder.split(",");
        double lon1 = Double.parseDouble(parts[0].trim());
        double lat1 = Double.parseDouble(parts[1].trim());
        double lon2 = Double.parseDouble(parts[2].trim());
        double lat2 = Double.parseDouble(parts[3].trim());

        double minLat = Math.min(lat1, lat2);
        double maxLat = Math.max(lat1, lat2);
        double minLon = Math.min(lon1, lon2);
        double maxLon = Math.max(lon1, lon2);

        return lat < minLat || lat > maxLat || lon < minLon || lon > maxLon;
    }
}
