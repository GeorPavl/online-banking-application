package com.service;

import com._config._helpers._enums.TransactionSource;
import com._config._helpers._enums.TransactionStatus;
import com._config._helpers._enums.TransactionType;
import com.dto.AccountDTO;
import com.dto.PaymentDTO;
import com.dto.TransactionDTO;
import com.dto.UserDTO;
import com.entity.Account;
import com.entity.Transaction;
import com.repository.TransactionRepository;
import javassist.NotFoundException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TransactionServiceImpl implements TransactionService{

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private UserService userService;
    @Autowired
    private MessageSource messageSource;

    private static final Locale locale = LocaleContextHolder.getLocale();
    private static String errorMessage;

    @Override
    public Transaction dtoToEntity(TransactionDTO transactionDTO) {
        Transaction transaction = new Transaction();
        BeanUtils.copyProperties(transactionDTO, transaction);
        if (transactionDTO.getAccountId() != null) {
            Account account = new Account();
            account.setId(transactionDTO.getAccountId());
            transaction.setAccount(account);
        }
        if (transactionDTO.getPaymentDTO() != null) {
            transaction.addPayment(paymentService.dtoToEntity(transactionDTO.getPaymentDTO()));
        }
        return transaction;
    }

    @Override
    public List<TransactionDTO> getTransactionsByUser() throws NotFoundException {
        UserDTO userDTO = userService.getLoggedInUser();
        List<TransactionDTO> list = new ArrayList<>();
        for (Transaction transaction : transactionRepository.getTransactionsById(userDTO.getId())) {
            list.add(new TransactionDTO(transaction));
        }
        return list;
    }

    @Override
    public TransactionDTO get(Long id) throws NotFoundException {
        Optional<Transaction> optionalTransaction = transactionRepository.findById(id);

        if (optionalTransaction.isEmpty()) {
            throw new NotFoundException("Did not found transaction with id: " + id);
        }
        return new TransactionDTO(optionalTransaction.get());
    }

    @Override
    public TransactionDTO save(TransactionDTO transactionDTO) {
        return new TransactionDTO(transactionRepository.save(dtoToEntity(transactionDTO)));
    }

    @Override
    public void delete(Long id) throws NotFoundException {
        if (get(id) != null) {
            transactionRepository.deleteById(id);
        }
    }

    @Override
    public void deposit(TransactionDTO transactionDTO) throws NotFoundException {
        // Check if amount value is grater than zero
        if (transactionDTO.getAmount() == 0) {
            errorMessage = messageSource.getMessage("errors.deposit.zeroAmount", null, locale);
            throw new RuntimeException(errorMessage);
        }
        AccountDTO accountDTO = accountService.get(transactionDTO.getAccountId());
        // Deposit money
        accountDTO.deposit(transactionDTO.getAmount());
        // Save transaction and new balance
        save(new TransactionDTO(transactionDTO.getAccountId(), transactionDTO.getAmount(), TransactionType.DEPOSIT, TransactionStatus.SUCCESS, "Deposit Transaction Successful", TransactionSource.ONLINE));
        accountService.save(accountDTO);
    }

    @Override
    public void withdraw(TransactionDTO transactionDTO) throws NotFoundException {
        AccountDTO accountDTO = accountService.get(transactionDTO.getAccountId());
        if (transactionDTO.getAmount() == 0) {
            errorMessage = messageSource.getMessage("errors.withdraw.zeroAmount", null, locale);
            throw new RuntimeException(errorMessage);
        } else if (accountDTO.getBalance() < transactionDTO.getAmount()) {
            errorMessage = messageSource.getMessage("errors.withdraw.insufficientFunds", null, locale);
            throw new RuntimeException(errorMessage);
        }
        accountDTO.withdraw(transactionDTO.getAmount());
        save(new TransactionDTO(accountDTO.getId(), transactionDTO.getAmount(), TransactionType.WITHDRAW, TransactionStatus.SUCCESS, "Withdrawal Transaction Successful", TransactionSource.ONLINE));
        accountService.save(accountDTO);
    }

    @Override
    public void transfer(TransactionDTO transactionDTO) throws NotFoundException {
        if (transactionDTO.getId() == transactionDTO.getTransferTo()) {
            errorMessage = messageSource.getMessage("errors.transfer.sameAccount", null, locale);
            throw new RuntimeException(errorMessage);
        }
        if (transactionDTO.getAmount() == 0) {
            errorMessage = messageSource.getMessage("errors.transfer.zeroAmount", null, locale);
            throw new RuntimeException(errorMessage);
        }
        AccountDTO transferFromAccount = accountService.get(transactionDTO.getAccountId());
        AccountDTO transferToAccount = accountService.get(transactionDTO.getTransferTo());
        transferFromAccount.transfer(transactionDTO.getAmount(), transferToAccount);
        save(new TransactionDTO(transactionDTO.getAccountId(), transactionDTO.getAmount(), TransactionType.TRANSFER, TransactionStatus.SUCCESS, "Transfer Transaction Successful", TransactionSource.ONLINE));
        accountService.save(transferFromAccount);
        accountService.save(transferToAccount);
    }

    @Override
    public void payment(TransactionDTO transactionDTO) throws NotFoundException {
        if (transactionDTO.getAmount() == 0) {
            errorMessage = messageSource.getMessage("errors.payment.zeroAmount", null, locale);
            throw new RuntimeException(errorMessage);
        }
        AccountDTO paymentFrom = accountService.get(transactionDTO.getAccountId());
        if (paymentFrom.getBalance() < transactionDTO.getAmount()) {
            errorMessage = messageSource.getMessage("errors.payment.insufficientFunds", null, locale);
            throw new RuntimeException(errorMessage);
        }
        paymentFrom.payment(transactionDTO.getAmount());
        PaymentDTO paymentDTO = new PaymentDTO(transactionDTO.getPaymentDTO().getBeneficiary(), transactionDTO.getPaymentDTO().getBeneficiaryAccountNumber(), transactionDTO.getPaymentDTO().getReferenceNumber());
        accountService.save(paymentFrom);
        save(new TransactionDTO(transactionDTO.getAccountId(), transactionDTO.getAmount(), TransactionType.PAYMENT, TransactionStatus.SUCCESS, "Payment Transaction Successful", TransactionSource.ONLINE, paymentDTO));
    }
}
