package com.fcareer;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Entegrasyon testleri için ek bean'ler. OPS-02'den beri boş: Redis yerine geçen bileşenler (Postgres token depoları, bellek içi sayaç ve
 * önbellek) testte de üretimdekiyle aynı çalışır.
 */
@TestConfiguration
public class TestSupportConfiguration {
}
