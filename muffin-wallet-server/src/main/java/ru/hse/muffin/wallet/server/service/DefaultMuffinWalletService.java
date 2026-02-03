package ru.hse.muffin.wallet.server.service;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hse.muffin.wallet.data.api.MuffinTransactionRepository;
import ru.hse.muffin.wallet.data.api.MuffinWalletRepository;
import ru.hse.muffin.wallet.server.client.CurrencyRateClient;
import ru.hse.muffin.wallet.server.dto.CurrencyRate;
import ru.hse.muffin.wallet.server.dto.MuffinTransaction;
import ru.hse.muffin.wallet.server.dto.MuffinWallet;
import ru.hse.muffin.wallet.server.exception.MuffinWalletNotFoundException;
import ru.hse.muffin.wallet.server.mapper.MuffinWalletMapper;

@Slf4j
@Service
@AllArgsConstructor
public class DefaultMuffinWalletService implements MuffinWalletService {

  private final MuffinWalletMapper muffinWalletMapper;

  private final MuffinWalletRepository muffinWalletRepository;

  private final MuffinTransactionRepository muffinTransactionRepository;

  private final CurrencyRateClient currencyRateClient;

  @Override
  public MuffinWallet getMuffinWallet(UUID id) {
    return muffinWalletMapper.dataDtoToMuffinWalletServiceDto(
        muffinWalletRepository.findById(id).orElseThrow(MuffinWalletNotFoundException::new));
  }

  @Override
  public Page<MuffinWallet> getMuffinWalletsByOwner(String ownerName, Pageable pageable) {
    if (ownerName != null) {
      return getMuffinWalletsByOwnerNameNotNull(ownerName, pageable);
    }

    return getAllMuffinWallets(pageable);
  }

  private Page<MuffinWallet> getMuffinWalletsByOwnerNameNotNull(
      String ownerName, Pageable pageable) {
    return muffinWalletRepository
        .findByOwnerNameLike(ownerName, pageable)
        .map(muffinWalletMapper::dataDtoToMuffinWalletServiceDto);
  }

  private Page<MuffinWallet> getAllMuffinWallets(Pageable pageable) {
    return muffinWalletRepository
        .findAll(pageable)
        .map(muffinWalletMapper::dataDtoToMuffinWalletServiceDto);
  }

  @Override
  public MuffinWallet createMuffinWallet(MuffinWallet muffinWallet) {
    return muffinWalletMapper.dataDtoToMuffinWalletServiceDto(
        muffinWalletRepository.save(
            muffinWalletMapper.serviceDtoToMuffinWalletDataDto(muffinWallet)));
  }

  @Override
  @Transactional
  public MuffinTransaction createMuffinTransaction(MuffinTransaction muffinTransaction) {
    muffinWalletRepository.findByIdInForUpdate(
        List.of(
            muffinTransaction.getFromMuffinWalletId(), muffinTransaction.getToMuffinWalletId()));

    var fromWallet =
        muffinWalletRepository
            .findById(muffinTransaction.getFromMuffinWalletId())
            .orElseThrow(MuffinWalletNotFoundException::new);

    // Списываем сумму с кошелька отправителя
    fromWallet.setBalance(fromWallet.getBalance().subtract(muffinTransaction.getAmount()));
    muffinWalletRepository.update(fromWallet);

    var toWallet =
        muffinWalletRepository
            .findById(muffinTransaction.getToMuffinWalletId())
            .orElseThrow(MuffinWalletNotFoundException::new);

    // Конвертируем сумму, если валюты разные
    BigDecimal amountToAdd = muffinTransaction.getAmount();
    
    if (muffinTransaction.getFromCurrency() != null 
        && muffinTransaction.getToCurrency() != null
        && !muffinTransaction.getFromCurrency().equals(muffinTransaction.getToCurrency())) {
      
      log.info("Currency conversion needed: {} to {}", 
          muffinTransaction.getFromCurrency(), 
          muffinTransaction.getToCurrency());
      
      // Получаем курс обмена из сервиса muffin-currency
      CurrencyRate rate = currencyRateClient.getCurrencyRate(
          muffinTransaction.getFromCurrency(), 
          muffinTransaction.getToCurrency());
      
      log.info("Exchange rate: {} {} = {} {}", 
          1, muffinTransaction.getFromCurrency(), 
          rate.getRate(), muffinTransaction.getToCurrency());
      
      // Применяем курс обмена
      amountToAdd = muffinTransaction.getAmount()
          .multiply(BigDecimal.valueOf(rate.getRate()));
      
      log.info("Converted amount: {} {} -> {} {}", 
          muffinTransaction.getAmount(), muffinTransaction.getFromCurrency(),
          amountToAdd, muffinTransaction.getToCurrency());
    }

    // Зачисляем конвертированную сумму на кошелек получателя
    toWallet.setBalance(toWallet.getBalance().add(amountToAdd));
    muffinWalletRepository.update(toWallet);

    return muffinWalletMapper.dataDtoToMuffinTransactionServiceDto(
        muffinTransactionRepository.save(
            muffinWalletMapper.serviceDtoToMuffinTransactionDataDto(muffinTransaction)));
  }
}
