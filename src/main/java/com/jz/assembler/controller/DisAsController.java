package com.jz.assembler.controller;

import com.alibaba.fastjson.JSONObject;
import com.jz.assembler.util.DisassemblerUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.*;

/**
 * 反汇编器Controller
 */
@Controller
public class DisAsController {
    @RequestMapping("/disassembler")
    public String getDisassembler(){
        return "disassembler";
    }

    @ResponseBody
    @PostMapping("/sendMachineCode")
    public JSONObject sendDisassembledCode(@RequestBody String jsonStr){
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        String code = jsonObject.getString("code");
        return DisassemblerUtil.getDisassembledCode(code);
    }

    @ResponseBody
    @PostMapping("/getAssembleCodeFile")
    public JSONObject getAssembleCodeFile(){
        String code = DisassemblerUtil.getCode();
//        DefaultResourceLoader loader = new DefaultResourceLoader();
//        try {
//            ClassPathResource resource = new ClassPathResource("\\static\\txt\\assembleCode.s");
////            InputStream is = resource.getInputStream();
//            File file = resource.getFile();
//            FileWriter writer = new FileWriter(file);
//            BufferedWriter out = new BufferedWriter(writer);
//            out.write(code);
//            out.flush();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        JSONObject jsonObject = new JSONObject();
        if(code.startsWith("Error"))
            jsonObject.put("status",1);
        else
            jsonObject.put("status",0);
        jsonObject.put("code",code);
        return jsonObject;
    }
}
