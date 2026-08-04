package app.banking.frauddetectionservice.engine;

import app.banking.frauddetectionservice.dto.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionEngine {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${fruad.max-transactions-per-minute}")
    private int maxTransactionsPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;


    public FraudCheckResult performFraudChecks(
            String accountNumber,
            BigDecimal amount,
            BigDecimal senderBalance){

        if(isVelocityExceeded(accountNumber)){
            return new FraudCheckResult(
                    true,
                    "Too many transactions in 60 seconds -> Velocity limit exceeded");
        }

        if(isAmountSuspicious(accountNumber, amount)){
            return new FraudCheckResult(
                    true,
                    "Unusual transaction amount -> exceeds 3x your average");
        }

        if(senderBalance.compareTo(BigDecimal.ZERO)>0 && isBalanceCheckFailed(senderBalance, amount)){
            return new FraudCheckResult(
                    true,
                    "Transaction exceed 90% of account balance");
        }


        return new FraudCheckResult(false, null);

    }

    private boolean isVelocityExceeded(String accountNumber) {
        String key = "fraud:velocity" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);

        if(count != null && count == 1){
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }

        log.info("Velocity check - account: {} count: {}/{}",
                accountNumber, count, maxTransactionsPerMinute);

        return count != null && count > maxTransactionsPerMinute;
    }

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {
        String avgKey = "fraud:avg_amount" + accountNumber;
        String avgStr = redisTemplate.opsForValue().get(avgKey);

        if(avgStr == null){
            redisTemplate.opsForValue().set(avgKey, amount.toString());
            return false;
        }

        BigDecimal avgAmount = new BigDecimal(avgStr);
        BigDecimal threshold = avgAmount.multiply(
                BigDecimal.valueOf(suspiciousAmountMultiplier));

        BigDecimal newAvg = avgAmount.add(amount)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        redisTemplate.opsForValue().set(avgKey, newAvg.toString());

        log.info("Amount check - amount: {} threshold: {} suspicious: {}",
                amount, threshold, amount.compareTo(threshold) > 0);

        return amount.compareTo(threshold) > 0;
    }

    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount) {
        BigDecimal maxAllowed = senderBalance.multiply(
                BigDecimal.valueOf(maxBalancePercentage));

        log.info("Balance check - amount: {} maxAllowed: {} suscpious: {}",
                amount, maxAllowed, amount.compareTo(maxAllowed) > 0);

        return amount.compareTo(maxAllowed) > 0;

    }

}
