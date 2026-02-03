package ru.hse.muffin.wallet.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRate {
  
  private String from;
  
  private String to;
  
  private Double rate;
}
