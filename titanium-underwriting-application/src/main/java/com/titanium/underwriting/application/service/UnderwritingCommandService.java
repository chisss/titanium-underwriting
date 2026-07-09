package com.titanium.underwriting.application.service;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.ManualReviewCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;

import lombok.RequiredArgsConstructor;

/**
 * 核保命令服务（写侧应用入口门面）
 * <p>
 * 作为写用例入口：入参即领域命令，仅经 {@link CommandGateway} 派发，保持门面「薄」。
 * Request/DTO → 领域命令的协议翻译已上移至 web 层（{@code UnderwritingWebMapper}），
 * application 门面不再依赖 api 契约（DTO/Request），也不做读模型到 DTO 的组装
 * （读侧对外表示由 web 层完成），彻底切断对 api 层的编译期依赖（项目规约 3.4.8 ①）。
 * </p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UnderwritingCommandService {

    private final CommandGateway commandGateway;

    /**
     * 创建核保
     *
     * @param command 创建核保命令
     * @return 新建核保ID
     */
    public String createUnderwriting(CreateUnderwritingCommand command) {
        return commandGateway.sendAndWait(command);
    }

    /**
     * 执行核保
     *
     * @param command 执行核保命令
     */
    public void underwrite(UnderwriteCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 手动审核
     *
     * @param command 手动审核命令
     */
    public void manualReview(ManualReviewCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 提交险种专属核保输入（健康告知/体检/职业/车辆风险）
     *
     * @param command 提交核保输入命令
     */
    public void submitInput(SubmitUnderwritingInputCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 触发核保决策
     * <p>
     * 基于已提交的结构化输入与核保金额，由核保聚合根内聚决策产出结论
     * （ACCEPT/MODIFY/REJECT/POSTPONE）与风险等级。
     * </p>
     *
     * @param command 核保决策命令
     */
    public void decide(DecideUnderwritingCommand command) {
        commandGateway.sendAndWait(command);
    }
}
