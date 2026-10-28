package cn.lunadeer.deertitle.domain;

public final class TitleException extends RuntimeException {
    private final ErrorCode code;
    public TitleException(ErrorCode code) { super(code.name()); this.code = code; }
    public TitleException(ErrorCode code, Throwable cause) { super(code.name(), cause); this.code = code; }
    public ErrorCode code() { return code; }
}
