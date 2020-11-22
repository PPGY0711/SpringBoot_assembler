package com.jz.assembler.assembler;

import com.jz.assembler.common.Mnemonic;
import com.jz.assembler.common.Register;
import com.jz.assembler.exception.AssembleException;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 反汇编器实现类
 */
@Getter
@Setter
public class Disassembler {

    //汇编代码
    private Map<Integer, String> AssembleCodeMap;
    //通用寄存器
    private Map<Integer, String> CommonRegisterNameMap;
    //协处理器寄存器
    private Map<Integer, String> CoprocessorRegisterNameMap;
    //指令集(作为Key的String由opc和func组成，形式为(opc,func)
    private Map<String, String> MnemonicMap;

    public Disassembler(){
        AssembleCodeMap = new TreeMap<>();
        InitRegisterMap();
        InitMnemonicMap();
    }

    public void resetDisassembler(){
        AssembleCodeMap.clear();
    }

    private void InitRegisterMap(){
        CommonRegisterNameMap = new HashMap<>();
        for(int i = 0; i < Register.CommonRegisterNames.length; i++){
            CommonRegisterNameMap.put(i,Register.CommonRegisterNames[i]);
        }
        CoprocessorRegisterNameMap = new HashMap<>();
        CoprocessorRegisterNameMap.put(12,"STATUS");
        CoprocessorRegisterNameMap.put(13,"CAUSE");
        CoprocessorRegisterNameMap.put(14,"EPC");
    }

    private void InitMnemonicMap(){
        MnemonicMap = new TreeMap<>();
        for(int i = 0; i < Mnemonic.allMnemonics.length; i++){
            String key = "(" + Mnemonic.allOpcs[i]+","+Mnemonic.allFuncs[i]+")";
            MnemonicMap.put(key,Mnemonic.allMnemonics[i]);
        }
        MnemonicMap.remove("(16,0)");
        MnemonicMap.put("(16,0),0","mfc0");
        MnemonicMap.put("(16,0),4","mtc0");
    }

    public String disassembleCode(String rawCode){
        try {
            ArrayList<Long> codeList = removeEmptyLines(rawCode);
            for(int i = 0; i < codeList.size();i++){
                parseMachineCode(i,codeList.get(i));
            }
        } catch (AssembleException e) {
            System.out.println(e.getMessage());
            return e.getMessage().replaceAll("\r\n","<br/>");
        }
        return null;
    }

    private ArrayList<Long> removeEmptyLines(String rawCode) throws AssembleException {
        String[] codeList = rawCode.split("\n");
        ArrayList<Long> retList = new ArrayList<>();
        for(String string : codeList){
            if(string.equals("") || string.matches("\\s+")){
                continue;
            }
            else{
                retList.add(str2Int(string.replaceAll("\t","").trim()));
            }
        }
        return retList;
    }

    private long str2Int(String str) throws AssembleException {
        long res = 0;
        boolean flag = true;
        if(str.length() == 8){
            //8位-》16进制机器码
            if(isHex(str))
                res = Long.parseLong(str,16);
            else flag = false;
        }
        else if(str.length()==32){
            if(isBin(str))
                res = Long.parseLong(str,2);
            else flag = false;
        }
        else
            flag = false;
        if(!flag)
            throw new AssembleException("Error: Wrong MachineCode Format\r\n\tThe input '"+ str +"' is not binary or hexadecimal\r\n\tPlease check your machine code.");
        else {
            return res;
        }
    }

    private boolean isHex(String s){
        for(int i = 0; i < s.length();i++){
            char c = s.charAt(i);
            if(!((c>='0'&&c<='9') || (c>='a'&&c<='f') || (c>='A' && c<='F')))
                return false;
        }
        return true;
    }

    private boolean isBin(String s){
        for(int i = 0; i < s.length(); i++){
            if(s.charAt(i) != '0' && s.charAt(i)!='1')
                return false;
        }
        return true;
    }

