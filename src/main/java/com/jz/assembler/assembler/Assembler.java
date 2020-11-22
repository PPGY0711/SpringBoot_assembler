package com.jz.assembler.assembler;

import com.alibaba.fastjson.JSONObject;
import com.jz.assembler.common.Mnemonic;
import com.jz.assembler.common.Register;
import com.jz.assembler.exception.AssembleException;
import com.jz.assembler.util.EncodingUtil;
import lombok.Getter;
import lombok.Setter;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * 汇编器实现类
 */
@Getter
@Setter
public class Assembler {

    //通用寄存器(名称形式、数字形式、内容）
    private Map<String, Integer> CommonRegisterNameMap;
    private Map<String, Integer> CommonRegisterNumMap;
    private Map<Integer, Long> CommonRegisterContent;

    //协处理器寄存器(名称形式、数字形式）
    private Map<String, Integer> CoprocessorRegisterNameMap;
    private Map<String, Integer> CoprocessorRegisterNumMap;
    private Map<Integer, Long> CoprocessorRegisterContent;

    //Hi,Lo
    private Long HiRegister,LoRegister;

    //指令集
    private Map<String, Integer> RTypeFuncMap;
    private Map<String, Integer> OpcMap;
    private Map<String, Integer> PseudoMap;
    //地址（标号地址）
    private Map<String, Integer> LabelAddressMap;
    private Map<Integer, Long> LineAddressMap;
    //需要第三次扫描才能翻译的指令（含有标号，表达式计算）
    private Map<Integer, String> NeedThirdParseMap;
    //格式指令处理
    private Map<String, Long> EquMap;
    //内存
    private Map<Long, Short> MemoryMap;
    //变量表
    private Map<String, Long> VariableMap;
    //结果(2/16进制)
    private Map<Long, String> BinMachineCodeMap;
    private Map<Long, String> HexMachineCodeMap;

    //控制，状态变量及计数器
    private Long PC;
    private Long textPC;
    private Long dataPC;
    private Long showPC;
    private int dataStart,codeStart,endLineNum;
    private int currentState; // 状态：0-》数据段；1-》代码段；2-》结束;-1->未开始

    public Assembler(){
        InitRegisterMaps();
        InitInstructionMaps();
        LabelAddressMap = new HashMap<>();
        LineAddressMap = new HashMap<>();
        NeedThirdParseMap = new HashMap<>();
        EquMap = new HashMap<>();
        VariableMap = new HashMap<>();
        BinMachineCodeMap = new TreeMap<>();
        HexMachineCodeMap = new TreeMap<>();
        MemoryMap = InitMemoryMap();
        setStatus();
    }

    private void setStatus(){
        PC = 0L;
        textPC = 0L;
        dataPC = 0x4000L;
        showPC = 0x6000L;
        currentState = -1;
        HiRegister = LoRegister = 0L;
        dataStart = codeStart = endLineNum =  -1;
        //$sp\$fp赋值
    }

    public void resetAssembler(){
        LabelAddressMap.clear();
        LineAddressMap.clear();
        EquMap.clear();
        BinMachineCodeMap.clear();
        HexMachineCodeMap.clear();
        VariableMap.clear();
        NeedThirdParseMap.clear();
        MemoryMap = InitMemoryMap();
        setStatus();
        int limit = CommonRegisterContent.size();
        for(int i = 0; i < limit; i++){
            CommonRegisterContent.put(i,0L);
            if(i==12 || i==13 || i==14)
                CoprocessorRegisterContent.put(i,0L);
        }
    }

    /**
     * 初始化内存
     * @return
     */
    private TreeMap<Long, Short> InitMemoryMap(){
        /**
         * 按书写规范来，不规范的会报错
         * 内存结构：总容量0x8000，每一个单位16bits，即一个zjie
         * 数据段：从0x4000开始，到0x5FFF结束
         * 代码段：从0x0000开始，到0x3FFF结束
         * 显存：从0x6000开始，到0x7FFF结束
         * 堆栈：从0x8000开始，到0x9FFF结束
         */
        TreeMap<Long, Short> map = new TreeMap<>();
        int limit = 0x10000;
        for(int i = 0; i < limit;i++){
            map.put(Long.parseLong(i+""),(short)0);
        }
        return map;
    }

    /**
     * 初始化寄存器
     */
    private void InitRegisterMaps(){

        CommonRegisterNameMap = new HashMap<>();
        CommonRegisterNumMap = new HashMap<>();
        CommonRegisterContent = new HashMap<>();
        int count = 0;
        for(String string : Register.CommonRegisterNames){
            CommonRegisterNameMap.put(string, count);
            CommonRegisterContent.put(count,0L);
            count++;
        }
        for(count = 0; count < 32; count++){
            CommonRegisterNumMap.put("$"+count, count);
        }
        CoprocessorRegisterNameMap = new HashMap<>();
        CoprocessorRegisterNumMap = new HashMap<>();
        CoprocessorRegisterContent = new HashMap<>();
        CoprocessorRegisterNameMap.put("STATUS",12);
        CoprocessorRegisterNameMap.put("CAUSE",13);
        CoprocessorRegisterNameMap.put("EPC",14);
        CoprocessorRegisterNumMap.put("$12",12);
        CoprocessorRegisterNumMap.put("$13",13);
        CoprocessorRegisterNumMap.put("$14",14);
        CoprocessorRegisterContent.put(12,0L);
        CoprocessorRegisterContent.put(13,0L);
        CoprocessorRegisterContent.put(14,0L);
    }

    /**
     * 初始化指令集
     */
    private void InitInstructionMaps(){
        List<String> RType = new ArrayList<>();
        OpcMap = new HashMap<>();
        for (int i = 0; i < Mnemonic.allMnemonics.length; i++) {
            OpcMap.put(Mnemonic.allMnemonics[i],Mnemonic.allOpcs[i]);
            if(Mnemonic.allOpcs[i] == 0)
                RType.add(Mnemonic.allMnemonics[i]);
        }
        RTypeFuncMap = new HashMap<>();
        for (int i = 0; i < RType.size(); i++) {
            RTypeFuncMap.put(RType.get(i),Mnemonic.rTypeFuncs[i]);
        }
        //添加mul指令到R型指令集
        RTypeFuncMap.put("mul",2);

        String[] pseudoList = {"li","la","push","pop","move","shi","shix","inc",
                            "dec","addu","addiu","subu","not","neg","abs","swap",
                            "b","beqz","bnez","beqi","bnei","blt","ble","bgt",
                            "bge","seq","sne"};
        PseudoMap = new HashMap<>();
        for(int i = 0; i< pseudoList.length;i++){
            PseudoMap.put(pseudoList[i],i);
        }
    }

