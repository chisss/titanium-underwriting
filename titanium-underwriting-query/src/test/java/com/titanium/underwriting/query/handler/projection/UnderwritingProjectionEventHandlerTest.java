package com.titanium.underwriting.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.common.exception.UnderwritingException;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.mapper.UnderwritingViewMapper;
import com.titanium.underwriting.query.repository.UnderwritingViewRepository;
import com.titanium.underwriting.valueobject.UnderwritingId;

@ExtendWith(MockitoExtension.class)
class UnderwritingProjectionEventHandlerTest {

    @Mock
    private UnderwritingViewRepository         repository;

    @Mock
    private UnderwritingViewMapper             mapper;

    @InjectMocks
    private UnderwritingProjectionEventHandler handler;

    @Test
    void updateBeforeCreateThrowsSoTrackingProcessorCanRetryOrDeadLetter() {
        UnderwritingStatusChangedEvent event = new UnderwritingStatusChangedEvent(new UnderwritingId("UW-001"),
                UnderwritingEnum.UnderwritingStatus.PENDING, UnderwritingEnum.UnderwritingStatus.APPROVED,
                "自动核保", LocalDateTime.now(), "system", "TENANT-001");
        when(repository.findById("UW-001")).thenReturn(Optional.empty());

        assertThrows(UnderwritingException.class, () -> handler.on(event));
    }
}
