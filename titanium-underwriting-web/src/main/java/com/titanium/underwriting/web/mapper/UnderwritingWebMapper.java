package com.titanium.underwriting.web.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.common.enums.VehicleUsageType;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.HealthDeclaration;
import com.titanium.underwriting.valueobject.OccupationInfo;
import com.titanium.underwriting.valueobject.PhysicalExamResult;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;
import com.titanium.underwriting.valueobject.VehicleRiskInfo;
import com.titanium.underwriting.web.request.CreateUnderwritingRequest;
import com.titanium.underwriting.web.request.DecideUnderwritingRequest;
import com.titanium.underwriting.web.request.SubmitUnderwritingInputRequest;
import com.titanium.underwriting.web.request.UnderwriteRequest;
import com.titanium.underwriting.web.vo.UnderwritingVO;

/**
 * 核保 Web 层对象映射器（MapStruct）
 * <p>
 * 边界输入到 CQRS 命令的转换枢纽：HTTP {@code Request}（Controller 用）与远程 {@code DTO}（Provider 用）
 * → 领域命令；读模型结果 {@code UnderwritingQueryResult} → 展示 {@code VO}（Controller 用）/ 对外
 * {@code UnderwritingDTO}（Provider 用）。application 门面入参即领域命令，本映射器在 web 层完成
 * Request/DTO → Command 的结构翻译与值对象装配（{@code BigDecimal}+币种 → {@link UnderwritingAmount}、
 * 险种输入 → {@link UnderwritingInput}）。因命令承载带校验的值对象、险种输入需分块判空，命令构造采用
 * default 方法手写装配，读模型→VO/DTO 的同名字段映射交由 MapStruct 生成。
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UnderwritingWebMapper {

    // ========== 读模型结果 → 展示/契约对象（MapStruct 同名映射） ==========

    /**
     * 读模型结果 → 展示 VO（Controller 用）
     *
     * @param result 读侧查询结果
     * @return 核保 VO
     */
    UnderwritingVO toVO(UnderwritingQueryResult result);

    /**
     * 读模型结果 → 对外 DTO（Provider 用）
     *
     * @param result 读侧查询结果
     * @return 核保 DTO
     */
    UnderwritingDTO toDTO(UnderwritingQueryResult result);

    /**
     * DTO → VO（Provider 结果透传给人机终端时复用）
     *
     * @param dto 核保 DTO
     * @return 核保 VO
     */
    UnderwritingVO toVO(UnderwritingDTO dto);

    /**
     * DTO 列表 → VO 列表
     *
     * @param dtoList 核保 DTO 列表
     * @return 核保 VO 列表
     */
    List<UnderwritingVO> toVOList(List<UnderwritingDTO> dtoList);

    // ========== HTTP Request → 领域命令（Controller 用） ==========

    /**
     * 创建核保请求 → 创建核保命令
     *
     * @param request  创建核保请求（web）
     * @param tenantId 租户ID（请求头）
     * @return 创建核保命令
     */
    default CreateUnderwritingCommand toCommand(CreateUnderwritingRequest request, String tenantId) {
        // 边界防腐：REST 入参币种 code(String) 转 CurrencyEnum，装配为带校验的金额值对象
        CurrencyEnum currency = CurrencyEnum.fromCode(request.getCurrency());
        return new CreateUnderwritingCommand(UnderwritingId.generate(), PolicyId.of(request.getPolicyId()),
                CustomerId.of(request.getCustomerId()), UnderwritingAmount.of(request.getAmount(), currency),
                request.getUnderwritingType(), request.getRequestBy(), tenantId);
    }

    /**
     * 执行核保请求 → 执行核保命令
     *
     * @param underwritingId 核保ID（path）
     * @param request        执行核保请求（web）
     * @param tenantId       租户ID（请求头）
     * @return 执行核保命令
     */
    default UnderwriteCommand toCommand(String underwritingId, UnderwriteRequest request, String tenantId) {
        return new UnderwriteCommand(new UnderwritingId(underwritingId), new UnderwritingAmount(request.getAmount()),
                request.getReason(), request.getUnderwriteBy(), tenantId);
    }

    /**
     * 提交核保输入请求 → 提交核保输入命令
     *
     * @param underwritingId 核保ID（path）
     * @param request        提交核保输入请求（web）
     * @param tenantId       租户ID（请求头）
     * @return 提交核保输入命令
     */
    default SubmitUnderwritingInputCommand toCommand(String underwritingId, SubmitUnderwritingInputRequest request,
                                                     String tenantId) {
        return new SubmitUnderwritingInputCommand(new UnderwritingId(underwritingId), toInput(request),
                request.getSubmittedBy(), tenantId);
    }

    /**
     * 核保决策请求 → 核保决策命令
     *
     * @param underwritingId 核保ID（path）
     * @param request        核保决策请求（web）
     * @param tenantId       租户ID（请求头）
     * @return 核保决策命令
     */
    default DecideUnderwritingCommand toCommand(String underwritingId, DecideUnderwritingRequest request,
                                                String tenantId) {
        return new DecideUnderwritingCommand(new UnderwritingId(underwritingId), toAuditType(request.getAuditType()),
                request.getDecidedBy(), tenantId);
    }

    // ========== 远程 DTO → 领域命令（Provider 用） ==========

    /**
     * 远程创建核保请求 → 创建核保命令（Provider 用）
     *
     * @param request  创建核保请求（api 契约）
     * @param tenantId 租户ID（请求头）
     * @return 创建核保命令
     */
    default CreateUnderwritingCommand toCommand(
            com.titanium.underwriting.api.request.CreateUnderwritingRequest request, String tenantId) {
        CurrencyEnum currency = CurrencyEnum.fromCode(request.getCurrency());
        return new CreateUnderwritingCommand(UnderwritingId.generate(), PolicyId.of(request.getPolicyId()),
                CustomerId.of(request.getCustomerId()), UnderwritingAmount.of(request.getAmount(), currency),
                request.getUnderwritingType(), request.getRequestBy(), tenantId);
    }

    /**
     * 远程执行核保请求 → 执行核保命令（Provider 用）
     *
     * @param underwritingId 核保ID（path）
     * @param request        执行核保请求（api 契约）
     * @param tenantId       租户ID（请求头）
     * @return 执行核保命令
     */
    default UnderwriteCommand toCommand(String underwritingId,
                                        com.titanium.underwriting.api.request.UnderwriteRequest request,
                                        String tenantId) {
        return new UnderwriteCommand(new UnderwritingId(underwritingId), new UnderwritingAmount(request.getAmount()),
                request.getReason(), request.getUnderwriteBy(), tenantId);
    }

    // ========== 险种输入装配（分块判空 + 值对象内聚校验） ==========

    /**
     * 提交核保输入请求 → 核保输入容器值对象
     * <p>
     * 四类输入按险种可选填充，未填充的整块保持 {@code null}；各值对象构造器内聚校验，此处仅做分块装配。
     * </p>
     *
     * @param request 提交核保输入请求（web）
     * @return 核保输入容器值对象
     */
    default UnderwritingInput toInput(SubmitUnderwritingInputRequest request) {
        return UnderwritingInput.builder().healthDeclaration(toHealthDeclaration(request.getHealthDeclaration()))
                .physicalExamResult(toPhysicalExamResult(request.getPhysicalExamResult()))
                .occupationInfo(toOccupationInfo(request.getOccupationInfo()))
                .vehicleRiskInfo(toVehicleRiskInfo(request.getVehicleRiskInfo())).build();
    }

    /**
     * 核保方式 code → 枚举（缺省回落自动核保）
     *
     * @param auditTypeCode 核保方式 code（AUTOMATIC/MANUAL/HYBRID）
     * @return 核保方式枚举
     */
    default UnderwritingEnum.AuditType toAuditType(String auditTypeCode) {
        if (auditTypeCode == null || auditTypeCode.isBlank()) {
            return UnderwritingEnum.AuditType.AUTOMATIC;
        }
        return UnderwritingEnum.AuditType.valueOf(auditTypeCode);
    }

    /** 健康告知输入装配（空块跳过） */
    private HealthDeclaration toHealthDeclaration(SubmitUnderwritingInputRequest.HealthDeclarationInput input) {
        if (input == null) {
            return null;
        }
        return new HealthDeclaration(input.getMedicalHistory(), input.getFamilyHistory(), input.isSmoking(),
                input.getHeightCm(), input.getWeightKg());
    }

    /** 体检报告输入装配（空块跳过） */
    private PhysicalExamResult toPhysicalExamResult(SubmitUnderwritingInputRequest.PhysicalExamInput input) {
        if (input == null) {
            return null;
        }
        return new PhysicalExamResult(input.getBmi(), input.getSystolicPressure(), input.getDiastolicPressure(),
                input.getBloodGlucose(), input.getAbnormalItems());
    }

    /** 职业信息输入装配（空块跳过） */
    private OccupationInfo toOccupationInfo(SubmitUnderwritingInputRequest.OccupationInput input) {
        if (input == null) {
            return null;
        }
        return new OccupationInfo(input.getOccupationName(), input.getOccupationCategory(), input.getRiskFactor());
    }

    /** 车辆风险输入装配（空块跳过，使用性质 code 转枚举） */
    private VehicleRiskInfo toVehicleRiskInfo(SubmitUnderwritingInputRequest.VehicleRiskInput input) {
        if (input == null) {
            return null;
        }
        return new VehicleRiskInfo(input.getVehicleAgeYears(), VehicleUsageType.valueOf(input.getUsageNature()),
                input.getHistoricalClaimCount(), input.getNcdFactor());
    }
}
