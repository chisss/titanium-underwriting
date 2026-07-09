package com.titanium.underwriting.common.constant;

/**
 * Underwriting Service Constants
 */
public class UnderwritingConstants {
    public static final String UNDERWRITING_SERVICE_NAME  = "titanium-underwriting";
    public static final String UNDERWRITING_TOPIC         = "underwriting-events";
    /** 核保决策完成事件 topic（跨域异步回流 policy 域） */
    public static final String TOPIC_UNDERWRITING_DECIDED = "underwriting-decided";
    /** 核保域跨域外发处理组 */
    public static final String KAFKA_PROCESSING_GROUP     = "underwriting-kafka-group";
}
