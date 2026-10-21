package com.example.demo.pricing;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Котировки: цена, километры и геометрия маршрута, посчитанные на сервере,
 * хранятся под случайным id.
 *
 * Клиент получает quoteId и возвращает его при создании заказа, поэтому
 * подменить цену или километры он не может.
 *
 * Хранилище — в памяти процесса (сервер работает в одном экземпляре).
 * Если появится несколько экземпляров, этот класс нужно перевести на Redis.
 */
@Service
public class QuoteService {

    private final Map<String, StoredQuote> quotes = new ConcurrentHashMap<>();

    @Value("${quote.ttl-minutes:10}")
    private long ttlMinutes;

    public String create(int price, double lengthKm, String shape) {
        cleanupIfNeeded();

        String id = UUID.randomUUID().toString();
        quotes.put(id, new StoredQuote(
                price,
                lengthKm,
                shape == null ? "" : shape,
                Instant.now().plus(Duration.ofMinutes(ttlMinutes))
        ));
        return id;
    }

    public Quote find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        StoredQuote stored = quotes.get(id);
        if (stored == null) {
            return null;
        }
        if (stored.expiresAt().isBefore(Instant.now())) {
            quotes.remove(id);
            return null;
        }
        return new Quote(stored.price(), stored.lengthKm(), stored.shape());
    }

    private void cleanupIfNeeded() {
        if (quotes.size() < 1000) {
            return;
        }
        Instant now = Instant.now();
        quotes.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record Quote(int price, double lengthKm, String shape) {
    }

    private record StoredQuote(int price, double lengthKm, String shape, Instant expiresAt) {
    }
}
