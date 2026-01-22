//package com.titanium.underwriting.query.service.impl;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.when;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//
//import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
//import com.titanium.underwriting.domain.aggregate.Underwriting;
//import com.titanium.underwriting.domain.repository.UnderwritingQueryRepository;
//import com.titanium.underwriting.domain.valueobject.CustomerId;
//import com.titanium.underwriting.domain.valueobject.PolicyId;
//import com.titanium.underwriting.domain.valueobject.UnderwritingAmount;
//import com.titanium.underwriting.domain.valueobject.UnderwritingId;
//import com.titanium.underwriting.query.entity.UnderwritingQueryResult;
//import com.titanium.underwriting.query.entity.UnderwritingStatisticsResult;
//import com.titanium.underwriting.query.mapper.UnderwritingQueryMapper;
//
///**
// * 核保查询服务实现类单元测试
// * 符合项目规约第18条：针对application层、domain层、infrastructure层需要有对应的单元测试类进行测试
// */
//@DisplayName("核保查询服务实现类测试")
//class UnderwritingQueryServiceImplTest {
//
//    @Mock
//    private UnderwritingQueryRepository  underwritingQueryRepository;
//    @InjectMocks
//    private UnderwritingQueryServiceImpl underwritingQueryService;
//    private UnderwritingQueryResult      testEntity;
//    private Underwriting                 testUnderwriting;
//    private String                       testTenantId;
//    private Pageable                     testPageable;
//
//    @BeforeEach
//    void setUp() {
//        // 1. 首先初始化模拟对象
//        MockitoAnnotations.openMocks(this);
//
//        testTenantId = "tenant-001";
//        testPageable = PageRequest.of(0, 10);
//
//        testEntity = new UnderwritingQueryResult();
//        testEntity.setUnderwritingId("UW202401001");
//        testEntity.setPolicyId("POL202401001");
//        testEntity.setCustomerId("CUST202401001");
//        testEntity.setAmount(BigDecimal.valueOf(500000));
//        testEntity.setStatus(UnderwritingEnum.UnderwritingStatus.APPROVED);
//        testEntity.setRiskLevel(UnderwritingEnum.RiskLevel.HIGH_RISK);
//        testEntity.setAuditType(UnderwritingEnum.AuditType.AUTOMATIC);
//        testEntity.setTenantId(testTenantId);
//        testEntity.setCreatedAt(LocalDateTime.now());
//        testEntity.setCreatedBy("system");
//        testEntity.setUpdatedAt(LocalDateTime.now());
//        testEntity.setUpdatedBy("system");
//
//        // 初始化testUnderwriting
//        testUnderwriting = Underwriting.builder().underwritingId(new UnderwritingId("UW202401001"))
//                .policyId(new PolicyId("POL202401001")).customerId(new CustomerId("CUST202401001"))
//                .amount(new UnderwritingAmount(BigDecimal.valueOf(500000)))
//                .status(UnderwritingEnum.UnderwritingStatus.APPROVED).createdAt(LocalDateTime.now()).createdBy("system")
//                .updatedAt(LocalDateTime.now()).updatedBy("system").build();
//
//    }
//
//    @Test
//    @DisplayName("根据风险等级查询核保信息")
//    void testFindByRiskLevelAndTenantId() {
//        // Given
//        UnderwritingEnum.RiskLevel riskLevel = UnderwritingEnum.RiskLevel.HIGH_RISK;
//        Page<Underwriting> expectedPage = new PageImpl<>(Collections.singletonList(testUnderwriting), testPageable, 1L);
//
//        when(underwritingQueryRepository.findByRiskLevelAndTenantId(riskLevel, testTenantId, testPageable))
//                .thenReturn(expectedPage);
//
//        // When
//        Page<UnderwritingQueryResult> result = underwritingQueryService.retriveByRiskLevelAndTenantId(riskLevel,
//                testTenantId, testPageable);
//
//        // Then
//        assertThat(result).isNotNull();
//        assertThat(result.getContent()).hasSize(1);
//        assertThat(result.getContent().getFirst().getRiskLevel()).isEqualTo(riskLevel);
//        assertThat(result.getContent().getFirst().getTenantId()).isEqualTo(testTenantId);
//    }
//
//    @Test
//    @DisplayName("根据核保员和时间范围查询核保信息")
//    void testFindByUnderwriterAndTimeRange() {
//        // Given
//        String underwriterId = "underwriter-001";
//        LocalDateTime startTime = LocalDateTime.now().minusDays(7);
//        LocalDateTime endTime = LocalDateTime.now();
//
//        Page<Underwriting> expectedPage = new PageImpl<>(Collections.singletonList(testUnderwriting), testPageable, 1L);
//
//        when(underwritingQueryRepository.findByUnderwriterAndTimeRange(underwriterId, startTime, endTime, testTenantId,
//                testPageable)).thenReturn(expectedPage);
//
//        // When
//        Page<UnderwritingQueryResult> result = underwritingQueryService.findByUnderwriterAndTimeRange(underwriterId,
//                startTime, endTime, testTenantId, testPageable);
//
//        // Then
//        assertThat(result).isNotNull();
//        assertThat(result.getContent()).hasSize(1);
//        assertThat(result.getContent().get(0).getTenantId()).isEqualTo(testTenantId);
//    }
//
//    @Test
//    @DisplayName("多条件组合查询核保信息")
//    void testFindByMultipleConditions() {
//        // Given
//        UnderwritingEnum.UnderwritingStatus status = UnderwritingEnum.UnderwritingStatus.APPROVED;
//        UnderwritingEnum.RiskLevel riskLevel = UnderwritingEnum.RiskLevel.STANDARD;
//        UnderwritingEnum.AuditType auditType = UnderwritingEnum.AuditType.AUTOMATIC;
//        String underwriterId = "underwriter-001";
//        LocalDateTime startTime = LocalDateTime.now().minusDays(7);
//        LocalDateTime endTime = LocalDateTime.now();
//
//        // 更新testUnderwriting和testEntity的风险等级以匹配测试条件
//        testUnderwriting = Underwriting.builder().underwritingId(new UnderwritingId("UW202401001"))
//                .policyId(new PolicyId("POL202401001")).customerId(new CustomerId("CUST202401001"))
//                .amount(new UnderwritingAmount(BigDecimal.valueOf(500000)))
//                .status(UnderwritingEnum.UnderwritingStatus.APPROVED).createdAt(LocalDateTime.now()).createdBy("system")
//                .updatedAt(LocalDateTime.now()).updatedBy("system").build();
//
//        testEntity.setRiskLevel(UnderwritingEnum.RiskLevel.STANDARD);
//
//        Page<Underwriting> expectedPage = new PageImpl<>(Collections.singletonList(testUnderwriting), testPageable, 1L);
//
//        when(underwritingQueryRepository.findByMultipleConditions(status, riskLevel, auditType, underwriterId,
//                startTime, endTime, testTenantId, testPageable)).thenReturn(expectedPage);
//
//        // When
//        Page<UnderwritingQueryResult> result = underwritingQueryService.findByMultipleConditions(status, riskLevel,
//                auditType, underwriterId, startTime, endTime, testTenantId, testPageable);
//
//        // Then
//        assertThat(result).isNotNull();
//        assertThat(result.getContent()).hasSize(1);
//        assertThat(result.getContent().getFirst().getStatus()).isEqualTo(status);
//        assertThat(result.getContent().getFirst().getRiskLevel()).isEqualTo(riskLevel);
//        assertThat(result.getContent().getFirst().getAuditType()).isEqualTo(auditType);
//    }
//
//    @Test
//    @DisplayName("获取核保统计数据")
//    void testGetStatistics() {
//        // Given
//        LocalDateTime startTime = LocalDateTime.now().minusDays(30);
//        LocalDateTime endTime = LocalDateTime.now();
//
//        // 根据UnderwritingQueryMapper.createStatisticsResult的实现，创建正确格式的rawData
//        List<Object[]> rawData = new ArrayList<>();
//        Object[] row = new Object[19]; // 根据createStatisticsResult方法的实现，需要19个元素
//        row[0] = 100L; // totalCount
//        row[1] = 80L; // standardCount
//        row[2] = 15L; // surchargeCount
//        row[3] = 0L; // exclusionCount
//        row[4] = 0L; // postponeCount
//        row[5] = 5L; // declineCount
//        row[6] = 50L; // lowRiskCount
//        row[7] = 30L; // mediumRiskCount
//        row[8] = 20L; // highRiskCount
//        row[9] = 90L; // autoUnderwritingCount
//        row[10] = 10L; // manualUnderwritingCount
//        row[11] = 0L; // hybridUnderwritingCount
//        row[12] = 2.5; // averageProcessingHours
//        row[13] = BigDecimal.valueOf(50000000); // totalAmount
//        row[14] = BigDecimal.valueOf(40000000); // standardAmount
//        row[15] = BigDecimal.valueOf(7500000); // surchargeAmount
//        row[16] = BigDecimal.valueOf(0); // exclusionAmount
//        row[17] = BigDecimal.valueOf(0); // postponeAmount
//        row[18] = BigDecimal.valueOf(2500000); // declineAmount
//        rawData.add(row);
//
//        when(underwritingQueryRepository.getStatisticsRawData(startTime, endTime, testTenantId)).thenReturn(rawData);
//
//        // When
//        UnderwritingStatisticsResult result = underwritingQueryService.getStatistics(startTime, endTime, testTenantId);
//
//        // Then
//        assertThat(result).isNotNull();
//        assertThat(result.getTotalCount()).isEqualTo(100L);
//        assertThat(result.getStandardCount()).isEqualTo(80L);
//        assertThat(result.getSurchargeCount()).isEqualTo(15L);
//        assertThat(result.getDeclineCount()).isEqualTo(5L);
//        assertThat(result.getTenantId()).isEqualTo(testTenantId);
//    }
//
//    // 手动实现UnderwritingQueryMapper接口用于测试
//    private class TestUnderwritingQueryMapper implements UnderwritingQueryMapper {
//        @Override
//        public UnderwritingQueryResult toQueryResult(Underwriting underwriting) {
//            // 直接返回测试用的testEntity
//            return testEntity;
//        }
//
//        @Override
//        public List<UnderwritingQueryResult> toQueryResultList(List<Underwriting> underwritings) {
//            return Collections.singletonList(toQueryResult(underwritings.get(0)));
//        }
//
//        @Override
//        public UnderwritingStatisticsResult createStatisticsResult(LocalDateTime startTime, LocalDateTime endTime,
//                                                                   String tenantId, List<Object[]> rawData) {
//            // 创建一个简单的统计结果
//            UnderwritingStatisticsResult result = new UnderwritingStatisticsResult();
//            result.setStartTime(startTime);
//            result.setEndTime(endTime);
//            result.setTenantId(tenantId);
//
//            if (rawData != null && !rawData.isEmpty()) {
//                Object[] row = rawData.get(0);
//                result.setTotalCount((Long) row[0]);
//                result.setStandardCount((Long) row[1]);
//                result.setSurchargeCount((Long) row[2]);
//                result.setExclusionCount((Long) row[3]);
//                result.setPostponeCount((Long) row[4]);
//                result.setDeclineCount((Long) row[5]);
//            }
//
//            return result;
//        }
//    }
//}
