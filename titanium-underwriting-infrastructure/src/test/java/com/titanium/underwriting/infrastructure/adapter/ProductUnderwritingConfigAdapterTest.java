package com.titanium.underwriting.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.response.config.UnderwritingConfigResponse;
import com.titanium.underwriting.port.ProductUnderwritingConfigPort.ProductUnderwritingConfig;

class ProductUnderwritingConfigAdapterTest {

    @Test
    void shouldQueryProductConfigurationByCode() {
        ProductApi productApi = mock(ProductApi.class);
        when(productApi.getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a"))
                .thenReturn(ApiResponse.success(new UnderwritingConfigResponse(null, null,
                        new BigDecimal("500000"), List.of(), null, false, false)));
        ProductUnderwritingConfigAdapter adapter = new ProductUnderwritingConfigAdapter(productApi);

        ProductUnderwritingConfig config = adapter.fetchConfig("TERM_LIFE_V1", "tenant-a");

        assertEquals(false, config.surchargeAcceptable());
        assertEquals(new BigDecimal("500000"), config.manualReviewAmountThreshold());
        verify(productApi).getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a");
    }
}
