package com.jz.assembler.util;

import com.alibaba.fastjson.JSONObject;
import com.jz.assembler.assembler.Assembler;

import java.util.*;
/**
 * 汇编器接口类
 * 对外提供汇编功能
 * 单例模式创建
 */
public class AssemblerUtil {
    //1.接收来自网页输入字符流，字符串切割-》指令流
    //2.指令流对应翻译
    //3.返回翻译字符串
    private static Assembler Instance = null;
    private static String info = null;
    private static Map<Long, String> binMap;
    private static Map<Long, String> hexMap;
    private static Assembler getInstance(){
        if(Instance == null){
            Instance = new Assembler();
        }
        else{
            resetAssembler();
        }
        return Instance;
    }

    public static JSONObject getAssembledCode(String input){
        JSONObject jsonObject = new JSONObject();
        //获取Assembler实例
        Assembler assembler = getInstance();
        //判断汇编情况并封装汇编结果对象
        if((info=assembler.assembleCode(input)) == null){
            //汇编成功
            jsonObject.put("status",0);
            binMap = assembler.getBinMachineCodeMap();
            hexMap = assembler.getHexMachineCodeMap();
            //2进制与16进制存储
            jsonObject.put("bin", mapToStr(binMap));
            jsonObject.put("hex",mapToStr(hexMap));
        }
        else{
            //汇编失败
            jsonObject.put("status",1);
            jsonObject.put("errorMsg",info);
        }
        System.out.println(info);
        return jsonObject;
    }

    public static JSONObject getPureMachineCode(int radix){
        JSONObject jsonObject = new JSONObject();
        if(info == null){
            jsonObject.put("status",0);
            if(radix == 2)
                jsonObject.put("code", mapToPureStr(binMap));
            else jsonObject.put("code",mapToPureStr(hexMap));
        }
        else{
            jsonObject.put("status",1);
            jsonObject.put("code",info);
        }
        return jsonObject;
    }

    private static String mapToStr(Map<Long, String> map){
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<Long, String> entry : map.entrySet()){
            builder.append(String.format("[0x%-5x] ",entry.getKey())+entry.getValue()+"\r\n");
        }
        return builder.toString();
    }

    private static String mapToPureStr(Map<Long, String> map){
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<Long, String> entry : map.entrySet()){
            builder.append(entry.getValue()+"\r\n");
        }
        return builder.toString();
    }

    /**
     * 重置汇编器为初始状态
     */
    private static void resetAssembler(){
        Instance.resetAssembler();
    }

}
