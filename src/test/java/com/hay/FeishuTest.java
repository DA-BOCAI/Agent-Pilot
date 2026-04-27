package com.hay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;

public class FeishuTest {

    
    // 你可以先建个测试类跑一下这段代码，看看能不能给你自己发消息
    @Test
    public void testSendMsg() throws Exception {    
        Process process = new ProcessBuilder(
            "cmd.exe", "/c",
            "lark-cli im +messages-send --user-id 你的飞书open_id --content \"测试消息\" --as bot"
        ).redirectErrorStream(true).start();
    
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("输出: " + line);
        }
        int exitCode = process.waitFor();
        System.out.println("退出码: " + exitCode);  
}
    
}
