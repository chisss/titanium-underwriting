package com.titanium.underwriting.application.service;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.underwriting.domain.command.CreateUnderwritingCommand;
import com.titanium.underwriting.domain.command.ManualReviewCommand;
import com.titanium.underwriting.domain.command.UnderwriteCommand;

/**
 * 核保命令服务
 */
@Service
@Transactional
public class UnderwritingCommandService {

    private final CommandGateway commandGateway;

    @Autowired
    public UnderwritingCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    /**
     * 创建核保
     * 
     * @param command 创建核保命令
     * @return 核保ID
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
}
