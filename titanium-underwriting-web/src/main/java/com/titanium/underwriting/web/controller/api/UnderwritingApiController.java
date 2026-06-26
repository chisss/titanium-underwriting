
package com.titanium.underwriting.web.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;
import com.titanium.underwriting.application.mapper.UnderwritingMapper;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.domain.command.CreateUnderwritingCommand;
import com.titanium.underwriting.domain.command.UnderwriteCommand;
import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.domain.valueobject.PolicyId;
import com.titanium.underwriting.domain.valueobject.UnderwritingAmount;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;

import lombok.AllArgsConstructor;

/**
 * 核保控制器
 */
@RestController
@AllArgsConstructor
@RequestMapping("/underwritings/web")
public class UnderwritingApiController implements UnderwritingApi {
    private final UnderwritingCommandService  underwritingCommandService;
    private final UnderwritingQueryAppService underwritingQueryAppService;
    private final UnderwritingMapper          underwritingMapper;

    /**
     * 创建核保
     *
     * @param request 创建核保请求
     * @return 创建的核保DTO
     */
    @PostMapping
    public ResponseEntity<UnderwritingDTO> createUnderwriting(@RequestBody CreateUnderwritingRequest request,
                                                              @RequestHeader("X-Tenant-ID") String tenantId) {

        // 构建命令
        // 边界防腐：REST 入参币种 code(String) 转 CurrencyEnum
        CurrencyEnum currency = CurrencyEnum.fromCode(request.getCurrency());
        CreateUnderwritingCommand command = new CreateUnderwritingCommand(UnderwritingId.generate(),
                PolicyId.of(request.getPolicyId()), CustomerId.of(request.getCustomerId()),
                UnderwritingAmount.of(request.getAmount(), currency), request.getUnderwritingType(),
                request.getRequestBy(), tenantId);

        // 执行命令
        String createdId = underwritingCommandService.createUnderwriting(command);

        // 查询创建后的核保
        var underwriting = underwritingQueryAppService.findUnderwritingById(new UnderwritingId(createdId), tenantId)
                .orElseThrow();
        return new ResponseEntity<>(underwritingMapper.toDTO(underwriting), HttpStatus.CREATED);
    }

    /**
     * 根据ID查询核保
     *
     * @param underwritingId 核保ID
     * @return 核保DTO
     */
    @GetMapping("/{underwritingId}")
    public ResponseEntity<UnderwritingDTO> getUnderwritingById(@PathVariable String underwritingId,
                                                               @RequestHeader("X-Tenant-ID") String tenantId) {
        var underwriting = underwritingQueryAppService
                .findUnderwritingById(new UnderwritingId(underwritingId), tenantId).orElseThrow();
        return ResponseEntity.ok(underwritingMapper.toDTO(underwriting));
    }

    /**
     * 执行核保
     *
     * @param underwritingId 核保ID
     * @param request 核保请求
     * @return 更新后的核保DTO
     */
    @PutMapping("/{underwritingId}/underwrite")
    public ResponseEntity<UnderwritingDTO> underwrite(@PathVariable String underwritingId,
                                                      @RequestBody UnderwriteRequest request,
                                                      @RequestHeader("X-Tenant-ID") String tenantId) {
        // 构建命令
        UnderwriteCommand command = new UnderwriteCommand(new UnderwritingId(underwritingId),
                new UnderwritingAmount(request.getAmount()), request.getReason(), request.getUnderwriteBy(), tenantId);

        // 执行命令
        underwritingCommandService.underwrite(command);

        // 查询更新后的核保
        var underwriting = underwritingQueryAppService
                .findUnderwritingById(new UnderwritingId(underwritingId), tenantId).orElseThrow();
        return ResponseEntity.ok(underwritingMapper.toDTO(underwriting));
    }

    public ResponseEntity<List<UnderwritingDTO>> getUnderwritingByPolicyId(String policyId, String tenantId) {
        return null;
    }

    public ResponseEntity<List<UnderwritingDTO>> getUnderwritingsByStatus(String status, String tenantId) {
        // 边界防腐：path 状态 code(String) 转枚举后再查询（当前为桩，查询逻辑待补）
        UnderwritingEnum.UnderwritingStatus underwritingStatus = UnderwritingEnum.UnderwritingStatus.fromCode(status);
        return ResponseEntity.ok(List.of());
    }

    public ResponseEntity<List<UnderwritingDTO>> getAllUnderwritings(String tenantId) {
        return ResponseEntity.ok(List.of());
    }
}
