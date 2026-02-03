package ru.hse.muffin.wallet.server.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.hse.muffin.wallet.server.dto.CurrencyRate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyRateClient {

  private final WebClient webClient;

  @Value("${muffin.currency.url}")
  private String currencyServiceUrl;

  /**
   * Получить курс обмена валют из сервиса muffin-currency
   *
   * @param from валюта-источник (например, CARAMEL)
   * @param to валюта-назначение (например, CHOKOLATE)
   * @return курс обмена
   */
  public CurrencyRate getCurrencyRate(String from, String to) {
    log.info("Requesting currency rate from {} to {}", from, to);
    
    try {
      String url = String.format("http://%s/rate?from=%s&to=%s", 
          currencyServiceUrl, from, to);
      
      log.info("Calling currency service: {}", url);
      
      CurrencyRate rate = webClient
          .get()
          .uri(url)
          .retrieve()
          .bodyToMono(CurrencyRate.class)
          .block();

      log.info("Received currency rate: {}", rate);
      return rate;
      
    } catch (Exception e) {
      log.error("Failed to get currency rate from {} to {}", from, to, e);
      throw new RuntimeException("Failed to fetch currency rate", e);
    }
  }
}
