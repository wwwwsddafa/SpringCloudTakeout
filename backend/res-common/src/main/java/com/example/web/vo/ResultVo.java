package com.example.web.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder  // 将当前类转为构造器模式，可以使用链式方式调用
public class ResultVo<T>{
    private Integer code;   //业务状态码
    private String msg;     //业务状态描述
    private T data;         //业务数据

    public static <T> ResultVo<T> success(T data){
        return new ResultVo<>(200,"success",data);
    }

    public static <T> ResultVo<T> success(Integer code,String msg){
        return new ResultVo<>(code,msg,null);
    }

    public static <T> ResultVo<T> success(Integer code,String msg, T data){
        return new ResultVo<>(code,msg,data);
    }

    public static <T> ResultVo<T> fail(Integer code,String msg){
        return new ResultVo<>(code,msg,null);
    }

    public static <T> ResultVo<T> fail(Integer code,String msg, T data){
        return new ResultVo<>(code,msg,data);
    }
}
/*
 * 统一响应封装类，用于前后端交互时异常时返回统一格式的数据。
 * 里面全是静态工厂方法，用于快速创建 ResultVo 对象，避免每次都要 new ResultVo<>() 并传入所有参数
 * 静态工厂方法是一个类的 static 方法，用于返回该类的实例，替代直接使用 new 构造器。
 */
