package com.titanium.underwriting.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.metadata.enums.underwriting.MaintenanceUnderwritingConclusion;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.common.exception.UnderwritingException;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.mapper.UnderwritingViewMapper;
import com.titanium.underwriting.query.repository.UnderwritingViewRepository;
import com.titanium.underwriting.query.view.UnderwritingView;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;

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

    @Test
    void maintenanceAssessedCreatesViewWhenRecordAbsentSoMaintenanceCasesReachReadModel() {
        MaintenanceUnderwritingAssessedEvent event = new MaintenanceUnderwritingAssessedEvent(
                new UnderwritingId("UW-100"), "TENANT-001", "MT-100", "POL-100", 3L, "ITEM-100", "IDEM-100",
                "HASH-100", "RULE-V1", "MODEL-V1", MaintenanceUnderwritingConclusion.APPROVED, List.of(), "标准承保",
                LocalDateTime.parse("2026-09-14T09:00:00"), LocalDateTime.parse("2026-09-14T09:30:00"), "uw09");
        when(repository.findById("UW-100")).thenReturn(Optional.empty());

        handler.on(event);

        // 保全核保走 AggregateCreationPolicy.CREATE_IF_MISSING，该事件可能是核保单的首个事件：
        // 必须 upsert 新建读模型记录，沿用「缺失即抛错」会让保全核保单永远进不了读模型
        verify(mapper).applyMaintenanceAssessed(any(UnderwritingView.class), eq(event));
        verify(repository).save(any(UnderwritingView.class));
    }

    @Test
    void inputSubmittedBeforeCreateStillThrowsSoTrackingProcessorCanRetryOrDeadLetter() {
        UnderwritingInputSubmittedEvent event = new UnderwritingInputSubmittedEvent(new UnderwritingId("UW-101"),
                UnderwritingInput.builder().build(), LocalDateTime.now(), "uw01", "TENANT-001");
        when(repository.findById("UW-101")).thenReturn(Optional.empty());

        // 输入事件必然晚于创建事件，缺失记录属真实异常，仍走重试/DLQ 而非静默新建
        assertThrows(UnderwritingException.class, () -> handler.on(event));
    }
}
