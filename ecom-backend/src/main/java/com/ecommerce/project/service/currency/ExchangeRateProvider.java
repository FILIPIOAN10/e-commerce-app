package com.ecommerce.project.service.currency;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A source of USD-based exchange rates. Each implementation is a {@code @Component};
 * {@link ExchangeRateProviderRegistry} injects them all and, ordered by
 * {@code @Order}, asks the first that {@link #supports(String) supports} the base
 * currency — falling through to the next if it fails — the same Strategy-registry
 * shape as {@code PaymentGatewayRegistry}.
 *
 * <p>The floor of the stack is always {@code FixedRateProvider}, so a lookup
 * never has zero providers.
 */
public interface ExchangeRateProvider {

    /** For logs and diagnostics. */
    String name();

    /** Whether this provider can quote rates against {@code baseCurrency}. */
    boolean supports(String baseCurrency);

    /**
     * Units of each quote currency per one unit of {@code baseCurrency}, including
     * the base itself at {@code 1}. Throws if the source is unreachable or the
     * response is unusable — the registry then tries the next provider.
     */
    Map<String, BigDecimal> ratesFor(String baseCurrency);
}
