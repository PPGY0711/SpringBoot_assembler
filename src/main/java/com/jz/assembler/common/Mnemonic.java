package com.jz.assembler.common;

public class Mnemonic {
    public static String[] allMnemonics =
            {"lui", "add", "addi", "sub", "slt", "slti", "sltu", "sltiu",
            "and", "andi", "or", "ori", "xor", "xori", "nor", "sll", "sllv",
            "srl", "srlv", "sra", "srav", "lw", "lwx", "lh", "lhx", "lhu",
            "lhux", "sw", "swx", "sh", "shx", "beq", "bne", "bgezal", "j",
            "jal", "jr", "jalr", "mfc0", "mtc0", "eret", "syscall", "mul",
            "mult", "multu", "div", "divu", "mfhi", "mflo", "mthi", "mtlo"};
    public static int[] allOpcs =
            {15, 0, 8, 0, 0, 10, 0, 11, 0, 12, 0, 13, 0, 14, 0, 0, 0, 0, 0,
            0, 0, 35, 34, 33, 32, 37, 36, 43, 42, 41, 40, 4, 5, 1, 2, 3,
            0, 0, 16, 16, 16, 0, 28, 0, 0, 0, 0, 0, 0, 0, 0};
    public static int[] rTypeFuncs =
            {32, 34, 42, 43, 36, 37, 38, 39, 0, 4, 2, 6,
            3, 7, 8, 9, 12, 24, 25, 26, 27, 16, 17, 18, 19};
    public static int[] allFuncs =
            {0, 32, 0, 34, 42, 0, 43, 0, 36, 0, 37, 0, 38, 0, 39, 0,
            4, 2, 6, 3, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 8, 9, 0, 0, 24, 12, 2, 24, 25, 26, 27, 16, 18, 17, 19};
}
