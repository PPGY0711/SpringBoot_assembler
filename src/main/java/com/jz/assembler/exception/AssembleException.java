package com.jz.assembler.exception;

/**
 * 自定义异常处理类
 * 当汇编器扫描过程中出错时，抛出该异常
 * 反汇编器同理
 */
public class AssembleException extends Exception{
    private String message;

    public AssembleException(String message){
        super(message);
        this.message = message;
    }
}
