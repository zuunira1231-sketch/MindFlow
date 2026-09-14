package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
//DeepSeek API 要求的数据格式
public class ChatRequest {

    //模型选择
    private String model;

    private List<ChatMessage> messages;

    //控制AI的创造力，既随机性和确定性
    private Double temperature;

     //格式要求，控制AI的创造性和稳定性
    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

}
