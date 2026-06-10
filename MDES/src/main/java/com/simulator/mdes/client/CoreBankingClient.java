package com.simulator.mdes.client;

import com.simulator.mdes.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign HTTP client for all TSP → Core Banking service-to-service calls.
 *
 * <p>Every request is automatically decorated with an internal JWT bearer token by
 * {@link FeignConfig.InternalJwtRequestInterceptor}, satisfying the Core Banking
 * {@link com.simulator.mdes.config.SecurityConfig} validation requirements.
 *
 * <p>The base URL is resolved from the environment variable
 * {@code CORE_BANKING_BASE_URL} (default: {@code http://core-banking:8082}).
 */
@FeignClient(
        name = "core-banking",
        url = "${core.banking.base-url:http://core-banking:8082}",
        configuration = FeignConfig.class
)
public interface CoreBankingClient {

    // ─────────────────────────────────────────────────────────────────────
    // Provisioning flow
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Step 2 of provisioning: verify the card identity and obtain OTP delivery options.
     * Corresponds to the EMVCo {@code authorizeService} API.
     *
     * @param request encapsulated request body — contains panUniqueReference, deviceInfo, walletId
     * @return bank decision (expected: {@code REQUIRE_ACTIVATION}) + activation methods
     */
    @PostMapping("/api/v1/core/authorizeService")
    AuthorizeServiceResponse authorizeService(@RequestBody AuthorizeServiceRequest request);

    /**
     * Step 5 of provisioning: instruct the bank to deliver the OTP to the cardholder.
     *
     * @param request contains {@code activationMethodId} from the previous step
     * @return simple success/failure wrapper
     */
    @PostMapping("/api/v1/core/deliverActivationCode")
    DeliverActivationCodeResponse deliverActivationCode(@RequestBody DeliverActivationCodeRequest request);

    /**
     * Step 8 of provisioning: notify the bank that token provisioning is complete.
     * Allows the bank to update its internal card-token mapping.
     *
     * @param request contains the issued DPAN and {@code tokenUniqueReference}
     * @return acknowledgement from the bank
     */
    @PostMapping("/api/v1/core/notifyServiceActivated")
    NotifyServiceActivatedResponse notifyServiceActivated(@RequestBody NotifyServiceActivatedRequest request);

    // ─────────────────────────────────────────────────────────────────────
    // Transaction authorisation flow
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Step 4 of the transaction flow: forward the de-tokenised payment to the bank
     * for balance check and ledger debit.
     *
     * @param request contains {@code panUniqueReference}, amount, currency, merchantId
     * @return bank decision (APPROVED / DECLINED) + authorization code
     */
    @PostMapping("/api/v1/core/authorizeTransaction")
    AuthorizeTransactionResponse authorizeTransaction(@RequestBody AuthorizeTransactionRequest request);

    // ─────────────────────────────────────────────────────────────────────
    // Inner DTOs — request / response records for Core Banking calls.
    // Kept as inner types here to avoid package explosion; can be moved to
    // a dedicated com.simulator.mdes.client.dto package if they grow large.
    // ─────────────────────────────────────────────────────────────────────

    /** Wraps the {@code authorizeService} request body per the EMVCo-style envelope. */
    record AuthorizeServiceRequest(AuthorizeServicePayload authorizeServiceRequest) {}

    record AuthorizeServicePayload(
            String requestId,
            String tokenRequestorId,
            String panUniqueReference,
            FundingAccountInfo fundingAccountInfo,
            DeviceInfo deviceInfo,
            String walletId,
            String tokenType
    ) {}

    record FundingAccountInfo(EncryptedPayload encryptedPayload) {}
    record EncryptedPayload(String encryptedData, String publicKeyFingerprint, String encryptedKey) {}
    record DeviceInfo(String deviceName, String deviceType, String deviceId, String osVersion) {}

    /** Bank response from {@code authorizeService}. */
    record AuthorizeServiceResponse(AuthorizeServiceResult authorizeServiceResponse) {}

    record AuthorizeServiceResult(
            String responseId,
            String decision,
            java.util.List<ActivationMethod> activationMethods
    ) {}

    record ActivationMethod(
            String activationMethodType,
            String activationMethodId,
            String activationMethodValue
    ) {}

    // ─── deliverActivationCode ──────────────────────────────────────────

    record DeliverActivationCodeRequest(String panUniqueReference, String activationMethodId) {}

    record DeliverActivationCodeResponse(String result) {}

    // ─── notifyServiceActivated ─────────────────────────────────────────

    record NotifyServiceActivatedRequest(
            String tokenUniqueReference,
            String tokenValue,
            String panUniqueReference,
            String walletId
    ) {}

    record NotifyServiceActivatedResponse(String result) {}

    // ─── authorizeTransaction ───────────────────────────────────────────

    record AuthorizeTransactionRequest(AuthorizeTransactionPayload authorizeTransactionRequest) {}

    record AuthorizeTransactionPayload(
            String requestId,
            String panUniqueReference,
            java.math.BigDecimal amount,
            String currency,
            String merchantId,
            String merchantName,
            String merchantCategoryCode,
            String posEntryMode
    ) {}

    record AuthorizeTransactionResponse(AuthorizeTransactionResult authorizeTransactionResponse) {}

    record AuthorizeTransactionResult(
            String responseId,
            String decision,
            String authorizationCode,
            java.math.BigDecimal availableBalance
    ) {}
}
