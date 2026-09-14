package com.cogniflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    //AI需要知道该content是来自于谁，system、user、assistant
    private String role;
    //携带内容
    private String content;
}
