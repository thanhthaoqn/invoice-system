package com.fsa.expense.email;
public class IntakePersistException extends RuntimeException {
    public IntakePersistException(Throwable cause){super("Không lưu được email gốc; không được đánh dấu SEEN",cause);}
}
