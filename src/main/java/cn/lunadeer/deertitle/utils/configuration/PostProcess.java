package cn.lunadeer.deertitle.utils.configuration;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface PostProcess {
    int priority() default 0;
}
