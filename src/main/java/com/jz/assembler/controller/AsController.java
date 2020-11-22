package com.jz.assembler.controller;

import com.alibaba.fastjson.JSONObject;
import com.jz.assembler.util.AssemblerUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.*;

/**
 * 汇编器Controller
 */
@Controller
public class AsController {

    @RequestMapping({"/","/index","/assembler"})
    public String getAssembler(){
        return "assembler";
    }

    @ResponseBody
    @PostMapping("/sendMIPSCode")
    public JSONObject sendAssembledCode(@RequestBody String jsonStr){
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        String code = jsonObject.getString("code");
        return AssemblerUtil.getAssembledCode(code);
    }


    @ResponseBody
    @PostMapping("/getMachineCodeFile")
    public JSONObject getMachineCodeFile(@RequestBody String jsonStr){
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        int radix = jsonObject.getInteger("radix");
        JSONObject retObject = AssemblerUtil.getPureMachineCode(radix);
        String code = retObject.getString("code");
//        DefaultResourceLoader loader = new DefaultResourceLoader();
//        try {
//            ClassPathResource resource = new ClassPathResource("\\static\\txt\\machineCode.txt");
//            InputStream is = resource.getInputStream();
//            File file = resource.getFile();
//            FileWriter writer = new FileWriter(file);
//            BufferedWriter out = new BufferedWriter(writer);
//            out.write(code);
//            out.flush();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        return retObject;
    }

}