    /**
     * 对Util类的汇编使用接口
     * @param rawCode
     * @return 如果出错，则返回错误信息，如果不出错则返回null;
     */
    public String assembleCode(String rawCode){
        ArrayList<String> codeList = handleRawCode(rawCode);
        //第一遍扫描(伪指令替换、错误指令报错-》结束汇编）
        ArrayList<String> scannedList = null;
        try {
            scannedList = firstScan(codeList);
        } catch (AssembleException e) {
            System.out.print(e.getMessage());
            return e.getMessage();
        }
        System.out.println("============ first scan result ===============");
        for(String str:scannedList){
            System.out.println(str);
        }
        //第二遍扫描/处理格式指令，填表（常量表、标号表）、计算相对地址
        System.out.println("============ second scan result ===============");
        try {
            secondScan(scannedList);
        } catch (AssembleException e) {
            System.out.print(e.getMessage());
            return e.getMessage();
        }
        System.out.println("============ third scan result ===============");
        try {
            thirdScan();
        } catch (AssembleException e) {
            System.out.print(e.getMessage());
            return e.getMessage();
        }
//        printMemoryMap();
        return null;
    }

    private void printMemoryMap(){
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < MemoryMap.size();i++){
            if(i%16==0)
                builder.append("["+ String.format("%4x",i) + "]");
            builder.append(String.format("%4x",MemoryMap.get(Long.parseLong(i+"",10))) + " ");
            if(i%16==15)
                builder.append("\n");
        }
        System.out.println(builder.toString());
    }

    private ArrayList<String> firstScan(ArrayList<String> list) throws AssembleException {
        String label = "";
        ArrayList<String> codeList = new ArrayList<>();
        for(String code : list){
            int pType;
            code = code.replace("\n","").replaceAll("\t"," ").replaceAll("\r","").trim();
            if(isLabelLine(code)){
                //标号单独成行的话
                label = code;
            }
            else if((pType=isPseudoCode(code)) != -1){
                ArrayList<String> substituteStrList = handlePseudoCode(pType,code);
                //插入替换后的指令到代码行
                for(int i = 0;i < substituteStrList.size();i++){
                    if(i == 0){
                        codeList.add(label + substituteStrList.get(i).trim());
                        label = "";
                    }
                    else
                       codeList.add(substituteStrList.get(i).trim());
                }
            }
            else if(isFormatCode(code) == -1 && isInstructionCode(code).getInteger("type") == -1){
                throw new AssembleException("Error: Invalid Instruction\r\n\tCode: '"+ code +"' is not acceptable by Assembler.\r\n\tPlease Check your spelling." );
            }
            else{
                codeList.add(label+" " + code);
                label = "";
            }
        }
        return codeList;
    }

    private boolean isLabelLine(String code){
        if(code.charAt(code.length()-1) == ':'){
            String input = code.substring(0,code.indexOf(":"));
            return input.matches("[a-zA-Z]\\w+");
        }
        else
            return false;
    }

    private String getMnemonic(String row){
//        System.out.println("======= getMnemonic: "+row);
        String mnemonic = "";
        if(row.contains(" ") && row.contains(":")){
            mnemonic = row.substring(row.indexOf(':')+1).trim();
            mnemonic = mnemonic.substring(0,mnemonic.indexOf(' '));
        }else if(!row.contains(" ")){
            mnemonic = row;
        }else if(!row.contains(":")){
            mnemonic = row.substring(0,row.indexOf(' ')).trim();
        }
        mnemonic = mnemonic.replaceAll("\t","");
//        System.out.println("======= Mnemonic: " + mnemonic);
        return mnemonic;
    }

    private int isPseudoCode(String row){
        String mnemonic = getMnemonic(row);
        return PseudoMap.containsKey(mnemonic)? PseudoMap.get(mnemonic):-1;
    }

    private ArrayList<String> handlePseudoCode(int type, String code) throws AssembleException {
        String label = "";
        if(code.contains(":")){
            label = code.substring(0,code.indexOf(":")+1);
            code = code.replace(label,"").trim();
        }
        StringBuilder builder = new StringBuilder();
        String regs = code.substring(code.indexOf(' ') + 1).trim();
        String[] regList = regs.split(",");
        int n = regList.length;
        try{
            switch (type){
                case 0://li
                {
                    String datStr = code.substring(code.indexOf(',') + 1).trim();
                    int radix = getRadix(datStr);
                    datStr = getPreDataStr(datStr,radix);
                    int dat = Integer.parseInt(datStr, radix);
                    String reg = code.substring(code.indexOf(' ') + 1, code.indexOf(',')).trim();
                    if (dat > 32767 || dat < -32768) {
                        builder.append("lui " + reg + ",HIGH " + dat + "\n");
                        builder.append("ori " + reg + ", $zero ,LOW " + dat + "\n");
                    } else {
                        builder.append("addi " + reg + ",$zero," + dat + "\n");
                    }
                    break;
                }
                case 1://la
                {

                    //这一步读的address，为了好处理，全部翻译成两条指令的形式，保留了label
                    builder.append("lui " + regList[0] + ",HIGH " + regList[1] + "\n");
                    builder.append("ori " + regList[0] + ", $zero ,LOW " + regList[1] + "\n");
                    break;
                }
                case 2://push
                {
                    builder.append("addi $sp,$sp," + (-n*2) + "\n");
                    for(int i = 0 ; i < n; i++){
                        builder.append("sw " + regList[i] + ", " + i*2 + "($sp)\n");
                    }
                    break;
                }
                case 3://pop
                {

                    for(int i = 0; i < n; i++){
                        builder.append("lw " + regList[i] + ", " + i*2 + "($sp)\n");
                    }
                    builder.append("addi $sp,$sp," + n*2 + "\n");
                    break;
                }
                case 4://move
                {
                    builder.append("or " + regList[0] + ", " + regList[1] + ", $zero\n");
                    break;
                }
                case 5://shi
                {
                    builder.append("addi $at,$zero," + regList[0] +"\n");
                    builder.append("sh $at," + regList[1]);
                    break;
                }
                case 6://shix
                {
                    builder.append("addi $at,$zero," + regList[0] +"\n");
                    builder.append("shx $at," + regList[1]);
                    break;
                }
                case 7://inc
                {
                    builder.append("addi " + regList[0] + ", " + regList[0] + ", 1\n");
                    break;
                }
                case 8://dec
                {
                    builder.append("addi " + regList[0] + ", " + regList[0] + ", -1\n");
                    break;
                }
                case 9://addu
                {
                    builder.append(code.replace("addu","add") +"\n");
                    break;
                }
                case 10://addiu
                {
                    builder.append(code.replace("addiu","addi") +"\n");
                    break;
                }
                case 11://subu
                {
                    builder.append(code.replace("subu","sub") +"\n");
                    break;
                }
                case 12://not
                {
                    builder.append("nor " + regList[0] + ", " + regList[1] + ", " + regList[1] +"\n");
                    break;
                }
                case 13://neg
                {
                    builder.append("sub " + regList[0] + ", $zero, " + regList[1] + "\n");
                    break;
                }
                case 14://abs
                {
                    builder.append("sra $at, " + regList[1] + ", 31\n");
                    builder.append("xor " + regList[0] + ", " + regList[1] + ",$at\n");
                    builder.append("sub " + regList[0] +", " + regList[0] + ", $at\n");
                    break;
                }
                case 15://swap
                {
                    builder.append("xor " + regList[0] + ","+ regList[0] +", " + regList[1] + "\n");
                    builder.append("xor " + regList[1] + ","+ regList[0] +", " + regList[1] + "\n");
                    builder.append("xor " + regList[0] + ","+ regList[0] +", " + regList[1] + "\n");
                    break;
                }
                case 16://b
                {
                    builder.append("beq $zero,$zero," + regList[0] +"\n");
                    break;
                }
                case 17://beqz
                {
                    builder.append("beq " + regList[0] + ", $zero, " + regList[1] + "\n");
                    break;
                }
                case 18://bnez
                {
                    builder.append("bne " + regList[0] + ", $zero, " + regList[1] + "\n");
                    break;
                }
                case 19://beqi
                {
                    builder.append("addi $at,$zero," + regList[1] + "\n");
                    builder.append("beq $at," + regList[0] + ", " + regList[2] +"\n");
                    break;
                }
                case 20://bnei
                {
                    builder.append("addi $at,$zero," + regList[1] + "\n");
                    builder.append("bne $at," + regList[0] + ", " + regList[2] +"\n");
                    break;
                }
                case 21://blt
                {
                    builder.append("slt $at," + regList[0] +"," + regList[1] +"\n");
                    builder.append("bne $at,$zero," + regList[2] +"\n");
                    break;
                }
                case 22://ble
                {
                    builder.append("slt $at," + regList[1] +"," + regList[0] +"\n");
                    builder.append("beq $at,$zero," + regList[2] +"\n");
                    break;
                }
                case 23://bgt
                {
                    builder.append("slt $at," + regList[1] +"," + regList[0] +"\n");
                    builder.append("bne $at,$zero," + regList[2] +"\n");
                    break;
                }
                case 24://bge
                {
                    builder.append("slt $at," + regList[0] +"," + regList[1] +"\n");
                    builder.append("beq $at,$zero," + regList[2] +"\n");
                    break;
                }
                case 25://seq
                {
                    builder.append(code.replace("seq","sub") +"\n");
                    builder.append("sltiu " + regList[0] +", " + regList[0] +",1\n");
                    break;
                }
                case 26://sne
                {
                    builder.append(code.replace("sne","sub") + "\n");
                    builder.append("sltu " + regList[0] + "$zero," + regList[0] +"\n");
                    break;
                }
            }
            return handleRawCode(label + builder.toString());
        }catch (ArrayIndexOutOfBoundsException e){
            throw new AssembleException("Error: Not Enough Argument Given\r\n\tThe code '" + code + "' format could be wrong.\r\n\tPlease check your code.");
        }
    }

    private boolean checkLegalHexCharacter(char c, String numStr) throws AssembleException {
        if(!((c>='0'&&c<='9') || (c>='a'&&c<='f') || (c>='A' && c<='F'))) {
            throw new AssembleException("Error: Not Acceptable Number Format\r\n\tThe number: '" + numStr + "' is wrong.\r\n\tPlease check your code.");
        }
        else
            return true;
    }

    private int getRadix(String numStr) throws AssembleException {
        /**
         * 默认十进制，十六进制以‘0x’为前缀或者以‘H’为后缀，二进制以B为后缀
         */
        System.out.println("----------- numStr: " + numStr);
        if(numStr.startsWith("-"))
            numStr = numStr.replace("-","");
        if((numStr.length()>2 && numStr.substring(0,2).equals("0x")) || numStr.charAt(numStr.length()-1) == 'H'){
            if(numStr.startsWith("0x") && numStr.length()>2){
                for(int i = 2 ;i < numStr.length();i++){
                    char c = numStr.charAt(i);
                    checkLegalHexCharacter(c,numStr);
                }
            }
            else if(numStr.equals("0x")){
                throw new AssembleException("Error: Not Acceptable Number Format\r\n\tThe number: '" + numStr + "' is wrong.\r\n\tPlease check your code.");
            }
            else{
                for(int i = 0 ;i < numStr.length()-1;i++){
                    char c = numStr.charAt(i);
                    checkLegalHexCharacter(c,numStr);
                }
            }
            return 16;
        }
        else if(numStr.charAt(numStr.length()-1) == 'B'){
            for(int i = 0 ;i <numStr.length()-1;i++){
                if(numStr.charAt(i) != '0' || numStr.charAt(i) !='1'){
                    throw new AssembleException("Error: Not Acceptable Number Format\r\n\tThe number: '" + numStr + "' is wrong.\r\n\tPlease check your code.");
                }
            }
            return 2;
        }
        else{
            System.out.println("could be decimal: " + numStr);
            for(int i = 0 ;i <numStr.length();i++){
                if(numStr.charAt(i)>'9' || numStr.charAt(i)<'0')
                    throw new AssembleException("Error: Not Acceptable Number Format\r\n\tThe number: '" + numStr + "' is wrong.\r\n\tPlease check your code.");
            }
            return 10;
        }
    }

    private String getPreDataStr(String datStr, int radix){
        switch (radix){
            case 2:
                datStr = datStr.replace("B","");
                break;
            case 16:
                if(datStr.substring(0,2).equals("0x")){
                    datStr = datStr.substring(2);
                }
                else
                    datStr = datStr.replace("H","");
        }
        return datStr;
    }

    private void secondScan(ArrayList<String> codeList) throws AssembleException {
        for(int i = 0; i < codeList.size() ; i++){
            int fType; //格式指令类型，操作指令类型
            String code = codeList.get(i).replaceAll("\t"," ").trim();
            if(code.contains(":")){
                String label = code.substring(0,code.indexOf(":"));
                if(insertLabel(label,i)){
                    code = code.substring(code.indexOf(":")+1).trim();
                }
                else{
                    throw new AssembleException("Error: Duplicate Label Defined\r\n\tLabel: '"+ label +"' can't be defined more than once.\r\n\tPlease check your code." );
                }
            }
            if((fType = isFormatCode(code)) != -1){
                handleFormatCode(fType,codeList.get(i),i);
            }
            else {
                //R\I\J\C->0\1\2\3
                JSONObject jsonObject = isInstructionCode(code);
                String mnemonic = jsonObject.getString("mnemonic");
                LineAddressMap.put(i,textPC);
                textPC+=2;
                switch (jsonObject.getInteger("type")){
                    case 0:
                        parseRTypeInstruction(code,i,mnemonic);
                        break;
                    case 1:
                        parseITypeInstruction(code,i,mnemonic);
                        break;
                    case 2:
                        parseJTypeInstruction(code,i,mnemonic);
                        break;
                    case 3:
                        parseCTypeInstruction(code,i,mnemonic);
                        break;
                }
            }
        }
    }

    private int findCommonRegisterNum(String nameOrNum){
        if(CommonRegisterNumMap.containsKey(nameOrNum) || CommonRegisterNameMap.containsKey(nameOrNum)){
            return CommonRegisterNameMap.containsKey(nameOrNum)?CommonRegisterNameMap.get(nameOrNum):CommonRegisterNumMap.get(nameOrNum);
        }
        else
            return -1;
    }

    private int findCoprocessorRegisterNum(String nameOrNum){
        if(CoprocessorRegisterNameMap.containsKey(nameOrNum) || CoprocessorRegisterNumMap.containsKey(nameOrNum)){
            return CoprocessorRegisterNameMap.containsKey(nameOrNum)?CoprocessorRegisterNameMap.get(nameOrNum):CoprocessorRegisterNumMap.get(nameOrNum);
        }
        else
            return -1;
    }

    private void parseRTypeInstruction(String code, int lineNum, String mnemonic) throws AssembleException {
        int func = RTypeFuncMap.get(mnemonic);
        Long addr = LineAddressMap.get(lineNum);
        String regs = code.replace(mnemonic,"").trim();
        String[] regList = regs.split(",");
        String rs,rt,rd,sa;
        int rsNum,rtNum,rdNum,saNum;
        rsNum = rtNum = rdNum = saNum = 0;
        int opc = OpcMap.get(mnemonic);
        switch (func){
            //rd,rs,rt
            case 32://add
            case 34://sub
            case 42://slt
            case 43://sltu
            case 36://and
            case 37://or
            case 38://xor
            case 39://nor
            case 4://sllv
            case 6://srlv
            case 7://srav
            case 2://mul,srl
            case 0://sll
            case 3://sra
            {
                rd = regList[0].replaceAll("\t"," ").trim();
                rs = regList[1].replaceAll("\t"," ").trim();
                rt = sa = regList[2].replaceAll("\t"," ").trim();
                rdNum = findCommonRegisterNum(rd);
                rsNum = findCommonRegisterNum(rs);
                rtNum = 0;
                saNum = 0;
                if(mnemonic.equals("srl") || mnemonic.equals("sll") || mnemonic.equals("sra")){
                    int radix = getRadix(sa);
                    sa = getPreDataStr(sa,radix);
                    saNum = Integer.parseInt(sa,radix);
                }
                else{
                    rtNum = findCommonRegisterNum(rt);
                }
                break;
            }
            case 8://jr
            case 17://mthi
            case 19://mtlo
            {
                rs = regList[0].replaceAll("\t"," ").trim();
                rsNum = findCommonRegisterNum(rs);
                break;
            }
            case 9://jalr
            {
                rs = regList[0].replaceAll("\t"," ").trim();
                rsNum = findCommonRegisterNum(rs);
                rd = regList[1].replaceAll("\t"," ").trim();
                rdNum = findCommonRegisterNum(rd);
                break;
            }
            case 18://mfhi
            case 16://mflo
            {
                rd = regList[0].replaceAll("\t"," ").trim();
                rdNum = findCommonRegisterNum(rd);
                break;
            }
            case 24://mult
            case 25://multu
            case 26://div
            case 27://divu
            {
                rs = regList[0].replaceAll("\t"," ").trim();
                rsNum = findCommonRegisterNum(rs);
                rt = regList[1].replaceAll("\t"," ").trim();
                rtNum = findCommonRegisterNum(rt);
                break;
            }
            case 12://syscall
            {
                break;
            }
        }
        insertMachineCode(opc,rsNum,rtNum,rdNum,saNum,func,addr,code);
    }

    private void parseITypeInstruction(String code, int lineNum, String mnemonic) throws AssembleException {
       //不支持表达式求值，仅支持最简单的high\low+立即数
        int opc = OpcMap.get(mnemonic);
        long addr = LineAddressMap.get(lineNum);
        int rsNum,rtNum,datNum,ofsNum,machineCode;
        long dotNum;
        String rs,rt,dat,dot,ofs;
        String regs = code.replace(mnemonic,"").replaceAll("\t"," ").trim();
        String[] regList = regs.split(",");
        rsNum = rtNum = datNum = ofsNum = 0;
        dotNum = 0;
        switch (opc){
            case 15://lui rt,dat
            {
                rt = regList[0].replaceAll("\t"," ").trim();
                rtNum = findCommonRegisterNum(rt);
                dat = regList[1].replaceAll("\t"," ").trim();
                datNum = calDatNum(dat);
                insertMachineCode(opc,rsNum,rtNum,datNum,0,addr,code,0);
                break;
            }
            case 8://addi
            case 10://slti
            {
                rs = regList[1].replaceAll("\t"," ").trim();
                rsNum = findCommonRegisterNum(rs);
                rt = regList[0].replaceAll("\t"," ").trim();
                rtNum = findCommonRegisterNum(rt);
                dat = regList[2].replaceAll("\t"," ").trim();
                datNum = calDatNum(dat);
                insertMachineCode(opc,rsNum,rtNum,datNum,0,addr,code,0);
                break;
            }
            case 11://sltiu
            case 12://andi
            case 13://ori
            case 14://xori
            {
                rs = regList[1].replaceAll("\t"," ").trim();
                rsNum = findCommonRegisterNum(rs);
                rt = regList[0].replaceAll("\t"," ").trim();
                rtNum = findCommonRegisterNum(rt);
                dot = regList[2].replaceAll("\t"," ").trim();
                dotNum = calDotNum(dot);
                insertMachineCode(opc,rsNum,rtNum,0,dotNum,addr,code,1);
                break;
            }
            //lw、sw
            case 32:
            case 33:
            case 34:
            case 35:
            case 36:
            case 37:
            case 40:
            case 41:
            case 42:
            case 43:{
                //不允许使用表达式
                rt = regList[0].replaceAll("\t"," ").trim();
                rtNum = findCommonRegisterNum(rt);
                dat = regList[1].substring(0,regList[1].indexOf("(")).replaceAll("\t"," ").trim();
                datNum = calDatNum(dat);
                rs = regList[1].substring(regList[1].indexOf("(")+1,regList[1].indexOf(")")).replaceAll("\t"," ").trim();
                rsNum = findCommonRegisterNum(rs);
                insertMachineCode(opc,rsNum,rtNum,datNum,0,addr,code,0);
                break;
            }
            case 4:
            case 5:
            case 1:
            {
                System.out.println("code added to ThirdScan: [" + lineNum + "]" + code);
                NeedThirdParseMap.put(lineNum,code);
                break;
            }
        }
    }

    private long calDotNum(String dot) throws AssembleException {
        long dotNum;
        int datNum;
        if(isHighLowExp(dot)){
            datNum = calHighLowExp(dot);
            if(datNum>0)
                dotNum = datNum;
            else
                dotNum = Math.abs(datNum);
        }
        else{
            int radix = getRadix(dot);
            dotNum = Long.parseLong(getPreDataStr(dot,radix),radix);
        }
        return dotNum;
    }

    private int calDatNum(String dat) throws AssembleException {
        int datNum;
        if(isHighLowExp(dat)){
            datNum = calHighLowExp(dat);
        }
        else{
            int radix = getRadix(dat);
            datNum = Integer.parseInt(getPreDataStr(dat,radix),radix);
        }
        return datNum;
    }

    private boolean isHighLowExp(String exp){
        if(exp.startsWith("high ") || exp.startsWith("HIGH ") || exp.startsWith("low ") || exp.startsWith("LOW ")){
            return true;
        }
        else{
            return false;
        }
    }

    private int calHighLowExp(String exp) throws AssembleException {
        String whole = exp.substring(exp.indexOf(" ")+1).trim();
        String half = exp.substring(0,exp.indexOf(" ")).trim();
        Long value = 0L;
        if(!LabelAddressMap.containsKey(whole)){
            if(!VariableMap.containsKey(whole) && !EquMap.containsKey(whole))
            {
                int radix = getRadix(whole);
                value = Long.parseLong(getPreDataStr(whole,radix),radix);
            }
            else if(VariableMap.containsKey(whole)){
                //如果是变量则引用变量的地址
                value = VariableMap.get(whole);
            }
            else if(EquMap.containsKey(whole)){
                //如果是Equ定义的变量
                value = EquMap.get(whole);
            }
        }
        else{
            value = LineAddressMap.get(LabelAddressMap.get(whole));
        }
        int choice = half.toLowerCase().equals("high") ? 0:1;
        if(choice == 0){
            return (int)((value & 0xFFFF0000)>>16);
        }
        else{
            return (int)((value & 0x0000FFFF));
        }
    }

    private void parseJTypeInstruction(String code, int lineNum, String mnemonic){
        int opc = OpcMap.get(mnemonic);
        String adr = code.replace(mnemonic,"").replaceAll("\t"," ").trim();
        int radix = 0;
        try {
            radix = getRadix(adr);
        } catch (AssembleException e) {
            System.out.println("--------- adr is: " + adr + ", code is : " + code);
            if(LabelAddressMap.containsKey(adr))
            {
                long tmpAddr = LineAddressMap.get(LabelAddressMap.get(adr));
                long adrValue = tmpAddr/2;
                int machineCode = (opc<<26)|(int)adrValue;
                fillMachineCodeMap(machineCode,LineAddressMap.get(lineNum));
            }
            else{
                System.out.println("code added to ThirdScan: [" + lineNum + "]" + code);
                NeedThirdParseMap.put(lineNum,code);
            }

            return;
        }
        long tmpAddr = Long.parseLong(getPreDataStr(adr,radix),radix);
        long adrValue = tmpAddr/2;
        int machineCode = (opc<<26)|(int)adrValue;
        fillMachineCodeMap(machineCode,LineAddressMap.get(lineNum));
    }

    private void parseCTypeInstruction(String code, int lineNum, String mnemonic) throws AssembleException {
        int rsNum,rtNum,rcNum,func,saNum;
        int opc = OpcMap.get(mnemonic);
        String rt,rc;
        long addr = LineAddressMap.get(lineNum);
        String regs = code.replace(mnemonic,"").trim();
        String[] regList = regs.split(",");
        rsNum = rtNum = func = saNum = 0;
        rcNum = 12;
        switch (mnemonic){
            case "mfc0":{
                rt = regList[0].replaceAll("\t","").trim();
                rc = regList[1].replaceAll("\t","").trim();
                rtNum = findCommonRegisterNum(rt);
                rcNum = findCoprocessorRegisterNum(rc);
                break;
            }
            case "mtc0":{
                rsNum = 4;
                rt = regList[0].replaceAll("\t","").trim();
                rc = regList[1].replaceAll("\t","").trim();
                rtNum = findCommonRegisterNum(rt);
                rcNum = findCoprocessorRegisterNum(rc);
                break;
            }
            case "eret":{
                rsNum = 16;
                rcNum = 0;
                func = 24;
                break;
            }
        }
        insertMachineCode(opc,rsNum,rtNum,rcNum,saNum,func,addr,code);
    }

    //R、C
    private void insertMachineCode(int opc, int rsNum, int rtNum, int rdNum, int saNum, int func, long addr,String code) throws AssembleException {
        if(rdNum == -1 || rsNum ==-1 || rtNum==-1){
            throw new AssembleException("Error: Unrecognized Register Used!\r\n\tRegister used in code: '"+code+"' is not legal.\r\n\tPlease check your code.");
        }
        int machineCode;
        machineCode = (opc << 26) | (rsNum << 21) | (rtNum << 16) | (rdNum << 11) | (saNum<<6) | func;
        fillMachineCodeMap(machineCode,addr);
    }

    //I
    private void insertMachineCode(int opc, int rsNum, int rtNum, int datNum, long dotNum, long addr, String code, int type) throws AssembleException {
        if(rsNum ==-1 || rtNum==-1){
            throw new AssembleException("Error: Unrecognized Register Used!\r\n\tRegister used in code: '"+code+"' is not legal.\r\n\tPlease check your code.");
        }
        if(opc == 4 || opc == 5 || opc == 1){
            return;
        }
        else{
            int machineCode;
            if(type == 0){
                //dat
                machineCode = (opc << 26) | (rsNum << 21) | (rtNum << 16) | datNum&0xFFFF;
            }
            else
                machineCode = (opc << 26) | (rsNum << 21) | (rtNum << 16) | (int)(dotNum&0x0000FFFF);
            fillMachineCodeMap(machineCode,addr);
        }
    }

    private void fillMachineCodeMap(int machineCode,long addr){
        String machineCodeBinStr,machineCodeHexStr;
        machineCodeBinStr = get32BitsBinStr(machineCode);
        machineCodeHexStr = get8BitsHexStr(machineCode);
        BinMachineCodeMap.put(addr,machineCodeBinStr);
        HexMachineCodeMap.put(addr,machineCodeHexStr);
    }

    private String get32BitsBinStr(int machineCode){
        String preStr = Integer.toBinaryString(machineCode);
        String zeroStr = "00000000000000000000000000000000"; //32个零，补零用
        if(preStr.length()<32){
            int dis = 32 - preStr.length();
            preStr = zeroStr.substring(0,dis) + preStr;
        }
        return preStr;
    }

    private String get8BitsHexStr(int machineCode){
        String preStr = Integer.toHexString(machineCode);
        String zeroStr = "00000000"; //8个零，补零用
        if(preStr.length()<8){
            int dis = 8 - preStr.length();
            preStr = zeroStr.substring(0,dis) + preStr;
        }
        return preStr;
    }

    private boolean insertLabel(String label,int lineNum){
        if(LabelAddressMap.containsKey(label)){
            return false;
        }
        else{
            LabelAddressMap.put(label,lineNum);
            return true;
        }
    }

    private JSONObject isInstructionCode(String code){
        String mnemonic = getMnemonic(code);
        JSONObject jsonObject = new JSONObject();
        int type = -1;
        if(OpcMap.containsKey(mnemonic)){
            int opc = OpcMap.get(mnemonic);
            if(opc == 0 || opc == 28){      //R
                type = 0;
            }
            else if(opc == 16)              //C
                type = 3;
            else if(opc == 2 || opc == 3)   //J
                type = 2;
            else                            //I
                type = 1;
        }
        jsonObject.put("type",type);
        jsonObject.put("mnemonic",mnemonic);
        return jsonObject;
    }

    /**
     * 判断是否为可处理的格式指令，返回格式指令类型
     * @param row 代码行
     * @return 格式指令类型
     */
    private int isFormatCode(String row){
        //equ:0,.origin:1,.data:2,.text:3,.end:4,.space:5,.zjie:6,.2zjie:7,.word:8
        //若非格式指令，返回-1
        if(row.contains(" equ ")){
            return 0;
        }
        else if(row.contains(".")){
            if(row.contains(".zjie"))
                return 6;
            else if(row.contains(".2zjie"))
                return 7;
            else if(row.contains(".word"))
                return 8;
            if(row.length() <= 3){
                return -1;
            }
            else{
                String mnemonic = row;
                if(row.contains(" ")){
                    mnemonic = mnemonic.substring(0,mnemonic.indexOf(" "));
                }
                mnemonic = mnemonic.replaceAll("\t","").trim();
                switch (mnemonic){
                    case ".origin":
                        return 1;
                    case ".data":
                        return 2;
                    case ".text":
                        return 3;
                    case ".end":
                        return 4;
                    case ".space":
                        return 5;
                    default:
                        return -1;
                }
            }
        }
        else
            return -1;
    }

    private void handleFormatCode(int type, String code, int lineNum) throws AssembleException {
        //传入的code已经不含有label了
        code = code.replaceAll("\t"," ").trim();
        switch (type){
            //equ:0,.origin:1,.data:2,.text:3,.end:4,.space:5,.zjie:6,.2zjie:7,.word:8
            case 0: //equ
            {
                String symbol = code.substring(0,code.indexOf(' '));
                String valueStr = code.substring(code.lastIndexOf(' ') + 1);
                int radix = getRadix(valueStr);
                valueStr = getPreDataStr(valueStr,radix);
                EquMap.put(symbol,Long.parseLong(valueStr,radix));
                System.out.println("Ready to put into EquMap-> (key,value) :(" + symbol+ ","+valueStr+")");
                break;
            }
            case 1: //.origin
            {
                String addr = code.replace(".origin","").trim();
                System.out.println("======== .origin" + addr);
                int radix = getRadix(addr);
                addr = getPreDataStr(addr,radix);
                Long addrValue = Long.parseLong(addr,radix);
                if(addrValue < 0x4000){
                    textPC = addrValue;
                }
                else{
                    throw new AssembleException("Error: Address Out Of Bound\r\n\tMemory for code is within 0x4000.\r\n\tPlease check your code.");
                }
                break;
            }
            case 2: //.data
            {
                dataStart = lineNum;
                currentState = 0; //处理数据段
                System.out.println("============= Data Segment starts at " + lineNum);
                break;
            }
            case 3: //.text
            {
                if(currentState != 2){
                    codeStart = lineNum;
                    currentState = 1;
                }
                else{
                    throw new AssembleException("Error: Wrong Format Code Used\r\n\tCode can't be located behind '.end'. Please check your code.");
                }
                System.out.println("============= Text Segment starts at " + lineNum);
                break;
            }
            case 4: //.end
            {
                endLineNum = lineNum;
                currentState = 2;
                break;
            }
            case 5: //.space
            {
                String nStr = code.substring(code.indexOf(' ') +1).trim();
                int radix = getRadix(nStr);
                nStr = getPreDataStr(nStr,radix);
                Long nValue = Long.parseLong(nStr,radix);
                dataPC += nValue; //预留nValue个单位zjie的空间
                System.out.println("============ Want to reserve space with size of " + nStr + " zjies");
                break;
            }
            case 6: //.zjie
            case 7: //.2zjie
            case 8: //.word
            {
                //汉字使用GB2312编码，英文字符使用ASCII编码
                String variable = code.substring(0,code.indexOf(" ")).trim();
                if(VariableMap.containsKey(variable)){
                    throw new AssembleException("Error: Duplicate Variable Defined\r\n\tYou can't define variable with the same name:'"+ variable +"'.\r\n\tPlease check your code.");
                }
                else{
                    VariableMap.put(variable,dataPC);
                }
                if(type ==6)
                    countZjieNum(code.substring(code.lastIndexOf(" ")+1));
                else{
                    countWordNum(code.substring(code.lastIndexOf(" ")+1));
                }
                break;
            }
        }
    }

    private void countWordNum(String string) throws AssembleException{
        String[] pieces = string.split(",");
        for(int i = 0;i<pieces.length;i++){
            String str = pieces[i].trim();
            if(!str.contains("'") && !str.contains("\"")){
                int radix = getRadix(str);
                String numStr = getPreDataStr(str,radix);
                int num = Integer.parseInt(numStr,radix);
                Short high16,low16;
                high16 = Short.parseShort(((num&0xFFFF0000)>>16)+"");
                low16 = Short.parseShort((num&0x0000FFFF) + "");
                //MIPS架构采用大端存储
                MemoryMap.put(dataPC,high16);
                dataPC++;
                MemoryMap.put(dataPC,low16);
                dataPC++;
                System.out.println("=========== Number High 16 digits: " + high16 +", Low 16 digits: " + low16);
            }
            else{
                JSONObject jsonObject = getWholeQuotationStr(pieces,i,str);
                String vStr = jsonObject.getString("str");
                vStr = vStr.substring(1,vStr.length()-1);
                i = jsonObject.getInteger("index");
                byte[] bytes = new byte[0];
                try {
                    bytes = getGB2312Bytes(vStr);
                } catch (UnsupportedEncodingException e) {
                    throw new AssembleException("Error: Unable To Convert String Encoding\r\n\tSomething wrong happened during converting the encoding of'" + vStr + "'\r\n\tPlease check your code.");
                }
                int index = 0;
                short hanZi = 0;
                for(byte b : bytes){
                    System.out.println(String.format("%x",b));
                    if((b & 0x80) != 0){
                        if(index == 1){
                            hanZi = (short)(hanZi+b);
                            MemoryMap.put(dataPC,(short)0);
                            dataPC++;
                            MemoryMap.put(dataPC,hanZi);
                            dataPC++;
                            hanZi = 0;
                            index = 0;
                        }
                        else{
                            hanZi = (short) (((short)b) << 8);
                            index++;
                        }
                    }
                    else{
                        MemoryMap.put(dataPC,(short)0);
                        dataPC++;
                        MemoryMap.put(dataPC,(short)b);
                        dataPC++;
                    }
                }
            }
        }
    }

    private void countZjieNum(String string) throws AssembleException {
        //中文字符与英文字符同样都各占一个zjie（16位）
        //读进来的时候，都是utf8编码的
        //去除多余的分隔符','
        String[] pieces = string.split(",");
        for(int i = 0; i< pieces.length;i++){
            System.out.println(" Outer ------ piece[" + i + "]: " + pieces[i].trim());
        }
        for(int i = 0; i< pieces.length;i++){
            String str = pieces[i].trim();
            System.out.println(" Inner ------ piece[" + i + "]: " + str);
            if(!str.contains("'") && !str.contains("\"")){
                //数字
                int radix = getRadix(str);
                String numStr = getPreDataStr(str,radix);
                MemoryMap.put(dataPC,Short.parseShort(numStr,radix));
                dataPC++;
            }
            else{
                JSONObject jsonObject = getWholeQuotationStr(pieces,i,str);
                String vStr = jsonObject.getString("str");
                vStr = vStr.substring(1,vStr.length()-1);
                i = jsonObject.getInteger("index");
                byte[] bytes = new byte[0];
                try {
                    bytes = getGB2312Bytes(vStr);
                } catch (UnsupportedEncodingException e) {
                    throw new AssembleException("Error: Unable To Convert String Encoding\r\n\tSomething wrong happened during converting the encoding of'" + vStr + "'\r\n\tPlease check your code.");
                }
                int countHanZi,countASCII;
                countASCII = countHanZi = 0;
                int index = 0;
                short hanZi = 0;
                for(byte b : bytes){
                    System.out.println(String.format("%x",b));
                    if((b & 0x80) != 0){
                        if(index == 1){
                            hanZi = (short)(hanZi+b);
                            MemoryMap.put(dataPC,hanZi);
                            dataPC++;
                            hanZi = 0;
                            index = 0;
                        }
                        else{
                            hanZi = (short) (((short)b) << 8);
                            index++;
                        }
                        countHanZi++;
                    }
                    else{
                        countASCII++;
                        MemoryMap.put(dataPC,(short)b);
                        dataPC++;
                    }
                }
                System.out.println("asc: " + countASCII);
                System.out.println("hanZi: " + countHanZi/2);
            }
        }
    }

    private JSONObject getWholeQuotationStr(String[] pieces, int i, String str) throws AssembleException {
        JSONObject jsonObject = new JSONObject();
        String vStr = "";
        int j=0;
        if((str.startsWith("'") && !str.endsWith("'")) || (str.startsWith("\"") && !str.endsWith("\""))){
            String symbol = str.startsWith("'")?"'":"\"";
            StringBuilder builder = new StringBuilder();
            for(j = i; j<pieces.length;j++){
                if(pieces[j].endsWith(symbol)){
                    builder.append(","+pieces[j]);
                    vStr = builder.toString();
                    break;
                }
                else{
                    if(j==i)
                        builder.append(pieces[j]);
                    else
                        builder.append("," + pieces[j]);
                }
            }
        }
        else if((str.startsWith("'") && str.endsWith("'")) || (str.startsWith("\"") && str.endsWith("\""))){
            j=i;
            vStr = str;
        }
        else{
            throw new AssembleException("Error: Wrong Data Definition Code\r\n\tThere is something wrong with data definition of '" + str +"'.\r\n\tPlease check your code.");
        }
        jsonObject.put("str",vStr);
        jsonObject.put("index",j);
        return jsonObject;
    }

    private byte[] getGB2312Bytes(String vStr) throws UnsupportedEncodingException {
        byte[] utf8Arr = vStr.getBytes("utf-8");
        byte[] gbkArr = EncodingUtil.convertEncoding_ByteArr(utf8Arr,"utf-8","gbk");
        return gbkArr;
    }

    /**
     * 预处理汇编代码，去掉不含代码的行，及行中，行间的注释
     * @param rawCode 未处理的源代码
     * @return 筛选出的有效代码行
     */
    private ArrayList<String> handleRawCode(String rawCode){
        ArrayList<String> postList = new ArrayList<>();
        String[] preList = rawCode.split("\n");
        for(String s : preList){
            String row;
            if((row = handleSingleRow(s)) != null){
                postList.add(row);
            }
        }
        return postList;
    }

    private String handleSingleRow(String row){
        if(row.length() == 0 || row.matches("\\s+"))
            return null;
        else{
            if(row.contains("#")){
                row = row.substring(0,row.indexOf('#'));
                row = row.trim();//去除前后空格
            }
            if(row.length() != 0)
                return row;
            else
                return null;
        }
    }

    private void thirdScan() throws AssembleException {
        System.out.println("There are " + NeedThirdParseMap.size() + " pieces of code in third scan");
        for(Map.Entry<Integer, String> entry: NeedThirdParseMap.entrySet()){
            System.out.println("---------- code in third scan: -----------\r\n\t" + entry.getValue());
            long addr = LineAddressMap.get(entry.getKey());
            String code = entry.getValue();
            String mnemonic = getMnemonic(code);
            int opc = OpcMap.get(mnemonic);
            int machineCode;
            String regs = code.replace(mnemonic,"").replaceAll("\t"," ").trim();
            switch (opc){
                //需要第三次扫描处理的都是可能含有标号的指令，三条b相关、J型指令
                case 4:
                case 5:
                case 1:
                {
                    int rsNum,rtNum;
                    String rs,rt,ofs;
                    String[] regList = regs.split(",");
                    long labelAddr;
                    if(opc != 1){
                        rs = regList[0].replaceAll("\t"," ").trim();
                        rt = regList[1].replaceAll("\t"," ").trim();
                        ofs = regList[2].replaceAll("\t"," ").trim();
                        rsNum = findCommonRegisterNum(rs);
                        rtNum = findCommonRegisterNum(rt);
                    }
                    else{
                        rs = regList[0].replaceAll("\t"," ").trim();
                        ofs = regList[1].replaceAll("\t"," ").trim();
                        rsNum = findCommonRegisterNum(rs);
                        rtNum = 17;
                    }
                    int radix = 0;
                    try {
                       radix = getRadix(ofs);
                    } catch (AssembleException e) {
                        System.out.println("Label in third scan: " + ofs);
                        if(LabelAddressMap.containsKey(ofs))
                        {
                            labelAddr = LineAddressMap.get(LabelAddressMap.get(ofs));
                            fillBranchInstruction2Map(addr,labelAddr,opc,rsNum,rtNum);
                            break;
                        }
                        else
                            throw new AssembleException("Error: Undefined Label Used\r\n\tCode '" + code + "' has used a label that hasn't been defined yet.\r\n\tPlease check your code.");
                    }
                    labelAddr = Long.parseLong(getPreDataStr(ofs,radix),radix);
                    fillBranchInstruction2Map(addr,labelAddr,opc,rsNum,rtNum);
                    break;
                }
                case 2:
                case 3:{
                    //不含标号的已经处理过
                    if(LabelAddressMap.containsKey(regs)){
                        long labelAddr = LineAddressMap.get(LabelAddressMap.get(regs));
                        long adrValue = labelAddr/2;
                        machineCode = (opc <<26)|(int)(adrValue&0x03FFFFFF);
                        fillMachineCodeMap(machineCode,addr);
                        break;
                    }
                    else
                        throw new AssembleException("Error: Undefined Label Used\r\n\tCode '" + code + "' has used a label that hasn't been defined yet.\r\n\tPlease check your code.");
                }
            }
        }
    }

    private void fillBranchInstruction2Map(long addr, long labelAddr, int opc, int rsNum, int rtNum){
        long nextAddr = addr+2;
        long dis = (labelAddr-nextAddr)/2;
        int ofsNum,machineCode;
        ofsNum = (int)dis;
        machineCode = (opc << 26) | (rsNum << 21) | (rtNum << 16) | (ofsNum&0x0000FFFF);
        fillMachineCodeMap(machineCode,addr);
    }

    public static void main(String[] args) {
        Long l = 0x123456789L;
        System.out.println(Long.toHexString(l));
        System.out.println(String.format("%8x",l));
    }

}