    private void parseMachineCode(int lineNum, long machineCode) throws AssembleException {
        //for R
        int opc,rs,rt,rd,sa,func;
        //for I
        short dat,dot;
        int datExtension,dotExtension;
        //for J
        int adr,adrValue;
        //for C
        int rc;
        opc = (int)((machineCode&0xFC000000) >> 26);
        rs = (int)((machineCode&0x03E00000) >> 21);
        rt = (int)((machineCode&0x001F0000) >> 16);
        rc = rd = (int)((machineCode&0x0000F800) >> 11);
        sa = (int)((machineCode&0x000007C0) >> 6);
        func = (int)(machineCode&0x000003F);
        dot = dat = (short)(machineCode&0x0000FFFF);
        if((dat&0x8000)!=0)
            datExtension = 0xFFFF0000 | dat&0x0000FFFF;
        else
            datExtension = dat&0x0000FFFF;
        dotExtension = dot&0x0000FFFF;
        adr = (int)((machineCode&0x03FFFFFF));
        adrValue = adr*2;
        //统一key格式
        String key = "";
        if(opc == 0 || opc == 28 || opc == 16)
            key = "(" + opc + "," + func + ")";
        else
            key = "(" + opc + ",0)";
        if(key.equals("(16,0)")){
            key = key+"," + rs;
        }
        System.out.println("=============== key is : " + key);
        String assembleCode = "";
        if(!MnemonicMap.containsKey(key)){
            //识别不出的一律翻译为.word XXXX
            assembleCode = ".word 0x" + String.format("%8x",machineCode);
            AssembleCodeMap.put(lineNum,assembleCode);
        }
        else{
            String mnemonic = MnemonicMap.get(key);
            switch (mnemonic){
                //这里写反汇编最主要的函数处理
                case "lui":
                {
                    if(CommonRegisterNameMap.containsKey(rt)){
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rt) + ", " + datExtension;
                    }
                    else{
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    break;
                }
                case "add":
                case "sub":
                case "slt":
                case "sltu":
                case "and":
                case "or":
                case "xor":
                case "nor":
                case "sllv":
                case "srlv":
                case "srav":
                case "mul":
                {
                    if(!CommonRegisterNameMap.containsKey(rs) || !CommonRegisterNameMap.containsKey(rt) ||!CommonRegisterNameMap.containsKey(rd)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else{
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rd) + ", " + CommonRegisterNameMap.get(rs) + ", " + CommonRegisterNameMap.get(rt);
                    }
                    break;
                }
                case "sll":
                case "srl":
                case "sra":
                {
                    if(!CommonRegisterNameMap.containsKey(rs) || !CommonRegisterNameMap.containsKey(rd)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rd) + ", " + CommonRegisterNameMap.get(rs) + ", " + sa;
                    }
                    break;
                }
                case "addi":
                case "slti":
                case "beq":
                case "bne":
                case "bgezal":
                {
                    if(!CommonRegisterNameMap.containsKey(rs) || !CommonRegisterNameMap.containsKey(rt)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        if(mnemonic.equals("bgezal")){
                            if(rt != 17){
                                throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                            }
                        }
                        if(mnemonic.equals("addi") || mnemonic.equals("slti"))
                            assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rt) + ", " + CommonRegisterNameMap.get(rs) + ", " + datExtension;
                        else
                            assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rs) + ", " + CommonRegisterNameMap.get(rt) + ", " + datExtension;
                    }
                    break;
                }
                case "lw":
                case "lwx":
                case "lh":
                case "lhx":
                case "lhu":
                case "lhux":
                case "sw":
                case "swx":
                case "sh":
                case "shx":
                {
                    if(!CommonRegisterNameMap.containsKey(rt) || !CommonRegisterNameMap.containsKey(rs)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rt) + ", " + datExtension + "(" + CommonRegisterNameMap.get(rs) + ")";
                    }
                    break;
                }
                case "sltiu":
                case "andi":
                case "ori":
                case "xori":
                {
                    if(!CommonRegisterNameMap.containsKey(rt) || !CommonRegisterNameMap.containsKey(rs)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rt) + ", " +  CommonRegisterNameMap.get(rs) + ", " + dotExtension;
                    }
                    break;
                }
                case "j":
                case "jal":
                {
                    assembleCode = mnemonic + " " + adrValue;
                    break;
                }
                case "jr":
                case "mthi":
                case "mtlo":
                {
                    if(!CommonRegisterNameMap.containsKey(rs)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rs);
                    break;
                }
                case "mfhi":
                case "mflo":
                {
                    if(!CommonRegisterNameMap.containsKey(rd)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rd);
                    break;
                }
                case "jalr":
                {
                    if(!CommonRegisterNameMap.containsKey(rs) || !CommonRegisterNameMap.containsKey(rd)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else {
                        assembleCode = mnemonic + " " +CommonRegisterNameMap.get(rs) + ", " + CommonRegisterNameMap.get(rd);
                    }
                    break;
                }
                case "eret":
                case "syscall":
                {
                    assembleCode = mnemonic;
                    break;
                }
                case "mult":
                case "multu":
                case "div":
                case "divu":
                {
                    if(!CommonRegisterNameMap.containsKey(rs) || !CommonRegisterNameMap.containsKey(rt)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rs) + ", " + CommonRegisterNameMap.get(rt);
                    break;
                }
                case "mfc0":
                case "mtc0":
                {
                    if(!CommonRegisterNameMap.containsKey(rt) || !CoprocessorRegisterNameMap.containsKey(rc)){
                        throw new AssembleException("Error: Illegal Register Used\r\n\t Code '" + machineCode + "' contains wrong register num\r\n\tPlease check your code");
                    }
                    else
                        assembleCode = mnemonic + " " + CommonRegisterNameMap.get(rt) + ", " + CoprocessorRegisterNameMap.get(rc);
                    break;
                }
            }
            AssembleCodeMap.put(lineNum,assembleCode);
        }
    }

    public static void main(String[] args) {
        Disassembler disassembler = new Disassembler();
        System.out.println(disassembler.MnemonicMap.size());
        for(Map.Entry<String, String> entry:disassembler.getMnemonicMap().entrySet()){
            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
        }
        short s1 = (short)0x8000;
        int i1 = s1&0x0000FFFF;
        int i2 = 0xFFFF0000 | s1&0x0000FFFF;
        System.out.println(i1);
        System.out.println(i2);
        System.out.println(Long.valueOf("afb00000",16));
    }

}
