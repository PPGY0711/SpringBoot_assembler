package com.jz.assembler.util;

import com.alibaba.fastjson.JSONObject;
import com.jz.assembler.assembler.Disassembler;

import java.util.Map;

/**
 * 反汇编器接口类
 * 对外提供反汇编器功能
 * 单例模式创建
 */
public class DisassemblerUtil {
    private static Disassembler Instance = null;
    private static String Code = null;
    private static Disassembler getInstance(){
        if(Instance == null){
            Instance = new Disassembler();
        }
        else{
            resetDisassembler();
        }
        return Instance;
    }

    public static String getCode() {
        return Code;
    }

    /**
     * 重置汇编器为初始状态
     */
    private static void resetDisassembler(){
        Instance.resetDisassembler();
    }

    public static JSONObject getDisassembledCode(String rawCode){
        JSONObject jsonObject = new JSONObject();
        Disassembler disassembler = DisassemblerUtil.getInstance();
        String info;
        //判断反汇编处理状态并封装反汇编结果
        if((info=disassembler.disassembleCode(rawCode)) == null){
            //反汇编成功
            jsonObject.put("status",0);
            Code = prepareCode(disassembler.getAssembleCodeMap());
            jsonObject.put("code",Code);
        }
        else{
            //反汇编失败
            jsonObject.put("status",1);
            jsonObject.put("code",info);
            Code = info;
        }
        System.out.println(info);
        return jsonObject;
    }

    private static String prepareCode(Map<Integer, String> codeMap){
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<Integer, String> entry : codeMap.entrySet()){
            builder.append(entry.getValue() + "\r\n");
        }
        return builder.toString();
    }
}
