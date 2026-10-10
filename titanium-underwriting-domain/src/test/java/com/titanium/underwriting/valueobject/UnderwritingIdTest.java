package com.titanium.underwriting.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 核保单号值对象测试（g02-04 / AC-05 幂等的地基）
 * <p>
 * {@link UnderwritingId#forAutoDecide(String, String)} 是「同键重放不产生第二张核保单」这条验收判据的
 * <b>唯一依托</b>：它把「同一投保单」映射到「同一聚合标识」，从而使幂等不再依赖 CQRS 读模型的反查
 * （读模型由异步投影维护，滞后时反查必然落空）。故其派生性质必须逐条锁死——这些性质一旦被破坏，
 * 幂等不会编译失败、也不会在任何单测里显形，只会在真机上以「多出一张核保单」的形式出现。
 * </p>
 */
class UnderwritingIdTest {

    private static final String TENANT_ID    = "TENANT-001";
    private static final String INSURANCE_ID = "INS-001";

    @Test
    void derivesSameIdForSameTenantAndInsuranceSoRepeatedCallsLandOnOneAggregate() {
        // AC-05 的地基：同键必得同号。若此处不等，上游 Saga 的每次重试都会落到一张新核保单上
        assertEquals(UnderwritingId.forAutoDecide(TENANT_ID, INSURANCE_ID),
                UnderwritingId.forAutoDecide(TENANT_ID, INSURANCE_ID));
    }

    @Test
    void derivesDifferentIdsForDifferentInsuranceSoDistinctProposalsStayIndependent() {
        assertNotEquals(UnderwritingId.forAutoDecide(TENANT_ID, INSURANCE_ID),
                UnderwritingId.forAutoDecide(TENANT_ID, "INS-002"));
    }

    @Test
    void derivesDifferentIdsForDifferentTenantsSoSameProposalInTwoTenantsStaysSeparate() {
        // 多租户隔离：投保单号在租户间不保证全局唯一，故租户必须参与派生
        assertNotEquals(UnderwritingId.forAutoDecide(TENANT_ID, INSURANCE_ID),
                UnderwritingId.forAutoDecide("TENANT-002", INSURANCE_ID));
    }

    @Test
    void usesItsOwnPrefixSoAutoDecidedCasesNeverCollideWithMaintenanceOrSnowflakeOnes() {
        UnderwritingId autoDecided  = UnderwritingId.forAutoDecide(TENANT_ID, INSURANCE_ID);
        UnderwritingId maintenance  = UnderwritingId.forMaintenance(TENANT_ID, INSURANCE_ID);

        assertTrue(autoDecided.value().startsWith("AUW-"));
        assertTrue(maintenance.value().startsWith("MUW-"));
        // 同一幂等键在两个用途下必须得到不同的号：前缀不同即是保证（两个派生源同形，仅前缀可区分）
        assertNotEquals(autoDecided, maintenance);
        // 🔴 核保单号不是纯数字（本端点产出 AUW- 前缀串）——全仓不得对其作数字解析；
        // policy 域跨域契约中一律为 String，已核验无 parseLong 类用法
        assertEquals(autoDecided.value(), autoDecided.toString());
    }
}
