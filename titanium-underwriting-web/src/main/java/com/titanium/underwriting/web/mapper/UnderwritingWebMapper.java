package com.titanium.underwriting.web.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.response.UnderwritingResponse;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.ExtraPremium;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.web.vo.UnderwritingVO;

/**
 * 核保 Web 层对象映射器（MapStruct，纯声明式）
 * <p>
 * 读模型结果/领域事件/同步命令快照 → 展示 {@code VO}（Controller 用）/ 对外 {@code UnderwritingResponse}
 * （Provider 用）的同名字段映射，差异字段以 {@code @Mapping(qualifiedByName)} + {@code @Named} 空安全转换
 * 方法声明式表达。边界输入 → 领域命令的装配（含 {@code UnderwritingId.generate()} 等业务决策）已剥离至
 * {@link com.titanium.underwriting.web.assembler.UnderwritingWebAssembler}，本映射器禁止手工组装对象。
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UnderwritingWebMapper {

    // ========== 读模型结果 → 展示/契约对象（MapStruct 同名映射） ==========

    /**
     * 读模型结果 → 展示 VO（Controller 用）
     * <p>
     * dev-505 补丁：结构化加费新字段（{@code extraPremium*}）与 QueryResult 同名自动映射；
     * 旧字段 {@code premiumSurchargeRate}/{@code surchargeReason} 由新字段显式映射兼容前端存量调用方。
     * </p>
     *
     * @param result 读侧查询结果
     * @return 核保 VO
     */
    @Mapping(target = "premiumSurchargeRate", source = "extraPremiumRatio")
    @Mapping(target = "surchargeReason", source = "extraPremiumReason")
    UnderwritingVO toVO(UnderwritingQueryResult result);

    /**
     * 读模型结果 → 对外 Response（Provider 用）
     *
     * @param result 读侧查询结果
     * @return 核保 Response
     */
    UnderwritingResponse toResponse(UnderwritingQueryResult result);

    /**
     * Response → VO（Provider 结果透传给人机终端时复用）
     *
     * @param response 核保 Response
     * @return 核保 VO
     */
    UnderwritingVO toVO(UnderwritingResponse response);

    /**
     * Response 列表 → VO 列表
     *
     * @param responseList 核保 Response 列表
     * @return 核保 VO 列表
     */
    List<UnderwritingVO> toVOList(List<UnderwritingResponse> responseList);

    // ========== 同步命令/事件快照 → 对外 Response（不依赖异步读模型） ==========

    /** 创建命令同步快照，不依赖异步读模型。 */
    @Mapping(target = "underwritingId", source = "underwritingId", qualifiedByName = "underwritingIdValue")
    @Mapping(target = "policyId", source = "policyId", qualifiedByName = "policyIdValue")
    @Mapping(target = "customerId", source = "customerId", qualifiedByName = "customerIdValue")
    @Mapping(target = "amount", source = "amount", qualifiedByName = "amountValue")
    @Mapping(target = "status", constant = "PENDING")
    UnderwritingResponse toResponse(CreateUnderwritingCommand command);

    /** 状态命令同步快照，不依赖异步读模型。 */
    @Mapping(target = "underwritingId", source = "underwritingId", qualifiedByName = "underwritingIdValue")
    @Mapping(target = "status", source = "newStatus")
    @Mapping(target = "rejectReason", source = "event", qualifiedByName = "rejectReasonOf")
    @Mapping(target = "reviewComments", source = "event", qualifiedByName = "reviewCommentsOf")
    @Mapping(target = "updatedBy", source = "changedBy")
    UnderwritingResponse toResponse(UnderwritingStatusChangedEvent event);

    /** 输入提交命令同步快照，不依赖异步读模型。 */
    @Mapping(target = "underwritingId", source = "underwritingId", qualifiedByName = "underwritingIdValue")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "updatedBy", source = "submittedBy")
    UnderwritingResponse toResponse(UnderwritingInputSubmittedEvent event);

    /** 最终决策命令同步快照，为调用方提供权威核保结论。 */
    @Mapping(target = "underwritingId", source = "underwritingId", qualifiedByName = "underwritingIdValue")
    @Mapping(target = "policyId", source = "policyId", qualifiedByName = "policyIdValue")
    @Mapping(target = "status", source = "newStatus")
    @Mapping(target = "updatedBy", source = "decidedBy")
    @Mapping(target = "rejectReason", source = "event", qualifiedByName = "rejectReasonOfDecided")
    @Mapping(target = "reviewComments", source = "event", qualifiedByName = "reviewCommentsOfDecided")
    @Mapping(target = "extraPremiumType", source = "extraPremium", qualifiedByName = "extraPremiumTypeCode")
    @Mapping(target = "extraPremiumRatio", source = "extraPremium", qualifiedByName = "extraPremiumRatio")
    @Mapping(target = "extraPremiumFixedAmount", source = "extraPremium",
            qualifiedByName = "extraPremiumFixedAmount")
    @Mapping(target = "surchargeReason", source = "extraPremium", qualifiedByName = "extraPremiumReason")
    UnderwritingResponse toResponse(UnderwritingDecidedEvent event);

    // ========== 差异字段空安全转换（@Named，仅被上述声明式映射引用） ==========

    /** 核保ID值对象 → 字符串标识。 */
    @Named("underwritingIdValue")
    default String underwritingIdValue(UnderwritingId id) {
        return id == null ? null : id.value();
    }

    /** 保单ID值对象 → 字符串标识。 */
    @Named("policyIdValue")
    default String policyIdValue(PolicyId id) {
        return id == null ? null : id.value();
    }

    /** 客户ID值对象 → 字符串标识。 */
    @Named("customerIdValue")
    default String customerIdValue(CustomerId id) {
        return id == null ? null : id.value();
    }

    /** 核保金额值对象 → 金额数值。 */
    @Named("amountValue")
    default BigDecimal amountValue(UnderwritingAmount amount) {
        return amount == null ? null : amount.amount();
    }

    /** 拒保/撤单状态 → 拒绝原因，其余状态返回 null。 */
    @Named("rejectReasonOf")
    default String rejectReasonOf(UnderwritingStatusChangedEvent event) {
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
            return event.reason();
        }
        return null;
    }

    /** 非拒保状态 → 审核意见，拒保/撤单状态返回 null。 */
    @Named("reviewCommentsOf")
    default String reviewCommentsOf(UnderwritingStatusChangedEvent event) {
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
            return null;
        }
        return event.reason();
    }

    /** 决策拒保 → 拒保原因（规则引擎原因），其余状态返回 null。 */
    @Named("rejectReasonOfDecided")
    default String rejectReasonOfDecided(UnderwritingDecidedEvent event) {
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
            return event.reason();
        }
        return null;
    }

    /** 决策转人工 → 审核意见（规则引擎原因），其余状态返回 null。 */
    @Named("reviewCommentsOfDecided")
    default String reviewCommentsOfDecided(UnderwritingDecidedEvent event) {
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW) {
            return event.reason();
        }
        return null;
    }

    /** 加费明细 → 加费类型 code（空安全）。 */
    @Named("extraPremiumTypeCode")
    default String extraPremiumTypeCode(ExtraPremium extraPremium) {
        if (extraPremium == null || extraPremium.type() == null) {
            return null;
        }
        return extraPremium.type().getCode();
    }

    /** 加费明细 → 加费率（空安全）。 */
    @Named("extraPremiumRatio")
    default BigDecimal extraPremiumRatio(ExtraPremium extraPremium) {
        return extraPremium == null ? null : extraPremium.ratio();
    }

    /** 加费明细 → 固定加费额（空安全）。 */
    @Named("extraPremiumFixedAmount")
    default BigDecimal extraPremiumFixedAmount(ExtraPremium extraPremium) {
        return extraPremium == null ? null : extraPremium.fixedAmount();
    }

    /** 加费明细 → 加费原因（空安全）。 */
    @Named("extraPremiumReason")
    default String extraPremiumReason(ExtraPremium extraPremium) {
        return extraPremium == null ? null : extraPremium.reason();
    }
}
